package org.tokend.template.features.assets.buy.logic

import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import org.tokend.sdk.factory.JsonApiToolsProvider
import org.tokend.sdk.utils.extentions.encodeBase64String
import org.tokend.template.data.model.AtomicSwapAskRecord
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.assets.buy.model.AtomicSwapInvoice
import org.tokend.template.logic.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction
import java.math.BigDecimal
import java.security.SecureRandom

/**
 * Submits [CreateAtomicSwapBidRequestOp] and waits
 * for invoice to appear in related reviewable request details.
 *
 * Updates asks repository on success
 */
class CreateAtomicSwapBidUseCase(
        private val amount: BigDecimal,
        private val quoteAssetCode: String,
        private val ask: AtomicSwapAskRecord,
        private val apiProvider: ApiProvider,
        private val repositoryProvider: RepositoryProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val accountProvider: AccountProvider,
        private val txManager: TxManager
) {
    private val objectMapper = JsonApiToolsProvider.getObjectMapper()
    private lateinit var accountId: String
    private lateinit var networkParams: NetworkParams
    private lateinit var reference: String
    private lateinit var transaction: Transaction
    private lateinit var pendingRequestId: String

    fun perform(): Single<AtomicSwapInvoice> {
        return getAccountId()
                .doOnSuccess { accountId ->
                    this.accountId = accountId
                }
                .flatMap {
                    getNetworkParams()
                }
                .doOnSuccess { networkParams ->
                    this.networkParams = networkParams
                }
                .flatMap {
                    getReference()
                }
                .doOnSuccess { reference ->
                    this.reference = reference
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
                .flatMap {
                    getPendingRequestId()
                }
                .doOnSuccess { pendingRequestId ->
                    this.pendingRequestId = pendingRequestId
                }
                .flatMap {
                    getInvoiceFromRequest()
                }
                .doOnSuccess {
                    updateRepositories()
                }
    }

    private fun getAccountId(): Single<String> {
        return walletInfoProvider
                .getWalletInfo()
                ?.accountId
                .toMaybe()
                .switchIfEmpty(Single.error(IllegalStateException("Missing account ID")))
    }

    private fun getNetworkParams(): Single<NetworkParams> {
        return repositoryProvider
                .systemInfo()
                .getNetworkParams()
    }

    private fun getReference(): Single<String> {
        return {
            SecureRandom.getSeed(12).encodeBase64String()
        }.toSingle().subscribeOn(Schedulers.newThread())
    }

    private fun getTransaction(): Single<Transaction> {
        return Single.error(NotImplementedError("Atomic swaps are not supported"))
    }

    private fun submitTransaction(): Single<Boolean> {
        return txManager
                .submit(transaction)
                .map { true }
    }

    private fun getPendingRequestId(): Single<String> {
        return Single.error(NotImplementedError("Atomic swaps are not supported"))
    }

    private fun getInvoiceFromRequest(): Single<AtomicSwapInvoice> {
        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))

        class NoInvoiceYetException : Exception()
        class RequestRejectedException : Exception()
        class InvoiceParsingException(cause: Exception) : Exception(cause)


        return Single.error(NotImplementedError("Atomic swaps are not supported"))
    }

    private fun updateRepositories() {
        repositoryProvider.atomicSwapAsks(ask.asset.code).updateIfEverUpdated()
    }

    companion object {
        private const val REFERENCE_KEY = "reference"
        private const val REQUEST_DATA_ARRAY_KEY = "data"
        private const val INVOICE_KEY = "invoice"
        private const val POLL_INTERVAL_MS = 2000L
        private const val LOG_TAG = "BidCreation"
    }
}