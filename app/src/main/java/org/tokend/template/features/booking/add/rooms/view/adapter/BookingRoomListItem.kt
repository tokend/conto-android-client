package org.tokend.template.features.booking.add.rooms.view.adapter

import org.tokend.template.features.booking.model.BookingRoom

class BookingRoomListItem(
        val name: String,
        val availableSeatsCount: Int,
        val logoUrl: String?,
        val source: BookingRoom?
) {
    constructor(source: BookingRoom): this(
            name = source.name,
            availableSeatsCount = source.seatsCount,
            logoUrl = null,
            source = source
    )
}