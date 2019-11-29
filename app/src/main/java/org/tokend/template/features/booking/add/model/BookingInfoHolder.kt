package org.tokend.template.features.booking.add.model

import org.tokend.template.features.booking.model.BookingBusinessRecord
import org.tokend.template.features.booking.model.BookingRoom
import org.tokend.template.features.booking.model.BookingTime

interface BookingInfoHolder {
    val business: BookingBusinessRecord
    val bookingTime: BookingTime
    val seatsCount: Int
    val availableRooms: Collection<BookingRoom>
    val selectedRoom: BookingRoom
}