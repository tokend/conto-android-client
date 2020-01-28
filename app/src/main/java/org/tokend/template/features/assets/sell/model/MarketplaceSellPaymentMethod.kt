package org.tokend.template.features.assets.sell.model

import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord

class MarketplaceSellPaymentMethod(
        val method: MarketplaceOfferRecord.PaymentMethod,
        val destination: String
)