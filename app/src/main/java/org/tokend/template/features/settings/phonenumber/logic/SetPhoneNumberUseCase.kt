package org.tokend.template.features.settings.phonenumber.logic

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
 * Sets phone number for given account.
 *
 * Updates identities cache in [AccountDetailsRepository] locally
 */
class SetPhoneNumberUseCase(
        private val phoneNumber: String,
        private val walletInfoProvider: WalletInfoProvider,
        private val apiProvider: ApiProvider,
        private val accountDetailsRepository: AccountDetailsRepository
) {
    class PhoneNumberAlreadyTakenException(number: String) :
            Exception("Phone number $number is already taken")

    private lateinit var accountId: String

    fun perform(): Completable {
        return getAccountId()
                .doOnSuccess { accountId ->
                    this.accountId = accountId
                }
                .flatMapCompletable {
                    setPhoneNumber()
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

    private fun setPhoneNumber(): Completable {
        val signedApi = apiProvider.getSignedApi()
                ?: return Completable.error(IllegalStateException("No signed API instance found"))

        return signedApi
                .identities
                .setPhoneNumber(
                        accountId,
                        phoneNumber
                )
                .toCompletable()
                .onErrorResumeNext {
                    if (it is HttpException && it.isConflict())
                        Completable.error(PhoneNumberAlreadyTakenException(phoneNumber))
                    else
                        Completable.error(it)
                }
    }

    private fun updateRepositories() {
        accountDetailsRepository
                .getCachedIdentity(accountId)
                ?.phoneNumber = phoneNumber
    }
}