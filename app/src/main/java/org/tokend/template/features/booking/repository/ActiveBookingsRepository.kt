package org.tokend.template.features.booking.repository

import io.reactivex.Single
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.base.params.PagingOrder
import org.tokend.sdk.api.base.params.PagingParamsV2
import org.tokend.sdk.api.integrations.booking.params.BookingsPageParams
import org.tokend.sdk.utils.SimplePagedResourceLoader
import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.data.repository.base.SimpleMultipleItemsRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.booking.model.ActiveBookingRecord

class ActiveBookingsRepository(
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val bookingBusinessRepository: BookingBusinessRepository,
        itemsCache: RepositoryCache<ActiveBookingRecord>
) : SimpleMultipleItemsRepository<ActiveBookingRecord>(itemsCache) {

    override fun getItems(): Single<List<ActiveBookingRecord>> {
        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))

        val loader = SimplePagedResourceLoader({ nextCursor ->
            signedApi.integrations.booking
                    .getBookings(
                            params = BookingsPageParams(
                                    owner = accountId,
                                    state = 1,
                                    pagingParams = PagingParamsV2(
                                            order = PagingOrder.DESC,
                                            page = nextCursor,
                                            limit = PAGE_LIMIT
                                    )
                            )
                    )
        })

        return bookingBusinessRepository
                .ensureItem()
                .flatMap { business ->
                    loader
                            .loadAll()
                            .toSingle()
                            .map { bookings ->
                                bookings to business
                            }
                }
                .map { (bookings, business) ->
                    bookings.map { booking ->
                        ActiveBookingRecord(booking, business)
                    }
                }
    }

    companion object {
        private const val PAGE_LIMIT = 20
    }
}