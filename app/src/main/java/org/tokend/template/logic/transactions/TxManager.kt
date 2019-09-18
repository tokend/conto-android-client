package org.tokend.template.logic.transactions

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.transactions.model.SubmitTransactionResponse
import org.tokend.sdk.api.v3.transactions.TransactionsApiV3
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.util.confirmation.ConfirmationProvider
import org.tokend.wallet.*
import org.tokend.wallet.xdr.Operation

/**
 * Manages transactions sending
 */
class TxManager(
        private val transactionsApi: TransactionsApiV3,
        private val confirmationProvider: ConfirmationProvider<Transaction>? = null
) {
    constructor(apiProvider: ApiProvider,
                confirmationProvider: ConfirmationProvider<Transaction>? = null): this(
            transactionsApi = apiProvider.getApi().v3.transactions,
            confirmationProvider = confirmationProvider
    )

    fun submit(transaction: Transaction): Single<SubmitTransactionResponse> {
        val confirmationCompletable =
                confirmationProvider?.requestConfirmation(transaction)
                        ?: Completable.complete()

        return confirmationCompletable
                .andThen(
                        transactionsApi
                                .submit(transaction, true)
                                .toSingle()
                )
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