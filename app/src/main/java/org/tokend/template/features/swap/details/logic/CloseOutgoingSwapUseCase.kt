package org.tokend.template.features.swap.details.logic

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.UrlConfigProvider
import org.tokend.template.features.swap.model.SwapRecord
import org.tokend.template.logic.transactions.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction
import org.tokend.wallet.TransactionBuilder
import org.tokend.wallet.xdr.CloseSwapOp
import org.tokend.wallet.xdr.EmptyExt
import org.tokend.wallet.xdr.Hash
import org.tokend.wallet.xdr.Operation

class CloseOutgoingSwapUseCase(
        private val outgoingSwap: SwapRecord,
        apiProvider: ApiProvider,
        urlConfigProvider: UrlConfigProvider,
        private val repositoryProvider: RepositoryProvider,
        private val accountProvider: AccountProvider
) {
    private val destSystemIndex = (0 until urlConfigProvider.getConfigsCount())
            .first { it != outgoingSwap.sourceSystemIndex }
    private val txManager = TxManager(
            apiProvider.getApi(destSystemIndex).v3.transactions, null)
    private lateinit var networkParams: NetworkParams

    fun perform(): Completable {
        return getNetworkParams()
                .doOnSuccess { networkParams ->
                    this.networkParams = networkParams
                }
                .flatMap {
                    getTransaction()
                }
                .flatMap { transaction ->
                    txManager.submit(transaction)
                }
                .doOnSuccess {
                    updateRepositories()
                }
                .ignoreElement()
    }

    private fun getTransaction(): Single<Transaction> {
        return Single.defer {
            val secret = outgoingSwap.secret
                    ?: return@defer Single.error(IllegalStateException("Outgoing swap has no secret"))
            val operation = CloseSwapOp(
                    swapID = outgoingSwap.id.toLong(),
                    secret = Hash(secret),
                    ext = EmptyExt.EmptyVersion()
            )

            val transaction =
                    TransactionBuilder(networkParams, outgoingSwap.sourceAccountId)
                            .addOperation(Operation.OperationBody.CloseSwap(operation))
                            .build()

            val account = accountProvider.getAccount()
                    ?: return@defer Single.error<Transaction>(
                            IllegalStateException("Cannot obtain current account")
                    )

            transaction.addSignature(account)

            Single.just(transaction)
        }.subscribeOn(Schedulers.newThread())
    }

    private fun getNetworkParams(): Single<NetworkParams> {
        return repositoryProvider
                .systemInfo()
                .getNetworkParams(destSystemIndex)
    }

    private fun updateRepositories() {
        repositoryProvider.balances().updateAssetBalance(
                assetCode = outgoingSwap.quoteAsset.code,
                delta = outgoingSwap.quoteAmount
        )
        repositoryProvider.swaps().updateIfEverUpdated()
    }
}