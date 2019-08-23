package org.tokend.template.features.settings.telegram.logic

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import org.tokend.rx.extensions.toCompletable
import org.tokend.sdk.utils.extentions.isConflict
import org.tokend.template.data.repository.AccountDetailsRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.WalletInfoProvider
import retrofit2.HttpException

/**
 * Sets Telegram username for given account.
 *
 * Updates identities cache in [AccountDetailsRepository] locally
 *
 * @param username Telegram username without "@"
 */
class SetTelegramUsernameUseCase(
        private val username: String,
        private val walletInfoProvider: WalletInfoProvider,
        private val apiProvider: ApiProvider,
        private val accountDetailsRepository: AccountDetailsRepository
) {
    class UsernameAlreadyTakenException(number: String) :
            Exception("Username $number is already taken")

    private lateinit var accountId: String

    fun perform(): Completable {
        return getAccountId()
                .doOnSuccess { accountId ->
                    this.accountId = accountId
                }
                .flatMapCompletable {
                    setUsername()
                }
                .doOnComplete {
                    updateRepositories()
                }
    }

    private fun getAccountId(): Single<String> {
        return walletInfoProvider
                .getWalletInfo()
                ?.accountId
                .toMaybe()
                .switchIfEmpty(Single.error(IllegalStateException("No wallet info found")))
    }

    private fun setUsername(): Completable {
        val signedApi = apiProvider.getSignedApi()
                ?: return Completable.error(IllegalStateException("No signed API instance found"))

        return signedApi
                .identities
                .setTelegramUsername(
                        accountId,
                        username
                )
                .toCompletable()
                .onErrorResumeNext {
                    if (it is HttpException && it.isConflict())
                        Completable.error(UsernameAlreadyTakenException(username))
                    else
                        Completable.error(it)
                }
    }

    private fun updateRepositories() {
        accountDetailsRepository
                .getCachedIdentity(accountId)
                ?.telegramUsername = username
    }
}