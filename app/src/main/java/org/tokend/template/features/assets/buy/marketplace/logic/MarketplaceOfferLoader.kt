package org.tokend.template.features.assets.buy.marketplace.logic

import io.reactivex.Maybe
import io.reactivex.rxkotlin.toMaybe
import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord
import org.tokend.template.features.assets.buy.marketplace.repository.MarketplaceOffersRepository

class MarketplaceOfferLoader(
        private val marketplaceOffersRepository: MarketplaceOffersRepository
) {
    fun load(assetCode: String): Maybe<MarketplaceOfferRecord> {
        val getExistingOffer = {
            marketplaceOffersRepository
                    .itemsList
                    .find { it.asset.code == assetCode }
                    .toMaybe()
        }

        return getExistingOffer()
                .switchIfEmpty(
                        marketplaceOffersRepository
                                .updateDeferred()
                                .toSingleDefault(true)
                                .flatMapMaybe { getExistingOffer() }
                )
    }
}