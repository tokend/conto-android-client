package org.tokend.template.features.signin.logic

import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import io.reactivex.schedulers.Schedulers
import org.tokend.rx.extensions.fromSecretSeedSingle
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.wallets.model.EmailNotVerifiedException
import org.tokend.sdk.api.wallets.model.InvalidCredentialsException
import org.tokend.sdk.keyserver.KeyServer
import org.tokend.sdk.keyserver.models.WalletInfo
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.UrlConfigProvider
import org.tokend.template.logic.Session
import org.tokend.template.logic.persistance.CredentialsPersistor
import org.tokend.wallet.Account

/**
 * Performs sign in with given credentials:
 * performs keypair loading and decryption,
 * sets up WalletInfoProvider and AccountProvider, updates CredentialsPersistor if it is set.
 * If CredentialsPersistor contains saved credentials no network calls will be performed.
 *
 * @param postSignInManager if set then [PostSignInManager.doPostSignIn] will be performed
 */
class SignInUseCase
private constructor(
        private val email: String,
        private val password: CharArray,
        private val apiProvider: ApiProvider?,
        private val urlConfigProvider: UrlConfigProvider?,
        private val forcedKeyServer: KeyServer?,
        private val session: Session,
        private val credentialsPersistor: CredentialsPersistor?,
        private val postSignInManager: PostSignInManager?
) {
    constructor(email: String,
                password: CharArray,
                keyServer: KeyServer,
                session: Session,
                credentialsPersistor: CredentialsPersistor?,
                postSignInManager: PostSignInManager?
    ) : this(email, password, null, null, keyServer,
            session, credentialsPersistor, postSignInManager)

    constructor(email: String,
                password: CharArray,
                apiProvider: ApiProvider,
                urlConfigProvider: UrlConfigProvider,
                session: Session,
                credentialsPersistor: CredentialsPersistor?,
                postSignInManager: PostSignInManager?
    ) : this(email, password, apiProvider, urlConfigProvider, null,
            session, credentialsPersistor, postSignInManager)

    private lateinit var walletInfo: WalletInfo
    private lateinit var account: Account

    fun perform(): Completable {
        val scheduler = Schedulers.newThread()

        return getWalletInfo()
                .doOnSuccess { walletInfo ->
                    this.walletInfo = walletInfo
                }
                .observeOn(scheduler)
                .flatMap {
                    getAccountFromWalletInfo()
                }
                .doOnSuccess { account ->
                    this.account = account
                }
                .flatMap {
                    updateProviders()
                }
                .flatMap {
                    performPostSignIn()
                }
                .observeOn(scheduler)
                .retry { attempt, error ->
                    error is PostSignInManager.AuthMismatchException && attempt == 1
                }
                .ignoreElement()
    }

    private fun getWalletInfo(): Single<WalletInfo> {
        return getSavedWalletInfo()
                .switchIfEmpty(getRemoteWalletInfo())
    }

    private fun getRemoteWalletInfo(): Single<WalletInfo> {
        if (forcedKeyServer != null) {
            return forcedKeyServer.getWalletInfo(email, password).toSingle()
        }

        val urlConfigProvider = this.urlConfigProvider
                ?: return Single.error(IllegalArgumentException("If no KeyServer specified then " +
                        "UrlConfigProvider is required"))
        val apiProvider = this.apiProvider
                ?: return Single.error(IllegalArgumentException("If no KeyServer specified then " +
                        "ApiProvider is required"))

        val systemsCount = urlConfigProvider.getConfigsCount()

        val errors = mutableListOf<Throwable>()

        return (0 until systemsCount).map { index ->
            apiProvider.getKeyServer(index)
                    .getWalletInfo(email, password)
                    .toSingle()
                    .toMaybe()
                    .doOnError { errors.add(it) }
                    .onErrorComplete()
        }
                .let { Maybe.merge(it) }
                .collect<MutableList<WalletInfo>>(
                        { mutableListOf() },
                        { a, b -> a.add(b) }
                )
                .flatMap { collectedWalletInfo ->
                    val mostValuableError = errors.firstOrNull { it is EmailNotVerifiedException }
                            ?: errors.firstOrNull { it is InvalidCredentialsException }
                            ?: errors.firstOrNull()

                    collectedWalletInfo.firstOrNull()?.let { Single.just(it) }
                            ?: Single.error(
                                    mostValuableError
                                            ?: throw IllegalStateException(
                                                    "There is no wallet info and no error, is it legal?"
                                            )
                            )
                }
    }

    private fun getSavedWalletInfo(): Maybe<WalletInfo> {
        return credentialsPersistor
                ?.takeIf { it.getSavedEmail() == email }
                .toMaybe()
                .flatMap { credentialsPersistor ->
                    credentialsPersistor.loadCredentialsMaybe(password)
                }
    }

    private fun getAccountFromWalletInfo(): Single<Account> {
        return Account.fromSecretSeedSingle(walletInfo.secretSeed)
    }

    private fun updateProviders(): Single<Boolean> {
        session.setWalletInfo(walletInfo)
        credentialsPersistor?.saveCredentials(walletInfo, password)
        session.setAccount(account)
        session.signInMethod = SignInMethod.CREDENTIALS

        return Single.just(true)
    }

    private fun performPostSignIn(): Single<Boolean> {
        return if (postSignInManager != null)
            postSignInManager
                    .doPostSignIn()
                    .doOnError {
                        if (it is PostSignInManager.AuthMismatchException) {
                            credentialsPersistor?.clear(keepEmail = true)
                        }
                    }
                    .toSingleDefault(true)
        else
            Single.just(false)
    }
}