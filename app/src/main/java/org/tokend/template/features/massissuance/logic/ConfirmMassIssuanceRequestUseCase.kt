package org.tokend.template.features.massissuance.logic

import io.reactivex.Completable
import io.reactivex.Single
import org.tokend.sdk.api.ingester.generated.resources.TransactionResource
import org.tokend.sdk.utils.extentions.encodeBase64String
import org.tokend.template.data.model.KeyValueEntryRecord
import org.tokend.template.data.model.history.SimpleFeeRecord
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.features.massissuance.model.MassIssuanceRequest
import org.tokend.template.features.send.model.PaymentRecipient
import org.tokend.template.features.send.model.PaymentType
import org.tokend.template.logic.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.PublicKeyFactory
import org.tokend.wallet.Transaction
import org.tokend.wallet.utils.Hashing
import org.tokend.wallet.xdr.MovementDestination
import org.tokend.wallet.xdr.Operation
import org.tokend.wallet.xdr.PaymentFeeData
import org.tokend.wallet.xdr.PaymentOp
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
    private var securityType: Int = 0
    private lateinit var transaction: Transaction

    fun perform(): Completable {
        return getNetworkParams()
                .doOnSuccess { networkParams ->
                    this.networkParams = networkParams
                }
                .flatMap {
                    getSecurityType()
                }
                .doOnSuccess { securityType ->
                    this.securityType = securityType
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

    private fun getSecurityType(): Single<Int> {
        return repositoryProvider
                .keyValueEntries()
                .ensureEntries(listOf(PAYMENT_TYPE.keyValueKey))
                .map { it[PAYMENT_TYPE.keyValueKey] as KeyValueEntryRecord.Number }
                .map { it.value.toInt() }
    }

    private fun getTransaction(): Single<Transaction> {
        val zeroFee = SimpleFeeRecord(BigDecimal.ZERO, BigDecimal.ZERO)
                .toXdrFee(networkParams)

        val operations = request.recipients.mapIndexed { i, recipient ->
            val accountId = recipient.accountId
            val actualSubject = "Mass issuance"

            Operation.OperationBody.Payment(
                    PaymentOp(
                            sourceBalanceID = PublicKeyFactory.fromBalanceId(request.issuerBalanceId),
                            destination = MovementDestination.Account(
                                    accountID = PublicKeyFactory.fromAccountId(accountId)
                            ),
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
                                actualSubject,
                            securityType = securityType,
                            ext = PaymentOp.PaymentOpExt.EmptyVersion()
                    )
            )
        }

        val account = accountProvider.getAccount()
                ?: return Single.error(IllegalStateException("Cannot obtain current account"))

        return TxManager.createSignedTransaction(networkParams, request.issuerAccountId, account,
                *operations.toTypedArray())
    }

    private fun submitTransaction(): Single<TransactionResource> {
        return txManager.submit(transaction)
    }

    private fun updateRepositories() {
        repositoryProvider.balances().updateIfEverUpdated()
        repositoryProvider.balanceChanges(request.issuerBalanceId).updateIfEverUpdated()
    }

    companion object {
        private val PAYMENT_TYPE = PaymentType.USER_TO_USER
    }
}