package org.tokend.template.features.booking.view.adapter

import org.tokend.template.features.booking.model.ActiveBookingRecord
import java.util.*

class ActiveBookingListItem(
        val seatsCount: Int,
        val dateFrom: Date,
        val dateTo: Date
) {
    constructor(source: ActiveBookingRecord): this(
            seatsCount = source.seatsCount,
            dateFrom = source.time.from,
            dateTo = source.time.to
    )
}