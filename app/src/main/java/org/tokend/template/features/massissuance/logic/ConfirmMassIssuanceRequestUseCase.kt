package org.tokend.template.features.massissuance.logic

import io.reactivex.Completable
import io.reactivex.Single
import org.tokend.sdk.api.transactions.model.SubmitTransactionResponse
import org.tokend.sdk.utils.extentions.encodeBase64String
import org.tokend.template.data.model.history.SimpleFeeRecord
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.features.massissuance.model.MassIssuanceRequest
import org.tokend.template.features.send.model.PaymentRecipient
import org.tokend.template.logic.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction
import org.tokend.wallet.utils.Hashing
import org.tokend.wallet.xdr.Operation
import org.tokend.wallet.xdr.PaymentFeeData
import org.tokend.wallet.xdr.op_extensions.SimplePaymentOp
import java.math.BigDecimal

/**
 * Confirms given [request].
 *
 * Updates balances and history on success
 */
class ConfirmMassIssuanceRequestUseCase(
        private val request: MassIssuanceRequest,
        private val accountProvider: AccountProvider,
        private val txManager: TxManager,
        private val repositoryProvider: RepositoryProvider
) {
    private lateinit var networkParams: NetworkParams
    private lateinit var transaction: Transaction

    fun perform(): Completable {
        return getNetworkParams()
                .doOnSuccess { networkParams ->
                    this.networkParams = networkParams
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

    private fun getTransaction(): Single<Transaction> {
        val zeroFee = SimpleFeeRecord(BigDecimal.ZERO, BigDecimal.ZERO)
                .toXdrFee(networkParams)

        val operations = request.recipients.mapIndexed { i, recipient ->
            val accountId = recipient.accountId
            val actualSubject = "Mass issuance"

            Operation.OperationBody.Payment(
                    SimplePaymentOp(
                            sourceBalanceId = request.issuerBalanceId,
                            destAccountId = accountId,
                            amount = networkParams.amountToPrecised(request.amount),
                            feeData = PaymentFeeData(
                                    zeroFee, zeroFee, false,
                                    PaymentFeeData.PaymentFeeDataExt.EmptyVersion()
                            ),
                            reference = Hashing.sha256((request.referenceSeed + i)
                                    .toByteArray())
                                    .encodeBase64String(),
                            subject = if (recipient is PaymentRecipient.NotExisting)
                                recipient.wrapPaymentSubject(request.issuerAccountId, actualSubject)
                            else
                                actualSubject
                    )
            )
        }

        val account = accountProvider.getAccount()
                ?: return Single.error(IllegalStateException("Cannot obtain current account"))

        return TxManager.createSignedTransaction(networkParams, request.issuerAccountId, account,
                *operations.toTypedArray())
    }

    private fun submitTransaction(): Single<SubmitTransactionResponse> {
        return txManager.submit(transaction)
    }

    private fun updateRepositories() {
        repositoryProvider.balances().updateIfEverUpdated()
        repositoryProvider.balanceChanges(request.issuerBalanceId).updateIfEverUpdated()
    }
}