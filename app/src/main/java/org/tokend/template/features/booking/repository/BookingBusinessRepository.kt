package org.tokend.template.features.booking.repository

import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import io.reactivex.rxkotlin.toSingle
import org.tokend.rx.extensions.toSingle
import org.tokend.template.data.repository.assets.AssetsRepository
import org.tokend.template.data.repository.base.SimpleSingleItemRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.features.booking.model.BookingBusinessRecord

class BookingBusinessRepository(
        private val apiProvider: ApiProvider,
        private val assetsRepository: AssetsRepository
) : SimpleSingleItemRepository<BookingBusinessRecord>() {
    override fun getItem(): Observable<BookingBusinessRecord> {
        return apiProvider.getApi().integrations.booking
                .getBusiness(DEFAULT_BUSINESS_ID)
                .toSingle()
                .flatMap { business ->
                    assetsRepository.ensureAssets(
                            business
                                    .bookingDetails
                                    .specificDetails
                                    .map { (_, roomDetails) ->
                                        roomDetails.price.asset
                                    }
                    ).map { business to it }
                }
                .map { (business, assetsMap) ->
                    BookingBusinessRecord.fromResource(business, assetsMap)
                }
                .toObservable()
    }

    fun ensureItem(): Single<BookingBusinessRecord> {
        return item.toMaybe()
                .switchIfEmpty(updateDeferred().andThen({ item!! }.toSingle()))
    }

    companion object {
        const val DEFAULT_BUSINESS_ID = "1"
    }
}