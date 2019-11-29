package org.tokend.template.features.assets.details.refund.logic

import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.features.trade.orderbook.model.OrderBook
import org.tokend.template.features.trade.orderbook.model.OrderBookEntryRecord
import org.tokend.template.features.trade.orderbook.repository.OrderBookRepository

/**
 * Loads offer for asset refund if it's available.
 *
 * @param repositoryProvider used to get [OrderBookRepository] for
 * specified assets
 */
class AssetRefundOfferLoader(
        private val repositoryProvider: RepositoryProvider
) {
    class RefundNotAvailableException : Exception()

    /**
     * Loads refund offer for given asset pair or
     * emits [RefundNotAvailableException] if it's not available.
     *
     * @see RefundNotAvailableException
     */
    fun load(assetToRefund: String, refundAssetCode: String): Single<OrderBookEntryRecord> {
        val repository = repositoryProvider.orderBook(
                baseAsset = assetToRefund,
                quoteAsset = refundAssetCode
        )

        return repository
                .updateIfNotFreshDeferred()
                .andThen(Single.defer { repository.item!!.toSingle() })
                .map(OrderBook::buyEntries)
                .flatMap { buyEntries ->
                    buyEntries.firstOrNull()?.toSingle()
                            ?: Single.error(RefundNotAvailableException())
                }
    }
}