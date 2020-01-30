package org.tokend.template.features.assets.sell.model

import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.BalanceRecord
import java.math.BigDecimal

interface MarketplaceSellInfoHolder {
    val amount: BigDecimal
    val balance: BalanceRecord
    val price: BigDecimal
    val priceAsset: Asset
    val cardNumber: String
}