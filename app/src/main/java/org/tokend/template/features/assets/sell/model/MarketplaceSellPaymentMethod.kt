package org.tokend.template.features.assets.sell.model

import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord
import java.io.Serializable

class MarketplaceSellPaymentMethod(
        val method: MarketplaceOfferRecord.PaymentMethod,
        val destination: String
): Serializable