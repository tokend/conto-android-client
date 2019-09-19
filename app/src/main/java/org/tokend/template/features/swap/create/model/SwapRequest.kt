package org.tokend.template.features.swap.create.model

import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.BalanceRecord
import java.io.Serializable
import java.math.BigDecimal

class SwapRequest(
        val sourceAccountId: String,
        val destAccountId: String,
        val baseAmount: BigDecimal,
        val baseBalance: BalanceRecord,
        val destEmail: String,
        val quoteAmount: BigDecimal,
        val quoteAsset: Asset,
        val secret: ByteArray
) : Serializable