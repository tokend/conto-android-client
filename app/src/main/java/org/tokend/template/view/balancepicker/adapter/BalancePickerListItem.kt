package org.tokend.template.view.balancepicker.adapter

import org.tokend.template.features.assets.model.Asset
import org.tokend.template.features.balances.model.BalanceRecord
import java.math.BigDecimal

class BalancePickerListItem(
        val available: BigDecimal?,
        val asset: Asset,
        val isEnough: Boolean,
        val source: BalanceRecord?
) {
    val displayedName: String = asset.name ?: asset.code

    constructor(source: BalanceRecord,
                available: BigDecimal? = source.available,
                required: BigDecimal = BigDecimal.ZERO) : this(
            asset = source.asset,
            available = available,
            isEnough = available == null || available >= required,
            source = source
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BalancePickerListItem) return false

        if (available != other.available) return false
        if (asset != other.asset) return false
        if (isEnough != other.isEnough) return false

        return true
    }

    override fun hashCode(): Int {
        var result = available.hashCode()
        result = 31 * result + asset.hashCode()
        result = 31 * result + isEnough.hashCode()
        return result
    }
}
