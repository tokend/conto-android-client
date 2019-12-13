package org.tokend.template.logic

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.ingester.generated.resources.TransactionResource
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.util.confirmation.ConfirmationProvider
import org.tokend.wallet.*
import org.tokend.wallet.xdr.Operation
import org.tokend.wallet.xdr.TransactionEnvelope

/**
 * Manages transactions sending
 */
class TxManager(
        private val apiProvider: ApiProvider,
        private val confirmationProvider: ConfirmationProvider<Transaction>? = null
) {
    fun submit(transaction: Transaction): Single<TransactionResource> {
        val confirmationCompletable =
                confirmationProvider?.requestConfirmation(transaction)
                        ?: Completable.complete()

        return confirmationCompletable
                .andThen(submitEnvelope(transaction.getEnvelope()))
    }

    fun submitWithoutConfirmation(transactionEnvelope: TransactionEnvelope)
            : Single<TransactionResource> {
        return submitEnvelope(transactionEnvelope)
    }

    private fun submitEnvelope(envelope: TransactionEnvelope): Single<TransactionResource> {
        return apiProvider.getApi()
                .ingester
                .transactions
                .submit(envelope, true)
                .toSingle()
    }

    companion object {
        /**
         * @return transaction with given [operations] for [sourceAccountId] signed by [signer]
         */
        fun createSignedTransaction(networkParams: NetworkParams,
                                    sourceAccountId: String,
                                    signer: Account,
                                    vararg operations: Operation.OperationBody
        ): Single<Transaction> {
            return Single.defer {
                val transaction =
                        TransactionBuilder(networkParams,
                                PublicKeyFactory.fromAccountId(sourceAccountId))
                                .addOperations(operations.toList())
                                .addSigner(signer)
                                .build()

                Single.just(transaction)
            }.subscribeOn(Schedulers.newThread())
        }
    }
}