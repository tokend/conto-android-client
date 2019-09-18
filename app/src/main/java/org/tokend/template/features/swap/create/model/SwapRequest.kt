package org.tokend.template.features.swap.create.model

import org.tokend.template.data.model.Asset
import java.io.Serializable
import java.math.BigDecimal

class SwapRequest(
        val sourceAccountId: String,
        val baseBalanceId: String,
        val destAccountId: String,
        val baseAmount: BigDecimal,
        val baseAsset: Asset,
        val destEmail: String,
        val quoteAmount: BigDecimal,
        val quoteAsset: Asset,
        val secret: ByteArray
) : Serializable