package org.tokend.template.features.trade.orderbook.repository

import io.reactivex.Maybe
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.v3.orderbook.params.OrderBookParamsV3
import org.tokend.template.data.repository.base.SingleItemRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.features.trade.orderbook.model.OrderBook

class OrderBookRepository
(
        private val apiProvider: ApiProvider,
        private val baseAsset: String,
        private val quoteAsset: String
) : SingleItemRepository<OrderBook>() {
    override fun getItem(): Maybe<OrderBook> {
        val api = apiProvider.getApi()

        val params = OrderBookParamsV3.Builder()
                .withMaxEntries(25)
                .withInclude(
                        OrderBookParamsV3.Includes.BUY_ENTRIES,
                        OrderBookParamsV3.Includes.SELL_ENTRIES,
                        OrderBookParamsV3.Includes.BASE_ASSET,
                        OrderBookParamsV3.Includes.QUOTE_ASSET
                )
                .build()

        return api
                .v3
                .orderBooks
                .getById(
                        baseAssetCode = baseAsset,
                        quoteAssetCode = quoteAsset,
                        params = params
                )
                .toSingle()
                .map(::OrderBook)
                .toMaybe()
    }
}