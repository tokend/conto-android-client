package org.tokend.template.features.nfcpayment.logic

import io.reactivex.Completable
import io.reactivex.Single
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.nfcpayment.model.FulfilledPosPaymentRequest
import org.tokend.template.logic.TxManager
import org.tokend.wallet.PublicKeyFactory
import org.tokend.wallet.Transaction
import org.tokend.wallet.xdr.Fee
import org.tokend.wallet.xdr.Operation
import org.tokend.wallet.xdr.PaymentFeeData
import org.tokend.wallet.xdr.PaymentOp
import java.util.concurrent.TimeUnit

class CreateAndBroadcastPosPaymentTxUseCase(
        private val paymentRequest: FulfilledPosPaymentRequest,
        private val walletInfoProvider: WalletInfoProvider,
        private val accountProvider: AccountProvider,
        private val transactionBroadcaster: TransactionBroadcaster,
        private val repositoryProvider: RepositoryProvider?
) {
    private val networkParams = paymentRequest.networkParams

    fun perform(): Completable {
        return getTransaction()
                .flatMapCompletable { transaction ->
                    transactionBroadcaster.broadcastTransaction(transaction)
                }
                .doOnComplete {
                    updateRepositories()
                }
                .delay(VISUAL_DELAY, TimeUnit.MILLISECONDS)
    }

    private fun getTransaction(): Single<Transaction> {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))
        val account = accountProvider.getAccount()
                ?: return Single.error(IllegalStateException("No account found"))

        val zeroFee = Fee(0L, 0L, Fee.FeeExt.EmptyVersion())

        return TxManager.createSignedTransaction(networkParams, accountId, account,
                Operation.OperationBody.Payment(PaymentOp(
                        sourceBalanceID = PublicKeyFactory.fromBalanceId(
                                paymentRequest.sourceBalanceId),
                        amount = networkParams.amountToPrecised(paymentRequest.amount),
                        reference = paymentRequest.referenceString,
                        feeData = PaymentFeeData(zeroFee, zeroFee, false,
                                PaymentFeeData.PaymentFeeDataExt.EmptyVersion()),
                        destination = PaymentOp.PaymentOpDestination.Balance(
                                PublicKeyFactory.fromBalanceId(paymentRequest.destinationBalanceId)
                        ),
                        subject = "",
                        ext = PaymentOp.PaymentOpExt.EmptyVersion()
                )))
    }

    private fun updateRepositories() {
        repositoryProvider?.balances()?.apply {
            updateBalanceByDelta(
                    balanceId = paymentRequest.sourceBalanceId,
                    delta = paymentRequest.amount.negate()
            )
        }
    }

    private companion object {
        const val VISUAL_DELAY = 500L
    }
}