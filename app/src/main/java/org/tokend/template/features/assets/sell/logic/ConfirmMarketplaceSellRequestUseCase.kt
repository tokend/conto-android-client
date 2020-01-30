package org.tokend.template.features.assets.sell.logic

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import org.tokend.rx.extensions.toCompletable
import org.tokend.sdk.api.base.model.DataEntity
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.features.assets.sell.model.MarketplaceSellRequest
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction

class ConfirmMarketplaceSellRequestUseCase(
        private val request: MarketplaceSellRequest,
        private val repositoryProvider: RepositoryProvider,
        private val apiProvider: ApiProvider,
        private val accountProvider: AccountProvider
) {
    private lateinit var networkParams: NetworkParams
    private lateinit var paymentTransaction: Transaction

    fun perform(): Completable {
        return getNetworkParams()
                .doOnSuccess { networkParams ->
                    this.networkParams = networkParams
                }
                .flatMap {
                    getPaymentTransaction()
                }
                .doOnSuccess { paymentTransaction ->
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

    private fun getNetworkParams(): Single<NetworkParams> {
        return repositoryProvider.systemInfo().getNetworkParams()
    }

    private fun getPaymentTransaction(): Single<Transaction> {
        val account = accountProvider.getAccount()
                ?: return Single.error(IllegalStateException("No account found"))

        return request
                .getPaymentToMarketplace()
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
                                "price" to request.price,
                                "price_asset" to request.priceAsset.code,
                                "base_asset" to request.asset.code
                        ),
                        "relationships" to mapOf(
                                "payment_methods" to DataEntity(
                                        request.paymentMethods.indices.map { i ->
                                            mapOf(
                                                    "id" to i.toString(),
                                                    "type" to "create-payment-method"
                                            )
                                        }
                                )
                        )
                ),
                "included" to request.paymentMethods.mapIndexed { i, method ->
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
                .toCompletable()
                .toSingleDefault(true)
    }

    private fun updateRepositories() {
        repositoryProvider.balances().updateBalanceByDelta(
                request.sourceBalanceId,
                request.amount.negate()
        )
        repositoryProvider.marketplaceOffers(null).invalidate()
    }
}