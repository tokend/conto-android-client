package org.tokend.template.features.booking.add.logic

import io.reactivex.Single
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.integrations.booking.params.FreeRoomsParams
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.features.booking.model.BookingBusinessRecord
import org.tokend.template.features.booking.model.BookingRoom
import org.tokend.template.features.booking.model.BookingTime
import org.tokend.template.features.booking.repository.BookingBusinessRepository

class AvailableRoomsLoader(
        private val apiProvider: ApiProvider,
        private val bookingBusinessRepository: BookingBusinessRepository
) {
    fun getAvailableRooms(time: BookingTime,
                          seatsCount: Int): Single<List<BookingRoom>> {
        return getBusiness()
                .flatMap { business ->
                    getAvailableRoomIds(business, time, seatsCount)
                            .map { business to it }
                }
                .map { (business, availableRoomIds) ->
                    val roomsMap = business.rooms
                            .associateBy(BookingRoom::id)

                    availableRoomIds.map(roomsMap::getValue)
                }
    }

    private fun getBusiness(): Single<BookingBusinessRecord> {
        return bookingBusinessRepository.ensureItem()
    }

    private fun getAvailableRoomIds(business: BookingBusinessRecord,
                                    time: BookingTime,
                                    seatsCount: Int): Single<List<String>> {
        return apiProvider.getApi().integrations.booking
                .getFreeRooms(
                        calendarId = business.calendarId,
                        params = FreeRoomsParams(
                                startTime = time.from,
                                endTime = time.to,
                                participantsCount = seatsCount,
                                roomIds = business.rooms.map(BookingRoom::id)
                        )
                )
                .toSingle()
    }
}