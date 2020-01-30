package org.tokend.template.features.assets.sell.model

import android.content.Context
import org.tokend.sdk.api.integrations.marketplace.model.MarketplacePaymentMethodType
import org.tokend.template.R
import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord
import org.tokend.template.view.util.LocalizedName

class MarketplaceSellPaymentMethodListItem(
        val name: String,
        val destination: String
) {
    constructor(source: MarketplaceSellPaymentMethod,
                context: Context) : this(
            name = LocalizedName(context).forMarketplacePaymentMethodType(source.method.type),
            destination = formatDestination(source.method, source.destination, context)
    )

    private companion object {
        private fun formatDestination(method: MarketplaceOfferRecord.PaymentMethod,
                                      destination: String,
                                      context: Context): String {
            return when (method.type) {
                MarketplacePaymentMethodType.FORBILL ->
                    context.getString(
                            R.string.template_payment_method_transfer_to,
                            "*" + destination
                                    .substring(destination.length - 4, destination.length)
//                                    .toCharArray()
//                                    .toMutableList()
//                                    .apply {
//                                        add(4, ' ')
//                                        add(9, ' ')
//                                        add(14, ' ')
//                                    }
//                                    .let { String(it.toCharArray()) }
                    )
                MarketplacePaymentMethodType.INTERNAL ->
                    method.asset.run { name ?: code }
                MarketplacePaymentMethodType.COINPAYMENTS ->
                    method.asset.code + "; " +
                            context.getString(
                                    R.string.template_payment_method_transfer_to,
                                    destination
                            )
            }
        }
    }
}