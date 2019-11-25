package org.tokend.template.features.booking.model

import org.tokend.sdk.api.integrations.booking.model.generated.resources.BusinessResource
import org.tokend.sdk.utils.BigDecimalUtil
import org.tokend.template.data.model.Asset

class BookingBusinessRecord(
        val id: String,
        val calendarId: String,
        val rooms: List<BookingRoom>
) {
    companion object {
        fun fromResource(source: BusinessResource,
                         assetsMap: Map<String, Asset>): BookingBusinessRecord {
            val specificDetails = source.bookingDetails.specificDetails
            val payloads = specificDetails?.payloads
                    ?: throw IllegalArgumentException("Resource must have payloads (room IDs)")
            val roomsMeta = source.details
                    ?.get("rooms_meta")
                    ?.fields()
                    ?.asSequence()
                    ?.map { it.key to it.value }
                    ?.toMap()
                    ?: throw IllegalArgumentException("Resource must have rooms meta in details")

            val rooms = payloads.map { roomId ->
                val priceData = specificDetails.prices[roomId]
                        ?: throw IllegalStateException("No price for room $roomId")

                BookingRoom(
                        id = roomId,
                        name = roomsMeta[roomId]?.get("name")?.asText()
                                ?: throw IllegalStateException("No name for room $roomId"),
                        price = priceData["amount"].asText().let { BigDecimalUtil.valueOf(it) },
                        priceAsset = assetsMap[priceData["asset"].asText()]
                                ?: throw IllegalStateException("Price asset for room $roomId is not in the map"),
                        seatsCount = specificDetails.capacity[roomId].intValue()
                )
            }

            return BookingBusinessRecord(
                    id = source.id,
                    calendarId = source.calendar.id,
                    rooms = rooms
            )
        }
    }
}