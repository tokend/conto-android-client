package org.tokend.template.features.booking.model

import org.tokend.sdk.api.integrations.booking.model.generated.inner.WorkHours
import org.tokend.sdk.api.integrations.booking.model.generated.resources.BusinessResource
import org.tokend.template.data.model.Asset
import java.util.*

class BookingBusinessRecord(
        val id: String,
        val calendarId: String,
        val rooms: List<BookingRoom>,
        val workingDays: Map<Int, WorkHours>
) {
    fun isWorkingTime(calendar: Calendar): Boolean {
        val workingHours = workingDays[calendar[Calendar.DAY_OF_WEEK]]
                ?: return false

        val checkingMinute = calendar[Calendar.HOUR_OF_DAY] * 60 + calendar[Calendar.MINUTE]
        val startMinute = workingHours.start.hours * 60 + workingHours.start.minutes
        val endMinute = workingHours.end.hours * 60 + workingHours.end.minutes

        return checkingMinute in startMinute..endMinute
    }

    fun isWorkingRange(from: Calendar, to: Calendar): Boolean {
        return from[Calendar.DAY_OF_WEEK] == to[Calendar.DAY_OF_WEEK]
                && isWorkingTime(from)
                && isWorkingTime(to)
    }

    companion object {
        fun fromResource(source: BusinessResource,
                         assetsMap: Map<String, Asset>): BookingBusinessRecord {
            val specificDetails = source.bookingDetails.specificDetails
            val roomsMeta = source.details
                    ?.get("rooms_meta")
                    ?.fields()
                    ?.asSequence()
                    ?.map { it.key to it.value }
                    ?.toMap()
                    ?: throw IllegalArgumentException("Resource must have rooms meta in details")

            val rooms = specificDetails.map { (roomId, roomDetails) ->
                val priceData = roomDetails.price
                val meta = roomsMeta[roomId]

                BookingRoom(
                        id = roomId,
                        name = meta?.get("name")?.asText()
                                ?: throw IllegalStateException("No name for room $roomId"),
                        price = priceData.amount,
                        priceAsset = assetsMap[priceData.asset]
                                ?: throw IllegalStateException("Price asset for room $roomId is not in the map"),
                        seatsCount = roomDetails.capacity,
                        logoUrl = meta.get("logo_url")?.asText()
                )
            }

            val daysMap = mapOf(
                    "Sunday" to Calendar.SUNDAY,
                    "Monday" to Calendar.MONDAY,
                    "Tuesday" to Calendar.TUESDAY,
                    "Wednesday" to Calendar.WEDNESDAY,
                    "Thursday" to Calendar.THURSDAY,
                    "Friday" to Calendar.FRIDAY,
                    "Saturday" to Calendar.SATURDAY
            )

            val workingDays = source
                    .workDays
                    ?.mapKeys { daysMap.getValue(it.key) }
                    ?: emptyMap()

            return BookingBusinessRecord(
                    id = source.id,
                    calendarId = source.calendar.id,
                    rooms = rooms,
                    workingDays = workingDays
            )
        }
    }
}