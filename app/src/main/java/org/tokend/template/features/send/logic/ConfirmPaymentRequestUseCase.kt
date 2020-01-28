package org.tokend.template.features.send.logic

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.features.send.model.PaymentRequest
import org.tokend.template.logic.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction

/**
 * Sends payment identified by given payment request.
 * Updates related repositories: balances, transactions
 */
class ConfirmPaymentRequestUseCase(
        private val request: PaymentRequest,
        private val accountProvider: AccountProvider,
        private val repositoryProvider: RepositoryProvider,
        private val txManager: TxManager
) {
    private lateinit var networkParams: NetworkParams
    private lateinit var resultMetaXdr: String

    fun perform(): Completable {
        return getNetworkParams()
                .doOnSuccess { networkParams ->
                    this.networkParams = networkParams
                }
                .flatMap {
                    getTransaction()
                }
                .flatMap { transaction ->
                    txManager.submit(transaction, waitForIngest = false)
                }
                .doOnSuccess { response ->
                    this.resultMetaXdr = response.resultMetaXdr!!
                }
                .doOnSuccess {
                    updateRepositories()
                }
                .ignoreElement()
    }

    private fun getTransaction(): Single<Transaction> {
        return Single.defer {
            val account = accountProvider.getAccount()
                    ?: return@defer Single.error<Transaction>(
                            IllegalStateException("Cannot obtain current account")
                    )

            request.toTransaction(networkParams, account).toSingle()
        }.subscribeOn(Schedulers.newThread())
    }

    private fun getNetworkParams(): Single<NetworkParams> {
        return repositoryProvider
                .systemInfo()
                .getNetworkParams()
    }

    private fun updateRepositories() {
        repositoryProvider.balances().apply {
            if (!updateBalancesByTransactionResultMeta(resultMetaXdr, networkParams))
                updateIfEverUpdated()
        }
        repositoryProvider.balanceChanges(request.senderBalanceId).addPayment(request)
        repositoryProvider.balanceChanges(null).addPayment(request)
    }
}