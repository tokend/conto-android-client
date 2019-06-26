package org.tokend.template.features.redeem.accept.logic

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import io.reactivex.schedulers.Schedulers
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.transactions.model.SubmitTransactionResponse
import org.tokend.sdk.api.v3.accounts.params.AccountParamsV3
import org.tokend.template.data.model.history.SimpleFeeRecord
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.redeem.model.RedemptionRequest
import org.tokend.template.features.send.model.PaymentFee
import org.tokend.template.logic.transactions.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction
import org.tokend.wallet.TransactionBuilder
import org.tokend.wallet.xdr.Operation
import org.tokend.wallet.xdr.PaymentFeeData
import org.tokend.wallet.xdr.op_extensions.SimplePaymentOp
import java.math.BigDecimal

class ConfirmRedemptionRequestUseCase(
        private val request: RedemptionRequest,
        private val repositoryProvider: RepositoryProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val apiProvider: ApiProvider,
        private val txManager: TxManager
) {
    private val balanceId = repositoryProvider
            .balances()
            .itemsList
            .find { it.assetCode == request.assetCode }
            ?.id

    private lateinit var networkParams: NetworkParams
    private lateinit var senderBalanceId: String
    private lateinit var transaction: Transaction

    fun perform(): Completable {
        return getNetworkParams()
                .doOnSuccess { networkParams ->
                    this.networkParams = networkParams
                }
                .flatMap {
                    getSenderBalanceId()
                }
                .doOnSuccess { senderBalanceId ->
                    this.senderBalanceId = senderBalanceId
                }
                .flatMap {
                    getTransaction()
                }
                .doOnSuccess { transaction ->
                    this.transaction = transaction
                }
                .flatMap {
                    submitTransaction()
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

    private fun getSenderBalanceId(): Single<String> {
        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))

        return signedApi.v3.accounts
                .getById(request.sourceAccountId, AccountParamsV3(
                        listOf(AccountParamsV3.Includes.BALANCES)
                ))
                .map { it.balances }
                .toSingle()
                .flatMapMaybe {
                    it.find { balanceResource ->
                        balanceResource.asset.id == request.assetCode
                    }?.id.toMaybe()
                }
                .switchIfEmpty(Single.error(
                        IllegalStateException("No balance ID found for ${request.assetCode}")
                ))
    }

    private fun getTransaction(): Single<Transaction> {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("Missing account ID"))

        val zeroFee = SimpleFeeRecord(BigDecimal.ZERO, BigDecimal.ZERO)
        val fee = PaymentFee(zeroFee, zeroFee)

        return Single.defer {
            val operation = SimplePaymentOp(
                    sourceBalanceId = senderBalanceId,
                    destAccountId = accountId,
                    amount = networkParams.amountToPrecised(request.amount),
                    subject = "",
                    reference = request.salt.toString(),
                    feeData = PaymentFeeData(
                            sourceFee = fee.senderFee.toXdrFee(networkParams),
                            destinationFee = fee.recipientFee.toXdrFee(networkParams),
                            sourcePaysForDest = false,
                            ext = PaymentFeeData.PaymentFeeDataExt.EmptyVersion()
                    )
            )

            val transaction = TransactionBuilder(networkParams, request.sourceAccountId)
                    .addOperation(Operation.OperationBody.Payment(operation))
                    .setSalt(request.salt)
                    .setTimeBounds(request.timeBounds)
                    .build()

            transaction.addSignature(request.signature)

            Single.just(transaction)
        }.subscribeOn(Schedulers.newThread())
    }

    private fun submitTransaction(): Single<SubmitTransactionResponse> {
        return txManager.submit(transaction)
    }

    private fun updateRepositories() {
        repositoryProvider.balances().updateIfEverUpdated()
        if (balanceId != null) {
            repositoryProvider.balanceChanges(balanceId).updateIfEverUpdated()
        }
        repositoryProvider.balanceChanges(null).updateIfEverUpdated()
    }
}
