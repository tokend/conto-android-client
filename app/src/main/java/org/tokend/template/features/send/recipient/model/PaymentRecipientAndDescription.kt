package org.tokend.template.features.send.recipient.model

import org.tokend.template.features.send.model.PaymentRecipient

class PaymentRecipientAndDescription(
        val recipient: PaymentRecipient,
        val description: String?
)