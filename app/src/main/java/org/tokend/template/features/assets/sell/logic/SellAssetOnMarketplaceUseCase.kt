package org.tokend.template.features.assets.sell.logic

import com.google.gson.reflect.TypeToken
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import io.reactivex.rxkotlin.toSingle
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.base.model.AttributesEntity
import org.tokend.sdk.api.base.model.DataEntity
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.history.SimpleFeeRecord
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.assets.sell.model.MarketplaceSellPaymentMethod
import org.tokend.template.features.send.model.PaymentFee
import org.tokend.template.features.send.model.PaymentRecipient
import org.tokend.template.features.send.model.PaymentRequest
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction
import java.math.BigDecimal

class SellAssetOnMarketplaceUseCase(
        private val amount: BigDecimal,
        private val asset: Asset,
        private val price: BigDecimal,
        private val priceAssetCode: String,
        paymentMethods: Collection<MarketplaceSellPaymentMethod>,
        private val repositoryProvider: RepositoryProvider,
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val accountProvider: AccountProvider
) {
    private val paymentMethodsInOrder = paymentMethods.toList()

    private lateinit var sourceBalanceId: String
    private lateinit var marketplaceAccountId: String
    private lateinit var networkParams: NetworkParams
    private lateinit var paymentTransaction: Transaction

    fun perform(): Completable {
        return getMarketplaceAccountId()
                .doOnSuccess { marketplaceAccountId ->
                    this.marketplaceAccountId = marketplaceAccountId
                }
                .flatMap {
                    getNetworkParams()
                }
                .doOnSuccess { networkParams->
                    this.networkParams = networkParams
                }
                .flatMap {
                    getSourceBalanceId()
                }
                .doOnSuccess { sourceBalanceId ->
                    this.sourceBalanceId = sourceBalanceId
                }
                .flatMap {
                    getPaymentTransaction()
                }
                .doOnSuccess {paymentTransaction ->
                    this.paymentTransaction = paymentTransaction
                }
                .flatMap {
                    createOffer()
                }
                .doOnSuccess {
                    updateRepositories()
                }
                .ignoreElement()
    }

    private fun getMarketplaceAccountId(): Single<String> {
        return apiProvider.getApi().customRequests.get<DataEntity<AttributesEntity<Map<String, String>>>>(
                url = "integrations/marketplace/info",
                responseType =
                object : TypeToken<DataEntity<AttributesEntity<Map<String, String>>>>() {}.type
        )
                .toSingle()
                .map { it.data.attributes.getValue("payment_account") }
    }

    private fun getSourceBalanceId(): Single<String> {
        return repositoryProvider.balances()
                .itemsList
                .find { it.assetCode == asset.code }
                ?.id
                .toMaybe()
                .switchIfEmpty(Single.error(IllegalStateException("No balance ID found for ${asset.code}")))
    }

    private fun getNetworkParams(): Single<NetworkParams> {
        return repositoryProvider.systemInfo().getNetworkParams()
    }

    private fun getPaymentTransaction(): Single<Transaction> {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))
        val account = accountProvider.getAccount()
                ?: return Single.error(IllegalStateException("No account found"))

        return PaymentRequest(
                amount = amount,
                asset = asset,
                fee = PaymentFee(SimpleFeeRecord.ZERO, SimpleFeeRecord.ZERO, false),
                paymentSubject = null,
                actualPaymentSubject = null,
                recipient = PaymentRecipient(marketplaceAccountId),
                senderAccountId = accountId,
                senderBalanceId = sourceBalanceId
        )
                .toTransaction(networkParams, account)
                .toSingle()
    }

    private fun createOffer(): Single<Boolean> {
        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))

        val requestBody = mapOf(
                "data" to mapOf(
                        "type" to "create-marketplace-offer",
                        "attributes" to mapOf(
                                "payment_tx_envelope" to paymentTransaction.getEnvelope().toBase64(),
                                "price" to price,
                                "price_asset" to priceAssetCode,
                                "base_asset" to asset.code
                        ),
                        "relationships" to mapOf(
                                "payment_methods" to DataEntity(
                                        paymentMethodsInOrder.indices.map { i ->
                                            mapOf(
                                                    "id" to i.toString(),
                                                    "type" to "create-payment-method"
                                            )
                                        }
                                )
                        )
                ),
                "included" to paymentMethodsInOrder.mapIndexed { i, method ->
                    mapOf(
                            "id" to i.toString(),
                            "type" to "create-payment-method",
                            "attributes" to mapOf(
                                    "asset" to method.method.asset.code,
                                    "type" to method.method.type.value,
                                    "destination" to method.destination
                            )
                    )
                }
        )

        return signedApi.customRequests.post(
                url = "integrations/marketplace/offers",
                body = requestBody,
                responseClass = Void::class.java
        )
                .toSingle()
                .map { true }
    }

    private fun updateRepositories() {
        repositoryProvider.balances().updateBalanceByDelta(sourceBalanceId, amount.negate())
        repositoryProvider.marketplaceOffers(null).invalidate()
    }
}