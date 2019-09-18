package org.tokend.template.features.swap.model

import org.tokend.template.data.model.Asset
import java.math.BigDecimal

class SwapRecord(
        val sourceAccountId: String,
        val destAccountId: String,
        val baseAmount: BigDecimal,
        val baseAsset: Asset,
        val quoteAmount: BigDecimal,
        val quoteAsset: Asset,
        val hash: String,
        val secret: ByteArray?,
        val state: SwapState,
        val isIncoming: Boolean,
        val counterpartyEmail: String
) {
}