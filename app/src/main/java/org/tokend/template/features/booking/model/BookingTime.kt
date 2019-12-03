package org.tokend.template.features.booking.model

import java.util.*

data class BookingTime(
        val from: Date,
        val to: Date
) {
    val isZero: Boolean
        get() = from.time == 0L && to.time == 0L

    companion object {
        val ZERO = BookingTime(Date(0), Date(0))
    }
}