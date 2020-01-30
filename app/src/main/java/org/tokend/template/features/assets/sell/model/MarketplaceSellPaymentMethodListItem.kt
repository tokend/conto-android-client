package org.tokend.template.features.assets.sell.model

import org.tokend.sdk.api.integrations.marketplace.model.MarketplacePaymentMethodType
import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord
import org.tokend.template.view.util.LocalizedName

class MarketplaceSellPaymentMethodListItem(
        val name: String,
        val destination: String
) {
    constructor(source: MarketplaceSellPaymentMethod,
                localizedName: LocalizedName) : this(
            name = localizedName.forMarketplacePaymentMethodType(source.method.type),
            destination = formatDestination(source.method, source.destination)
    )

    private companion object {
        private fun formatDestination(method: MarketplaceOfferRecord.PaymentMethod,
                                      destination: String): String {
            return when (method.type) {
                MarketplacePaymentMethodType.FORBILL ->
                    destination
                            .toCharArray()
                            .toMutableList()
                            .apply {
                                add(4, ' ')
                                add(9, ' ')
                                add(14, ' ')
                            }
                            .let { String(it.toCharArray()) }
                MarketplacePaymentMethodType.INTERNAL ->
                    method.asset.run { name ?: code }
                MarketplacePaymentMethodType.COINPAYMENTS ->
                    method.asset.code + " – " + destination
            }
        }
    }
}