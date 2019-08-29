package org.tokend.template.data.model

import com.fasterxml.jackson.databind.ObjectMapper
import org.tokend.sdk.api.generated.resources.AtomicSwapAskResource
import java.io.Serializable
import java.math.BigDecimal

class AtomicSwapAskRecord(
        val id: String,
        val asset: AssetRecord,
        val amount: BigDecimal,
        val isCanceled: Boolean,
        val quoteAssets: List<QuoteAsset>
) : Serializable {
    constructor(source: AtomicSwapAskResource,
                assetsMap: Map<String, Asset>,
                urlConfig: UrlConfig?,
                mapper: ObjectMapper
    ) : this(
            id = source.id,
            asset = AssetRecord.fromResource(source.baseAsset, urlConfig, mapper),
            amount = source.availableAmount,
            isCanceled = source.isCanceled,
            quoteAssets = source.quoteAssets
                    .map {
                        QuoteAsset(
                                code = it.quoteAsset,
                                trailingDigits = 6,
                                price = it.price,
                                name = assetsMap[it.quoteAsset]?.name
                        )
                    }
                    .sortedByDescending { it.code == DEFAULT_QUOTE_ASSET }
    )

    override fun equals(other: Any?): Boolean {
        return other is AtomicSwapAskRecord && other.id == this.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    class QuoteAsset(
            override val code: String,
            override val trailingDigits: Int,
            val price: BigDecimal,
            override val name: String?
    ) : Asset

    companion object {
        private const val DEFAULT_QUOTE_ASSET = "UAH"
    }
}