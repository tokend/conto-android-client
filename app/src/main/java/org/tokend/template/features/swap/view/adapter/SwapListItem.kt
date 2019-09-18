package org.tokend.template.features.swap.view.adapter

import org.tokend.template.data.model.Asset
import org.tokend.template.features.swap.model.SwapRecord
import org.tokend.template.features.swap.model.SwapState
import java.math.BigDecimal

class SwapListItem(
        val baseAmount: BigDecimal,
        val baseAsset: Asset,
        val quoteAmount: BigDecimal,
        val quoteAsset: Asset,
        val counterparty: String,
        val state: SwapState,
        val isIncoming: Boolean,
        val source: SwapRecord?
) {
    constructor(source: SwapRecord) : this(
            baseAmount = source.baseAmount,
            baseAsset = source.baseAsset,
            quoteAmount = source.quoteAmount,
            quoteAsset = source.quoteAsset,
            counterparty = source.counterpartyEmail,
            state = source.state,
            isIncoming = source.isIncoming,
            source = source
    )
}