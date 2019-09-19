package org.tokend.template.features.swap.model

import com.fasterxml.jackson.databind.ObjectMapper
import org.tokend.sdk.api.generated.resources.SwapResource
import org.tokend.sdk.utils.extentions.decodeHex
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.SimpleAsset
import java.math.BigDecimal
import java.util.*

class SwapRecord(
        val id: String,
        val sourceAccountId: String,
        val destAccountId: String,
        val baseAmount: BigDecimal,
        var baseAsset: Asset,
        val quoteAmount: BigDecimal,
        var quoteAsset: Asset,
        val hash: String,
        val secret: ByteArray?,
        val state: SwapState,
        val isIncoming: Boolean,
        val createdAt: Date,
        var counterpartyEmail: String?,
        val sourceSystemIndex: Int
) {
    companion object {
        fun fromResource(source: SwapResource,
                         secret: ByteArray?,
                         state: SwapState,
                         isIncoming: Boolean,
                         objectMapper: ObjectMapper,
                         sourceSystemIndex: Int): SwapRecord {
            val quoteAmountDetails =
                    objectMapper.treeToValue(source.details, SwapQuoteAmountDetails::class.java)

            return SwapRecord(
                    id = source.id,
                    sourceAccountId = source.source.id,
                    destAccountId = source.destination.id,
                    baseAmount = source.amount,
                    baseAsset = SimpleAsset(source.asset.id),
                    quoteAmount = quoteAmountDetails.quoteAmount,
                    quoteAsset = SimpleAsset(quoteAmountDetails.quoteAssetCode),
                    hash = source.secretHash,
                    createdAt = source.createdAt,
                    state = state,
                    secret = secret,
                    isIncoming = isIncoming,
                    counterpartyEmail = null,
                    sourceSystemIndex = sourceSystemIndex
            )
        }
    }

    val hashBytes: ByteArray
        get() = hash.decodeHex()
}