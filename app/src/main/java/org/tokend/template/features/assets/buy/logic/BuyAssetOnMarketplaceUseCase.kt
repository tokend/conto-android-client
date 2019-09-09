package org.tokend.template.features.assets.buy.logic

import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.integrations.marketplace.model.MarketplaceInvoiceData
import org.tokend.template.data.model.AtomicSwapAskRecord
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.logic.transactions.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction
import org.tokend.wallet.xdr.CreateAtomicSwapBidRequest
import org.tokend.wallet.xdr.CreateAtomicSwapBidRequestOp
import org.tokend.wallet.xdr.Operation
import java.math.BigDecimal

class BuyAssetOnMarketplaceUseCase(
        private val amount: BigDecimal,
        private val quoteAssetCode: String,
        private val ask: AtomicSwapAskRecord,
        private val apiProvider: ApiProvider,
        private val repositoryProvider: RepositoryProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val accountProvider: AccountProvider
) {
    private lateinit var accountId: String
    private lateinit var networkParams: NetworkParams
    private lateinit var transaction: Transaction

    fun perform(): Single<MarketplaceInvoiceData> {
        return getAccountId()
                .doOnSuccess { accountId ->
                    this.accountId = accountId
                }
                .flatMap {
                    getNetworkParams()
                }
                .doOnSuccess { networkParams ->
                    this.networkParams = networkParams
                }
                .flatMap {
                    getTransaction()
                }
                .doOnSuccess { transaction ->
                    this.transaction = transaction
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

    private fun getNetworkParams(): Single<NetworkParams> {
        return repositoryProvider
                .systemInfo()
                .getNetworkParams()
    }

    private fun getTransaction(): Single<Transaction> {
        val op = CreateAtomicSwapBidRequestOp(
                request = CreateAtomicSwapBidRequest(
                        askID = ask.id.toLong(),
                        quoteAsset = quoteAssetCode,
                        baseAmount = networkParams.amountToPrecised(amount),
                        creatorDetails = "",
                        ext = CreateAtomicSwapBidRequest.CreateAtomicSwapBidRequestExt.EmptyVersion()
                ),
                ext = CreateAtomicSwapBidRequestOp.CreateAtomicSwapBidRequestOpExt.EmptyVersion()
        )

        val account = accountProvider.getAccount()
                ?: return Single.error(IllegalStateException("Cannot obtain current account"))

        return TxManager.createSignedTransaction(networkParams, accountId, account,
                Operation.OperationBody.CreateAtomicSwapBidRequest(op))
    }

    private fun getInvoice(): Single<MarketplaceInvoiceData> {
        return apiProvider.getApi()
                .integrations
                .marketplace
                .submitBidTransaction(transaction)
                .toSingle()
    }

    private fun updateRepositories() {
        listOf(
                repositoryProvider.allAtomicSwapAsks(null),
                repositoryProvider.allAtomicSwapAsks(ask.company.id)
        ).forEach {
            it.updateAskAvailable(ask.id, -amount)
        }
    }
}