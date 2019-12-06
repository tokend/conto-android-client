package org.tokend.template.features.assets.buy.marketplace.logic

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import org.tokend.sdk.api.integrations.marketplace.model.MarketplaceInvoiceData
import org.tokend.sdk.api.transactions.model.SubmitTransactionResponse
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.logic.TxManager
import org.tokend.wallet.Account
import org.tokend.wallet.Base32Check
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction
import org.tokend.wallet.xdr.DecoratedSignature
import org.tokend.wallet.xdr.Operation
import org.tokend.wallet.xdr.PublicKey
import org.tokend.wallet.xdr.TransactionEnvelope

/**
 * Adds own signature to the [MarketplaceInvoiceData.Internal.transactionEnvelope]
 * and submits the transaction
 */
class PerformMarketplaceInnerPaymentUseCase(
        private val invoice: MarketplaceInvoiceData.Internal,
        private val assetToBuy: String,
        private val accountProvider: AccountProvider,
        private val repositoryProvider: RepositoryProvider,
        private val txManager: TxManager
) {
    private lateinit var envelope: TransactionEnvelope
    private lateinit var account: Account
    private lateinit var networkParams: NetworkParams
    private lateinit var resultMeta: String

    fun perform(): Completable {
        return getAccount()
                .doOnSuccess { account ->
                    this.account = account
                }
                .flatMap {
                    decodeEnvelope()
                }
                .doOnSuccess { envelope ->
                    this.envelope = envelope
                }
                .flatMap {
                    getNetworkParams()
                }
                .doOnSuccess { networkParams ->
                    this.networkParams = networkParams
                }
                .flatMap {
                    getOwnSignature()
                }
                .doOnSuccess { ownSignature ->
                    envelope.signatures = arrayOf(
                            *envelope.signatures,
                            ownSignature
                    )
                }
                .flatMap {
                    submitTransaction()
                }
                .doOnSuccess { response ->
                    this.resultMeta = response.resultMetaXdr!!
                }
                .doOnSuccess {
                    updateRepositories()
                }
                .ignoreElement()

    }

    private fun decodeEnvelope(): Single<TransactionEnvelope> {
        return {
            TransactionEnvelope.fromBase64(invoice.transactionEnvelope)
        }.toSingle().subscribeOn(Schedulers.computation())
    }

    private fun getAccount(): Single<Account> {
        return accountProvider.getAccount().toMaybe()
                .switchIfEmpty(Single.error(IllegalStateException("No account found")))
    }

    private fun getNetworkParams(): Single<NetworkParams> {
        return repositoryProvider.systemInfo().getNetworkParams()
    }

    private fun getOwnSignature(): Single<DecoratedSignature> {
        return {
            Transaction.getSignature(account, networkParams.networkId, envelope.tx)
        }.toSingle().subscribeOn(Schedulers.computation())
    }

    private fun submitTransaction(): Single<SubmitTransactionResponse> {
        return txManager.submitWithoutConfirmation(envelope)
    }

    private fun updateRepositories() {
        val ownPayment = (envelope.tx.operations.getOrNull(0)?.body
            as? Operation.OperationBody.Payment)?.paymentOp

        val balancesRepository = repositoryProvider.balances()
        val buyingAssetBalance = balancesRepository.itemsList.find {
            it.assetCode == assetToBuy
        }

        val paymentBalanceId = (ownPayment?.sourceBalanceID as? PublicKey.KeyTypeEd25519)
                ?.ed25519
                ?.wrapped
                ?.let(Base32Check::encodeBalanceId)

        balancesRepository.apply {
            if (!updateBalancesByTransactionResultMeta(resultMeta, networkParams)) {
                updateIfEverUpdated()
            }
        }

        if (paymentBalanceId != null) {
            repositoryProvider.balanceChanges(paymentBalanceId).updateIfEverUpdated()
        }

        if (buyingAssetBalance != null) {
            repositoryProvider.balanceChanges(buyingAssetBalance.id).updateIfEverUpdated()
        }
    }
}