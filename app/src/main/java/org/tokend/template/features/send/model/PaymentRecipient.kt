package org.tokend.template.features.send.model

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
            val counterpartyAccountId: String,
            val actualEmail: String
    ): PaymentRecipient(counterpartyAccountId, actualEmail) {
        override val displayedValue = actualEmail
    }
}