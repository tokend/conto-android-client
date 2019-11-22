package org.tokend.template.features.booking.add.model

import org.tokend.template.features.booking.add.logic.RoomAvailableSeats
import org.tokend.template.features.booking.model.BookingTime

interface BookingInfoHolder {
    val bookingTime: BookingTime
    val roomAvailableSeats: List<RoomAvailableSeats>
    val selectedRoom: RoomAvailableSeats
}