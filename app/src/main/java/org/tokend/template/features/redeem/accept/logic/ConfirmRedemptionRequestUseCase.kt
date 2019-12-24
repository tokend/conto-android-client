package org.tokend.template.features.redeem.accept.logic

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.ingester.accounts.params.IngesterAccountParams
import org.tokend.sdk.api.ingester.generated.resources.IngesterStateResource
import org.tokend.sdk.api.ingester.generated.resources.TransactionResource
import org.tokend.sdk.utils.extentions.toNetworkParams
import org.tokend.template.data.model.KeyValueEntryRecord
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.redeem.model.RedemptionRequest
import org.tokend.template.logic.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction

class ConfirmRedemptionRequestUseCase(
        private val request: RedemptionRequest,
        private val repositoryProvider: RepositoryProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val apiProvider: ApiProvider,
        private val txManager: TxManager
) {
    class RedemptionAlreadyProcessedException : Exception()

    private lateinit var accountId: String
    private lateinit var systemInfo: IngesterStateResource
    private lateinit var networkParams: NetworkParams
    private lateinit var senderBalanceId: String
    private var securityType: Int = 0
    private lateinit var transaction: Transaction
    private lateinit var submitTransactionResponse: TransactionResource

    fun perform(): Completable {
        return getSystemInfo()
                .doOnSuccess { systemInfo ->
                    this.systemInfo = systemInfo
                    this.networkParams = systemInfo.toNetworkParams()
                }
                .flatMap {
                    getSenderBalanceId()
                }
                .doOnSuccess { senderBalanceId ->
                    this.senderBalanceId = senderBalanceId
                }
                .flatMap {
                    getAccountId()
                }
                .doOnSuccess { accountId ->
                    this.accountId = accountId
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
                .doOnSuccess { submitTransactionResponse ->
                    this.submitTransactionResponse = submitTransactionResponse
                }
                .flatMap {
                    ensureActualSubmit()
                }
                .doOnSuccess {
                    updateRepositories()
                }
                .ignoreElement()
    }

    private fun getSystemInfo(): Single<IngesterStateResource> {
        val systemInfoRepository = repositoryProvider.systemInfo()

        return systemInfoRepository
                .updateDeferred()
                .andThen(Single.defer {
                    Single.just(systemInfoRepository.item!!)
                })
    }

    private fun getSenderBalanceId(): Single<String> {
        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))

        return signedApi.ingester.accounts
                .getById(request.sourceAccountId, IngesterAccountParams(
                        listOf(IngesterAccountParams.Includes.BALANCES)
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

    private fun getAccountId(): Single<String> {
        return walletInfoProvider
                .getWalletInfo()
                ?.accountId
                .toMaybe()
                .switchIfEmpty(Single.error(IllegalStateException("Missing account ID")))
    }

    private fun getSecurityType(): Single<Int> {
        return repositoryProvider
                .keyValueEntries()
                .ensureEntries(listOf(RedemptionRequest.PAYMENT_TYPE.keyValueKey))
                .map { it[RedemptionRequest.PAYMENT_TYPE.keyValueKey] as KeyValueEntryRecord.Number }
                .map { it.value.toInt() }
    }

    private fun getTransaction(): Single<Transaction> {
        return {
            request.toTransaction(senderBalanceId, accountId, networkParams, securityType)
        }.toSingle().subscribeOn(Schedulers.newThread())
    }

    private fun submitTransaction(): Single<TransactionResource> {
        return txManager.submit(transaction)
    }

    private fun ensureActualSubmit(): Single<Boolean> {
        val latestBlockBeforeSubmit = systemInfo.core.latest
                ?: return Single.error(IllegalStateException("Cannot obtain latest core block"))

        val transactionBlock = submitTransactionResponse.ledgerSequence ?: 0

        // The exactly same transaction is always accepted without any errors
        // but if it wasn't the first submit the block number will be lower than the latest one.
        return if (transactionBlock <= latestBlockBeforeSubmit) {
            Single.error(RedemptionAlreadyProcessedException())
        } else {
            Single.just(true)
        }
    }

    private fun updateRepositories() {
        val balance = repositoryProvider.balances()
                .itemsList
                .find { it.assetCode == request.assetCode }
        val balanceId = balance?.id
        val asset = balance?.asset
        val senderNickname = repositoryProvider.accountDetails()
                .getCachedIdentity(request.sourceAccountId)
                ?.email

        val balanceChangesRepositories = mutableListOf(repositoryProvider.balanceChanges(null))
        if (balanceId != null) {
            balanceChangesRepositories.add(repositoryProvider.balanceChanges(balanceId))
        }

        if (asset != null && balanceId != null) {
            balanceChangesRepositories.forEach {
                it.addAcceptedIncomingRedemption(
                        request = request,
                        asset = asset,
                        balanceId = balanceId,
                        accountId = accountId,
                        senderNickname = senderNickname
                )
            }

            repositoryProvider.balances().apply {
                if (!updateBalancesByTransactionResultMeta(submitTransactionResponse.resultMetaXdr!!,
                                networkParams))
                    updateBalanceByDelta(balanceId, request.amount)
            }
        } else {
            balanceChangesRepositories.forEach { it.updateIfEverUpdated() }
            repositoryProvider.balances().updateIfNotFresh()
        }
    }
}
