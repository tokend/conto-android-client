package org.tokend.template.features.assets.buy.marketplace.view.adapter

import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.AssetRecord
import org.tokend.template.data.model.RecordWithDescription
import org.tokend.template.data.model.RecordWithLogo
import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord
import java.math.BigDecimal

class MarketplaceOfferListItem(
        val available: BigDecimal,
        val asset: Asset,
        val logoUrl: String?,
        val price: BigDecimal,
        val priceAsset: Asset,
        val companyName: String?,
        val description: String?,
        val source: MarketplaceOfferRecord?
) {
    constructor(source: MarketplaceOfferRecord,
                withCompany: Boolean) : this(
            available = source.amount,
            asset = source.asset,
            logoUrl = (source.asset as? RecordWithLogo)?.logoUrl,
            price = source.price,
            priceAsset = source.priceAsset,
            companyName = if (withCompany) source.company.name else null,
            description = (source.asset as? RecordWithDescription)?.description,
            source = source
    )
}