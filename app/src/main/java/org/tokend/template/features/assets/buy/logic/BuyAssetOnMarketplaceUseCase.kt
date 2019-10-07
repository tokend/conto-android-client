package org.tokend.template.features.assets.buy.logic

import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import io.reactivex.rxkotlin.toSingle
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.integrations.marketplace.model.MarketplaceBuyRequestAttributes
import org.tokend.sdk.api.integrations.marketplace.model.MarketplaceInvoiceData
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord
import java.math.BigDecimal

class BuyAssetOnMarketplaceUseCase(
        private val amount: BigDecimal,
        private val quoteAssetCode: String,
        private val offer: MarketplaceOfferRecord,
        private val apiProvider: ApiProvider,
        private val repositoryProvider: RepositoryProvider,
        private val walletInfoProvider: WalletInfoProvider
) {
    private lateinit var accountId: String
    private lateinit var buyRequest: MarketplaceBuyRequestAttributes

    fun perform(): Single<MarketplaceInvoiceData> {
        return getAccountId()
                .doOnSuccess { accountId ->
                    this.accountId = accountId
                }
                .flatMap {
                    getBuyRequest()
                }
                .doOnSuccess { buyRequest ->
                    this.buyRequest = buyRequest
                }
                .flatMap {
                    getInvoice()
                }
                .doOnSuccess {
                    updateRepositories()
                }
    }

    private fun getAccountId(): Single<String> {
        return walletInfoProvider
                .getWalletInfo()
                ?.accountId
                .toMaybe()
                .switchIfEmpty(Single.error(IllegalStateException("Missing account ID")))
    }

    private fun getBuyRequest(): Single<MarketplaceBuyRequestAttributes> {
        return {
            MarketplaceBuyRequestAttributes(
                    amount = amount,
                    offerId = offer.id.toLong(),
                    senderAccountId = accountId,
                    senderEmail = null,
                    paymentMethodId = offer.paymentMethods
                            .first {
                                it.asset.code == quoteAssetCode
                            }
                            .id
                            .toLong()
            )
        }.toSingle()
    }

    private fun getInvoice(): Single<MarketplaceInvoiceData> {
        return apiProvider.getApi()
                .integrations
                .marketplace
                .submitBuyRequest(buyRequest)
                .toSingle()
    }

    private fun updateRepositories() {
        listOf(
                repositoryProvider.marketplaceOffers(null),
                repositoryProvider.marketplaceOffers(offer.company.id)
        ).forEach {
            it.updateAvailableAmount(offer.id, -amount)
        }
    }
}