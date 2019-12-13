package org.tokend.template.features.withdraw.logic

import io.reactivex.Completable
import io.reactivex.Single
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.features.withdraw.model.WithdrawalRequest
import org.tokend.template.logic.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction

/**
 * Sends withdrawal request.
 *
 * Updates related repositories: balances, transactions
 */
class ConfirmWithdrawalRequestUseCase(
        private val request: WithdrawalRequest,
        private val accountProvider: AccountProvider,
        private val repositoryProvider: RepositoryProvider,
        private val txManager: TxManager
) {
    private lateinit var networkParams: NetworkParams
    private lateinit var resultMeta: String

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
                .doOnSuccess { result ->
                    this.resultMeta = result.resultMetaXdr!!
                }
                .doOnSuccess {
                    updateRepositories()
                }
                .ignoreElement()
    }

    private fun getNetworkParams(): Single<NetworkParams> {
        return repositoryProvider
                .systemInfo()
                .getNetworkParams()
    }

    private fun getTransaction(): Single<Transaction> {
        return Single.error(NotImplementedError("Withdrawals are not yet supported"))
    }

    private fun updateRepositories() {
        repositoryProvider.balances().apply {
            if (!updateBalancesByTransactionResultMeta(resultMeta, networkParams))
                updateIfEverUpdated()
        }
        repositoryProvider.balanceChanges(request.balanceId).updateIfEverUpdated()
        repositoryProvider.balanceChanges(null).updateIfEverUpdated()
    }
}