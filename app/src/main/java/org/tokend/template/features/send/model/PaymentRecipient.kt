package org.tokend.template.features.send.model

import org.tokend.sdk.factory.GsonFactory
import org.tokend.template.data.model.history.details.BalanceChangeCause
import java.io.Serializable

/**
 * Recipient for [PaymentRequest]
 */
open class PaymentRecipient(
        val accountId: String,
        val nickname: String? = null
) : Serializable {
    /**
     * [nickname] if it's present, [accountId] otherwise
     */
    open val displayedValue = nickname ?: accountId

    /**
     * Recipient for [PaymentRequest] to not existing account
     * which requires counterparty account.
     */
    class NotExisting(
            counterpartyAccountId: String,
            val actualEmail: String
    ) : PaymentRecipient(counterpartyAccountId, actualEmail) {
        override val displayedValue = actualEmail

        fun wrapPaymentSubject(senderAccount: String,
                               actualSubject: String?): String {
            return BalanceChangeCause.Payment.PaymentToNotExistingRecipientMeta(
                    senderAccount = senderAccount,
                    recipientEmail = actualEmail,
                    actualSubject = actualSubject
            ).let { GsonFactory().getBaseGson().toJson(it) }
        }
    }
}