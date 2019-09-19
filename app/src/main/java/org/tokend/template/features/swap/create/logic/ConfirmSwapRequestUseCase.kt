package org.tokend.template.features.swap.create.logic

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.tokend.template.data.model.history.SimpleFeeRecord
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.swap.create.model.SwapRequest
import org.tokend.template.features.swap.model.SwapDetails
import org.tokend.template.logic.transactions.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.PublicKeyFactory
import org.tokend.wallet.Transaction
import org.tokend.wallet.TransactionBuilder
import org.tokend.wallet.xdr.*

class ConfirmSwapRequestUseCase(
        private val request: SwapRequest,
        apiProvider: ApiProvider,
        private val repositoryProvider: RepositoryProvider,
        private val accountProvider: AccountProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val objectMapper: ObjectMapper
) {
    private val txManager = TxManager(apiProvider.getApi(request.baseBalance.systemIndex).v3.transactions)
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
            val zeroFee = SimpleFeeRecord.ZERO.toXdrFee(networkParams)

            val email = walletInfoProvider.getWalletInfo()?.email
                    ?: return@defer Single.error<Transaction>(
                            IllegalStateException("No wallet info found")
                    )

            val operation = OpenSwapOp(
                    sourceBalance = PublicKeyFactory.fromBalanceId(
                            request.baseBalance.id
                    ),
                    amount = networkParams.amountToPrecised(request.baseAmount),
                    feeData = PaymentFeeData(zeroFee, zeroFee, false,
                            PaymentFeeData.PaymentFeeDataExt.EmptyVersion()),
                    destination = OpenSwapOp.OpenSwapOpDestination.Account(
                            PublicKeyFactory.fromAccountId(request.destAccountId)
                    ),
                    lockTime = networkParams.nowTimestamp + LOCK_TIME_SECONDS,
                    secretHash = Hash(request.hash),
                    details = objectMapper.writeValueAsString(
                            SwapDetails(
                                    request.quoteAmount,
                                    request.quoteAsset.code,
                                    email,
                                    request.destEmail
                            )
                    ),
                    ext = EmptyExt.EmptyVersion()
            )

            val transaction =
                    TransactionBuilder(networkParams, request.sourceAccountId)
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
                .getNetworkParams(request.baseBalance.systemIndex)
    }

    private fun updateRepositories() {
        repositoryProvider.balances().updateBalance(
                balanceId = request.baseBalance.id,
                delta = -request.baseAmount
        )
        repositoryProvider.swaps().updateIfEverUpdated()
    }

    private companion object {
        private const val LOCK_TIME_SECONDS = 24L * 3600
    }
}