package org.tokend.template.features.swap.details.logic

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.identity.params.IdentitiesPageParams
import org.tokend.template.data.model.history.SimpleFeeRecord
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.features.swap.model.SwapDetails
import org.tokend.template.features.swap.model.SwapRecord
import org.tokend.template.logic.transactions.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.PublicKeyFactory
import org.tokend.wallet.Transaction
import org.tokend.wallet.TransactionBuilder
import org.tokend.wallet.xdr.*
import java.util.concurrent.TimeUnit

class ConfirmIncomingSwapUseCase(
        private val incomingSwap: SwapRecord,
        private val apiProvider: ApiProvider,
        private val repositoryProvider: RepositoryProvider,
        private val accountProvider: AccountProvider,
        private val objectMapper: ObjectMapper
) {
    private val quoteBalance = repositoryProvider.balances()
            .itemsList
            .first { it.assetCode == incomingSwap.quoteAsset.code }
    private val txManager = TxManager(
            apiProvider.getApi(quoteBalance.systemIndex).v3.transactions, null)
    private lateinit var networkParams: NetworkParams

    fun perform(): Completable {
        return getNetworkParams()
                .doOnSuccess { networkParams ->
                    this.networkParams = networkParams
                }
                .flatMap {
                    ensureCounterpartyAccount()
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

    private fun ensureCounterpartyAccount(): Single<Boolean> {
        return apiProvider.getApi(quoteBalance.systemIndex)
                .identities
                .get(IdentitiesPageParams(incomingSwap.sourceEmail))
                .toSingle()
                .delay(5, TimeUnit.SECONDS)
                .map { true }
    }

    private fun getTransaction(): Single<Transaction> {
        return Single.defer {
            val zeroFee = SimpleFeeRecord.ZERO.toXdrFee(networkParams)

            val operation = OpenSwapOp(
                    sourceBalance = PublicKeyFactory.fromBalanceId(
                            quoteBalance.id
                    ),
                    amount = networkParams.amountToPrecised(incomingSwap.quoteAmount),
                    feeData = PaymentFeeData(zeroFee, zeroFee, false,
                            PaymentFeeData.PaymentFeeDataExt.EmptyVersion()),
                    destination = OpenSwapOp.OpenSwapOpDestination.Account(
                            PublicKeyFactory.fromAccountId(incomingSwap.sourceAccountId)
                    ),
                    lockTime = networkParams.nowTimestamp + LOCK_TIME_SECONDS,
                    secretHash = Hash(incomingSwap.hashBytes),
                    details = objectMapper.writeValueAsString(
                            SwapDetails(
                                    incomingSwap.baseAmount,
                                    incomingSwap.baseAsset.code,
                                    "", ""
                            )
                    ),
                    ext = EmptyExt.EmptyVersion()
            )

            val transaction =
                    TransactionBuilder(networkParams, incomingSwap.destAccountId)
                            .addOperation(Operation.OperationBody.OpenSwap(operation))
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
                .getNetworkParams(quoteBalance.systemIndex)
    }

    private fun updateRepositories() {
        repositoryProvider.balances().updateBalance(
                balanceId = quoteBalance.id,
                delta = -incomingSwap.quoteAmount
        )
        repositoryProvider.swaps().updateIfEverUpdated()
    }

    private companion object {
        private const val LOCK_TIME_SECONDS = 12L * 3600
    }
}