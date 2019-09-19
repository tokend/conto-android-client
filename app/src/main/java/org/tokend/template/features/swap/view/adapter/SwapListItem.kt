package org.tokend.template.features.swap.view.adapter

import org.tokend.template.data.model.Asset
import org.tokend.template.features.swap.model.SwapRecord
import org.tokend.template.features.swap.model.SwapState
import java.math.BigDecimal

class SwapListItem(
        val payAmount: BigDecimal,
        val payAsset: Asset,
        val receiveAmount: BigDecimal,
        val receiveAsset: Asset,
        val counterparty: String?,
        val state: SwapState,
        val isIncoming: Boolean,
        val source: SwapRecord?
) {
    constructor(source: SwapRecord) : this(
            payAmount = if (!source.isIncoming) source.baseAmount else source.quoteAmount,
            payAsset = if (!source.isIncoming) source.baseAsset else source.quoteAsset,
            receiveAmount = if (source.isIncoming) source.baseAmount else source.quoteAmount,
            receiveAsset = if (source.isIncoming) source.baseAsset else source.quoteAsset,
            counterparty = source.counterpartyEmail,
            state = source.state,
            isIncoming = source.isIncoming,
            source = source
    )
}