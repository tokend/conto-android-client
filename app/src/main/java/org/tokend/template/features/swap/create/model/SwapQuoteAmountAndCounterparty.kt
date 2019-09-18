package org.tokend.template.features.swap.create.model

import org.tokend.template.data.model.Asset
import org.tokend.template.features.send.model.PaymentRecipient
import java.io.Serializable
import java.math.BigDecimal

class SwapQuoteAmountAndCounterparty(
        val amount: BigDecimal,
        val asset: Asset,
        val counterparty: PaymentRecipient
) : Serializable