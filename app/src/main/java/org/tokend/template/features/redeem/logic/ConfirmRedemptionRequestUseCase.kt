package org.tokend.template.features.redeem.logic

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import io.reactivex.schedulers.Schedulers
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.v3.accounts.params.AccountParamsV3
import org.tokend.template.data.model.AssetRecord
import org.tokend.template.data.model.history.SimpleFeeRecord
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.redeem.model.RedemptionRequest
import org.tokend.template.features.send.model.PaymentFee
import org.tokend.template.features.send.model.PaymentRecipient
import org.tokend.template.features.send.model.PaymentRequest
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction
import org.tokend.wallet.TransactionBuilder
import org.tokend.wallet.xdr.Operation
import org.tokend.wallet.xdr.PaymentFeeData
import org.tokend.wallet.xdr.TransactionEnvelope
import org.tokend.wallet.xdr.op_extensions.SimplePaymentOp
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class ConfirmRedemptionRequestUseCase(
        private val request: RedemptionRequest,
        private val balanceId: String,
        private val repositoryProvider: RepositoryProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val apiProvider: ApiProvider
) {
    private lateinit var networkParams: NetworkParams
    private lateinit var senderBalanceId: String

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
                .flatMap { transactionEnvelope ->
                    apiProvider.getApi()
                            .transactions
                            .submit(transactionEnvelope)
                            .toSingle()
                            .delay(1, TimeUnit.SECONDS)
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

    private fun getTransaction(): Single<TransactionEnvelope> {
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

            val transactionEnvelope = transaction.getEnvelope().apply {
                signatures = arrayOf(request.signature)
            }

            Single.just(transactionEnvelope)
        }.subscribeOn(Schedulers.newThread())
    }

    private fun updateRepositories() {
        repositoryProvider.balances().updateIfEverUpdated()
        repositoryProvider.balanceChanges(balanceId).updateIfEverUpdated()
        repositoryProvider.balanceChanges(null).updateIfEverUpdated()
    }
}
