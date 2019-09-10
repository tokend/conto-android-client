package org.tokend.template.data.model

import com.fasterxml.jackson.databind.ObjectMapper
import org.tokend.sdk.api.generated.resources.AtomicSwapAskResource
import java.io.Serializable
import java.math.BigDecimal

class AtomicSwapAskRecord(
        val id: String,
        val asset: AssetRecord,
        var amount: BigDecimal,
        val isCanceled: Boolean,
        val quoteAssets: List<QuoteAsset>,
        val company: CompanyRecord,
        val price: BigDecimal,
        val priceAsset: Asset
) : Serializable {
    constructor(source: AtomicSwapAskResource,
                assetsMap: Map<String, Asset>,
                companiesMap: Map<String, CompanyRecord>,
                urlConfig: UrlConfig?,
                mapper: ObjectMapper
    ) : this(
            id = source.id,
            asset = AssetRecord.fromResource(source.baseAsset, urlConfig, mapper),
            amount = source.availableAmount,
            isCanceled = source.isCanceled,
            company = companiesMap[source.owner.id]
                    ?: throw IllegalArgumentException("No company info found for ${source.owner.id}"),
            quoteAssets = source.quoteAssets
                    .map {
                        QuoteAsset(
                                code = it.quoteAsset,
                                trailingDigits = 6,
                                price = it.price,
                                name = assetsMap[it.quoteAsset]?.name,
                                logoUrl = assetsMap[it.quoteAsset]?.logoUrl
                        )
                    },
            price = source.quoteAssets.first().price,
            priceAsset = assetsMap[companiesMap[source.owner.id]!!.conversionAssetCode]!!
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
            override val name: String?,
            override val logoUrl: String?
    ) : Asset
}