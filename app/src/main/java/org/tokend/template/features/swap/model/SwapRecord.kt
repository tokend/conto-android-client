package org.tokend.template.features.swap.model

import com.fasterxml.jackson.databind.ObjectMapper
import org.tokend.sdk.api.generated.resources.SwapResource
import org.tokend.sdk.utils.extentions.decodeHex
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.SimpleAsset
import java.math.BigDecimal
import java.util.*

class SwapRecord(
        val sourceAccountId: String,
        val destAccountId: String,
        val baseAmount: BigDecimal,
        val baseAsset: Asset,
        val quoteAmount: BigDecimal,
        val quoteAsset: Asset,
        val hash: String,
        val secret: ByteArray?,
        val state: SwapState,
        val isIncoming: Boolean,
        val createdAt: Date,
        var counterpartyEmail: String?
) {
    companion object {
        fun fromResource(source: SwapResource,
                         secret: ByteArray?,
                         state: SwapState,
                         isIncoming: Boolean,
                         objectMapper: ObjectMapper): SwapRecord {
            val quoteAmountDetails =
                    objectMapper.treeToValue(source.details, SwapQuoteAmountDetails::class.java)

            return SwapRecord(
                    sourceAccountId = source.source.id,
                    destAccountId = source.destination.id,
                    baseAmount = source.amount,
                    baseAsset = SimpleAsset(source.asset),
                    quoteAmount = quoteAmountDetails.quoteAmount,
                    // TODO: Actual
                    quoteAsset = SimpleAsset(quoteAmountDetails.quoteAssetCode),
                    hash = source.secretHash,
                    createdAt = source.createdAt,
                    state = state,
                    secret = secret,
                    isIncoming = isIncoming,
                    counterpartyEmail = null
            )
        }
    }

    val hashBytes: ByteArray
        get() = hash.decodeHex()

    override fun hashCode(): Int {
        return hash.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is SwapRecord && other.hash == this.hash
    }
}