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
        val destId: String?,
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
        val sourceEmail: String,
        val destEmail: String,
        val sourceSystemIndex: Int
) {
    companion object {
        fun fromResource(source: SwapResource,
                         secret: ByteArray?,
                         state: SwapState,
                         isIncoming: Boolean,
                         objectMapper: ObjectMapper,
                         sourceSystemIndex: Int,
                         destId: String?): SwapRecord {
            val details =
                    objectMapper.treeToValue(source.details, SwapDetails::class.java)

            return SwapRecord(
                    id = source.id,
                    sourceAccountId = source.source.id,
                    destAccountId = source.destination.id,
                    baseAmount = source.amount,
                    baseAsset = SimpleAsset(source.asset.id),
                    quoteAmount = details.quoteAmount,
                    quoteAsset = SimpleAsset(details.quoteAssetCode),
                    hash = source.secretHash,
                    createdAt = source.createdAt,
                    state = state,
                    secret = secret,
                    isIncoming = isIncoming,
                    sourceEmail = details.sourceEmail,
                    destEmail = details.destEmail,
                    sourceSystemIndex = sourceSystemIndex,
                    destId = destId
            )
        }
    }

    val counterpartyEmail: String
        get() = if (isIncoming) sourceEmail else destEmail

    val hashBytes: ByteArray
        get() = hash.decodeHex()
}