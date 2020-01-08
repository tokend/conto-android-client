package org.tokend.template.features.nfcpayment.logic

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.nfcpayment.model.PosPaymentRequest
import org.tokend.template.logic.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.PublicKeyFactory
import org.tokend.wallet.Transaction
import org.tokend.wallet.xdr.Fee
import org.tokend.wallet.xdr.Operation
import org.tokend.wallet.xdr.PaymentFeeData
import org.tokend.wallet.xdr.PaymentOp

class CreateAndBroadcastPosPaymentUseCase(
        private val paymentRequest: PosPaymentRequest,
        private val repositoryProvider: RepositoryProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val accountProvider: AccountProvider,
        private val transactionBroadcaster: TransactionBroadcaster
) {
    private val asset = paymentRequest.asset

    private lateinit var networkParams: NetworkParams
    private lateinit var senderBalanceId: String

    fun perform(): Completable {
        return getNetworkParams()
                .doOnSuccess { networkParams ->
                    this.networkParams = networkParams
                }
                .flatMap {
                    getSenderBalance()
                }
                .doOnSuccess { senderBalanceId ->
                    this.senderBalanceId = senderBalanceId
                }
                .flatMap {
                    getTransaction()
                }
                .flatMapCompletable { transaction ->
                    transactionBroadcaster.broadcastTransaction(transaction)
                }
    }

    private fun getNetworkParams(): Single<NetworkParams> {
        return repositoryProvider.systemInfo()
                .getNetworkParams()
    }

    private fun getSenderBalance(): Single<String> {
        val balancesRepository = repositoryProvider.balances()

        return balancesRepository
                .updateIfNotFreshDeferred()
                .toSingleDefault(true)
                .flatMapMaybe {
                    balancesRepository
                            .itemsList
                            .find { it.assetCode == asset.code }
                            ?.id
                            .toMaybe()
                }
                .switchIfEmpty(Single.error(
                        IllegalStateException("No balance ID found for $asset")
                ))
    }

    private fun getTransaction(): Single<Transaction> {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))
        val account = accountProvider.getAccount()
                ?: return Single.error(IllegalStateException("No account found"))

        val zeroFee = Fee(0L, 0L, Fee.FeeExt.EmptyVersion())

        return TxManager.createSignedTransaction(networkParams, accountId, account,
                Operation.OperationBody.Payment(PaymentOp(
                        sourceBalanceID = PublicKeyFactory.fromBalanceId(senderBalanceId),
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
}