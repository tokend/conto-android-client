package org.tokend.template.features.dashboard.shop.view.adapter

import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.AtomicSwapAskRecord
import java.math.BigDecimal

class SinglePriceAtomicSwapAskListItem(
        val available: BigDecimal,
        val asset: Asset,
        val logoUrl: String?,
        val quoteAsset: AtomicSwapAskRecord.QuoteAsset,
        val source: AtomicSwapAskRecord?
) {
    constructor(source: AtomicSwapAskRecord): this(
            available = source.amount,
            asset = source.asset,
            logoUrl = source.asset.logoUrl,
            quoteAsset = source.quoteAssets.first(),
            source = source
    )
}