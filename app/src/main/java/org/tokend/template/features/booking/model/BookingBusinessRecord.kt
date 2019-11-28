package org.tokend.template.features.booking.model

import org.tokend.sdk.api.integrations.booking.model.generated.resources.BusinessResource
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
            val roomsMeta = source.details
                    ?.get("rooms_meta")
                    ?.fields()
                    ?.asSequence()
                    ?.map { it.key to it.value }
                    ?.toMap()
                    ?: throw IllegalArgumentException("Resource must have rooms meta in details")

            val rooms = specificDetails.map { (roomId, roomDetails) ->
                val priceData = roomDetails.price

                BookingRoom(
                        id = roomId,
                        name = roomsMeta[roomId]?.get("name")?.asText()
                                ?: throw IllegalStateException("No name for room $roomId"),
                        price = priceData.amount,
                        priceAsset = assetsMap[priceData.asset]
                                ?: throw IllegalStateException("Price asset for room $roomId is not in the map"),
                        seatsCount = roomDetails.capacity
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