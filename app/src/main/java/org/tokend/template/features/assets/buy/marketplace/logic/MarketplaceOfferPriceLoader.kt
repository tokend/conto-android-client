package org.tokend.template.features.assets.buy.marketplace.logic

import io.reactivex.Single
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.integrations.marketplace.model.MarketplaceOfferPrice
import org.tokend.template.di.providers.ApiProvider
import java.math.BigDecimal

class MarketplaceOfferPriceLoader(
        private val apiProvider: ApiProvider,
        private val offerId: String,
        private val paymentMethodId: String
) {
    fun load(amount: BigDecimal,
             promoCode: String?): Single<MarketplaceOfferPrice> {
        return apiProvider
                .getApi()
                .integrations
                .marketplace
                .getCalculatedOfferPrice(
                        offerId = offerId,
                        paymentMethodId = paymentMethodId,
                        amount = amount,
                        promocode = promoCode
                )
                .toSingle()
    }
}