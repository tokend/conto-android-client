package org.tokend.template.features.polls.logic

import io.reactivex.Completable
import io.reactivex.Single
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.logic.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction

/**
 * Submits user's vote in the poll.
 *
 * Updates polls repository on success.
 *
 * @param choiceIndex index of choice, starts from 0
 */
class AddVoteUseCase(
        private val pollId: String,
        private val pollOwnerAccountId: String,
        private val choiceIndex: Int,
        private val accountProvider: AccountProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val repositoryProvider: RepositoryProvider,
        private val txManager: TxManager
) {
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
                    updateRepository()
                }
                .ignoreElement()
    }

    private fun getNetworkParams(): Single<NetworkParams> {
        return repositoryProvider
                .systemInfo()
                .getNetworkParams()
    }

    private fun getTransaction(): Single<Transaction> {
        return Single.error(NotImplementedError("Polls are not yet supported  "))
    }

    private fun updateRepository() {
        repositoryProvider
                .polls(pollOwnerAccountId)
                .updatePollChoiceLocally(pollId, choiceIndex)
    }
}