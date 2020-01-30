package org.tokend.template.features.assets.sell.logic

import com.google.gson.reflect.TypeToken
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import io.reactivex.rxkotlin.toSingle
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.base.model.AttributesEntity
import org.tokend.sdk.api.integrations.marketplace.model.MarketplacePaymentMethodType
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.BalanceRecord
import org.tokend.template.data.model.SimpleAsset
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord
import org.tokend.template.features.assets.sell.model.MarketplaceSellPaymentMethod
import org.tokend.template.features.assets.sell.model.MarketplaceSellRequest
import java.math.BigDecimal

class CreateMarketplaceSellRequestUseCase(
        private val amount: BigDecimal,
        private val balance: BalanceRecord,
        private val price: BigDecimal,
        private val priceAsset: Asset,
        private val cardNumber: String,
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider
) {
    private lateinit var sourceAccountId: String
    private lateinit var marketplaceAccountId: String

    fun perform(): Single<MarketplaceSellRequest> {
        return getSourceAccountId()
                .doOnSuccess { sourceAccountId ->
                    this.sourceAccountId = sourceAccountId
                }
                .flatMap {
                    getMarketplaceAccountId()
                }
                .doOnSuccess { marketplaceAccountId ->
                    this.marketplaceAccountId = marketplaceAccountId
                }
                .flatMap {
                    getRequest()
                }
    }

    private fun getSourceAccountId(): Single<String> {
        return walletInfoProvider.getWalletInfo()
                ?.accountId
                .toMaybe()
                .switchIfEmpty(Single.error(IllegalStateException("No wallet info found")))
    }

    private fun getMarketplaceAccountId(): Single<String> {
        return apiProvider.getApi().customRequests.get<AttributesEntity<Map<String, String>>>(
                url = "integrations/marketplace/info",
                responseType =
                object : TypeToken<AttributesEntity<Map<String, String>>>() {}.type
        )
                .toSingle()
                .map { it.attributes.getValue("payment_account") }
    }

    private fun getRequest(): Single<MarketplaceSellRequest> {
        val quoteAsset = SimpleAsset(QUOTE_ASSET_CODE)

        return MarketplaceSellRequest(
                amount = amount,
                asset = balance.asset,
                sourceAccountId = sourceAccountId,
                sourceBalanceId = balance.id,
                price = price,
                priceAsset = priceAsset,
                marketplaceAccountId = marketplaceAccountId,
                paymentMethods = setOf(
                        MarketplaceSellPaymentMethod(
                                method = MarketplaceOfferRecord.PaymentMethod(
                                        id = "",
                                        type = MarketplacePaymentMethodType.INTERNAL,
                                        asset = quoteAsset
                                ),
                                destination = sourceAccountId
                        ),
                        MarketplaceSellPaymentMethod(
                                method = MarketplaceOfferRecord.PaymentMethod(
                                        id = "",
                                        type = MarketplacePaymentMethodType.FORBILL,
                                        asset = quoteAsset
                                ),
                                destination = cardNumber
                        )
                )
        ).toSingle()
    }

    companion object {
        // 😈.
        private const val QUOTE_ASSET_CODE = "UAH"
    }
}