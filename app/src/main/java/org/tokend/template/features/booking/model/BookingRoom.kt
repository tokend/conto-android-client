package org.tokend.template.features.booking.model

import dagger.multibindings.IntoMap
import org.tokend.template.data.model.Asset
import java.math.BigDecimal

class BookingRoom(
        val id: String,
        val name: String,
        val price: BigDecimal,
        val priceAsset: Asset,
        val seatsCount: Int,
        val logoUrl: String?
) {
    override fun equals(other: Any?): Boolean {
        return other is BookingRoom && other.id == this.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}