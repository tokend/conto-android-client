package org.tokend.template.features.booking.add.rooms.view.adapter

import org.tokend.template.features.booking.add.logic.RoomAvailableSeats

class BookingRoomListItem(
        val name: String,
        val availableSeatsCount: Int,
        val logoUrl: String?,
        val source: RoomAvailableSeats?
) {
    constructor(source: RoomAvailableSeats): this(
            name = source.first.name,
            availableSeatsCount = source.second,
            logoUrl = null,
            source = source
    )
}