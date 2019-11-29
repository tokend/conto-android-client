package org.tokend.template.features.booking.add.rooms.view.adapter

import org.tokend.template.data.model.Asset
import org.tokend.template.features.booking.model.BookingRoom
import java.math.BigDecimal

class BookingRoomListItem(
        val name: String,
        val price: BigDecimal,
        val priceAsset: Asset,
        val logoUrl: String?,
        val source: BookingRoom?
) {
    constructor(source: BookingRoom): this(
            name = source.name,
            price = source.price,
            priceAsset = source.priceAsset,
            logoUrl = source.logoUrl,
            source = source
    )
}