package org.tokend.template.features.booking.model

import org.tokend.sdk.api.integrations.booking.model.generated.resources.BookingResource
import org.tokend.template.data.model.SimpleAsset
import java.math.BigDecimal

class ActiveBookingRecord(
        val id: String,
        val time: BookingTime,
        val room: BookingRoom,
        val seatsCount: Int
) {
    // TODO: Actual data
    constructor(source: BookingResource): this(
            id = source.id,
            time = BookingTime(source.startTime, source.endTime),
            room = BookingRoom(
                    id = "",
                    name = "Mocked room",
                    seatsCount = 404,
                    price = BigDecimal.ONE,
                    priceAsset = SimpleAsset("UAH")
            ),
            seatsCount = 404
    )
}