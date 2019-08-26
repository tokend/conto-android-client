package org.tokend.template.features.send.recipient.logic

import io.reactivex.Single
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.integrations.paymentproxy.PaymentProxyApi
import org.tokend.template.data.repository.AccountDetailsRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.features.send.model.PaymentRecipient
import org.tokend.template.util.validator.EmailValidator
import org.tokend.wallet.Base32Check

/**
 * Loads payment recipient info
 */
class PaymentRecipientLoader(
        private val accountDetailsRepository: AccountDetailsRepository,
        private val apiProvider: ApiProvider
) {
    class NoRecipientFoundException(recipient: String)
        : Exception("No recipient account ID found for $recipient")

    /**
     * Loads payment recipient info if [recipient] is an email or just
     * returns it immediately if [recipient] is an account ID
     *
     * @see NoRecipientFoundException
     */
    fun load(recipient: String): Single<PaymentRecipient> {
        return if (Base32Check.isValid(Base32Check.VersionByte.ACCOUNT_ID,
                        recipient.toCharArray()))
            Single.just(PaymentRecipient(recipient))
        else
            accountDetailsRepository
                    .getAccountIdByIdentifier(recipient)
                    .map { accountId ->
                        PaymentRecipient(
                                accountId = accountId,
                                nickname = recipient
                        )
                    }
                    .onErrorResumeNext { error ->
                        if (error is AccountDetailsRepository.NoIdentityAvailableException)
                            if (EmailValidator.isValid(recipient))
                                getRecipientForNotExistingAccount(recipient)
                            else
                                Single.error(NoRecipientFoundException(recipient))
                        else
                            Single.error(error)
                    }
    }

    private fun getRecipientForNotExistingAccount(email: String): Single<PaymentRecipient> {
        return apiProvider
                .getApi()
                .integrations
                .paymentProxy
                .getInfo()
                .toSingle()
                .map { proxyAccountInfo ->
                    PaymentRecipient.NotExisting(
                            counterpartyAccountId = proxyAccountInfo.id,
                            actualEmail = email
                    )
                }
    }
}