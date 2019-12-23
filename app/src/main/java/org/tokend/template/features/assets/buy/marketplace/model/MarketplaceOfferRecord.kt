package org.tokend.template.features.assets.buy.marketplace.model

import org.tokend.sdk.api.integrations.marketplace.model.MarketplaceOfferResource
import org.tokend.sdk.api.integrations.marketplace.model.MarketplacePaymentMethodResource
import org.tokend.sdk.api.integrations.marketplace.model.MarketplacePaymentMethodType
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.CompanyRecord
import java.io.Serializable
import java.math.BigDecimal

class MarketplaceOfferRecord(
        val id: String,
        val asset: Asset,
        var amount: BigDecimal,
        val price: BigDecimal,
        val priceAsset: Asset,
        val paymentMethods: List<PaymentMethod>,
        val company: CompanyRecord,
        val isCanceled: Boolean
) : Serializable {
    class PaymentMethod(
            val id: String,
            val type: MarketplacePaymentMethodType,
            val asset: Asset
    ) : Serializable, Asset by asset {
        constructor(source: MarketplacePaymentMethodResource,
                    assetsMap: Map<String, Asset>) : this(
                id = source.id,
                asset = assetsMap[source.asset]
                        ?: throw IllegalStateException("Payment method asset ${source.asset} is not in the map"),
                type = source.type?.value?.let(MarketplacePaymentMethodType.Companion::fromValue)
                        ?: MarketplacePaymentMethodType.FORBILL
        )

        override val code: String
            get() = id

        // region 🙈
        private val isCreditCard: Boolean
            get() = type == MarketplacePaymentMethodType.FORBILL && asset.code == "UAH"

        override val name: String?
            get() = if (isCreditCard) "Credit card" else asset.name ?: asset.code
        // endregion
    }

    constructor(source: MarketplaceOfferResource,
                assetsMap: Map<String, Asset>,
                companiesMap: Map<String, CompanyRecord>) : this(
            id = source.id,
            asset = assetsMap[source.baseAsset]
                    ?: throw IllegalStateException("Base asset ${source.baseAsset} is not in the map"),
            amount = source.baseAmount,
            priceAsset = assetsMap[source.priceAsset]
                    ?: throw IllegalStateException("Price asset ${source.priceAsset} is not in the map"),
            price = source.price,
            company = companiesMap[source.owner]
                    ?: throw IllegalStateException("Owner company ${source.owner} is not in the map"),
            isCanceled = source.isCanceled,
            paymentMethods = source.paymentMethods.map { PaymentMethod(it, assetsMap) }
    )

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is MarketplaceOfferRecord && other.id == this.id
    }
}