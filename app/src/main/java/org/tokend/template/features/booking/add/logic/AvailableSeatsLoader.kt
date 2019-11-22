package org.tokend.template.features.booking.add.logic

import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import org.tokend.template.data.model.SimpleAsset
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.features.booking.model.BookingRoom
import org.tokend.template.features.booking.model.BookingTime
import java.math.BigDecimal

typealias RoomAvailableSeats = Pair<BookingRoom, Int>

class AvailableSeatsLoader(private val apiProvider: ApiProvider) {
    private val priceAsset = SimpleAsset("UAH")

    fun getAvailableSeats(time: BookingTime): Single<List<RoomAvailableSeats>> {
        return { Thread.sleep(1000) }
                .toSingle()
                .map { listOf(
                        BookingRoom("1", "Big room #1", BigDecimal("100"),
                                priceAsset, 20) to 15,
                        BookingRoom("1", "Yellow room", BigDecimal("250"),
                                priceAsset, 10) to 3
                )}
    }
}