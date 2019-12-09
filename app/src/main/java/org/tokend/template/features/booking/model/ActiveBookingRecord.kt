package org.tokend.template.features.booking.model

import org.tokend.sdk.api.integrations.booking.model.generated.resources.BookingResource

class ActiveBookingRecord(
        val id: String,
        val time: BookingTime,
        val room: BookingRoom,
        val seatsCount: Int,
        val reference: String
) {
    constructor(source: BookingResource,
                business: BookingBusinessRecord): this(
            id = source.id,
            time = BookingTime(source.startTime, source.endTime),
            room = business.rooms.find { it.id == source.payload }
                    ?: throw IllegalStateException("Room ${source.payload} is not in business ${business.id}"),
            seatsCount = source.participants,
            reference = source.reference
    )
}