package org.tokend.template.features.booking.add.model

interface BookingInfoHolder {
    val bookingTime: BookingTime
    val availableSeats: Int
}