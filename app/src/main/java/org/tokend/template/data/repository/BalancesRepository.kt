package org.tokend.template.data.repository

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.v3.balances.params.ConvertedBalancesParams
import org.tokend.sdk.utils.extentions.isNotFound
import org.tokend.sdk.utils.extentions.isUnauthorized
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.BalanceRecord
import org.tokend.template.data.model.SimpleAsset
import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.data.repository.base.SimpleMultipleItemsRepository
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.UrlConfigProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.extensions.mapSuccessful
import org.tokend.template.logic.transactions.TxManager
import org.tokend.wallet.*
import org.tokend.wallet.xdr.Operation
import org.tokend.wallet.xdr.op_extensions.CreateBalanceOp
import retrofit2.HttpException
import java.math.BigDecimal

class BalancesRepository(
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val urlConfigProvider: UrlConfigProvider,
        private val mapper: ObjectMapper,
        private val conversionAssetCode: String?,
        private val companiesRepository: ClientCompaniesRepository,
        itemsCache: RepositoryCache<BalanceRecord>
) : SimpleMultipleItemsRepository<BalanceRecord>(itemsCache) {

    var conversionAsset: Asset? = null

    override fun getItems(): Single<List<BalanceRecord>> {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))

        return if (conversionAssetCode != null)
            getConvertedBalances(
                    accountId,
                    urlConfigProvider,
                    mapper,
                    conversionAssetCode
            )
                    .onErrorResumeNext {
                        // It's back!
                        if (it is HttpException && it.isNotFound()) {
                            Log.e("BalancesRepo",
                                    "This env is unable to convert balances into $conversionAssetCode")
                            getBalances(
                                    accountId,
                                    urlConfigProvider,
                                    mapper
                            )
                        } else
                            Single.error(it)
                    }
        else
            getBalances(
                    accountId,
                    urlConfigProvider,
                    mapper
            )
    }

    private fun getConvertedBalances(accountId: String,
                                     urlConfigProvider: UrlConfigProvider,
                                     mapper: ObjectMapper,
                                     conversionAssetCode: String): Single<List<BalanceRecord>> {
        val systemsCount = urlConfigProvider.getConfigsCount()
        return Single.merge(
                (0 until systemsCount)
                        .map { index ->
                            getSystemConvertedBalances(
                                    index, accountId,
                                    urlConfigProvider,
                                    mapper,
                                    conversionAssetCode
                            )
                        }
        )
                .collect(
                        { mutableListOf<List<BalanceRecord>>() },
                        { a, b -> a.add(b) }
                )
                .map { it.flatten() }
    }

    private fun getSystemConvertedBalances(index: Int,
                                           accountId: String,
                                           urlConfigProvider: UrlConfigProvider,
                                           mapper: ObjectMapper,
                                           conversionAssetCode: String): Single<List<BalanceRecord>> {
        val signedBalancesApi = apiProvider.getSignedApi(index)?.v3?.balances
                ?: return Single.error(IllegalStateException("No signed API found for system $index"))
        val urlConfig = urlConfigProvider.getConfig(index)

        return signedBalancesApi
                .getConvertedBalances(
                        accountId = accountId,
                        assetCode = conversionAssetCode,
                        params = ConvertedBalancesParams(
                                include = listOf(
                                        ConvertedBalancesParams.Includes.BALANCE_ASSET,
                                        ConvertedBalancesParams.Includes.STATES,
                                        ConvertedBalancesParams.Includes.ASSET
                                )
                        )
                )
                .toSingle()
                .flatMap { convertedBalances ->
                    companiesRepository
                            .ensureCompanies(
                                    convertedBalances.states.map { it.balance.asset.owner.id }
                            )
                            .map { convertedBalances to it }
                }
                .map { (convertedBalances, companiesMap) ->
                    conversionAsset = SimpleAsset(convertedBalances.asset)
                    convertedBalances
                            .states
                            .mapSuccessful {
                                BalanceRecord(it, urlConfig, mapper,
                                        conversionAsset, companiesMap, index)
                            }
                }
    }

    private fun getBalances(accountId: String,
                            urlConfigProvider: UrlConfigProvider,
                            mapper: ObjectMapper): Single<List<BalanceRecord>> {
        val systemsCount = urlConfigProvider.getConfigsCount()
        return Single.merge(
                (0 until systemsCount)
                        .map { index ->
                            getSystemBalances(index, accountId, urlConfigProvider, mapper)
                        }
        ).collect(
                { mutableListOf<List<BalanceRecord>>() },
                { a, b -> a.add(b) }
        )
                .map { it.flatten() }
    }

    private fun getSystemBalances(index: Int,
                                  accountId: String,
                                  urlConfigProvider: UrlConfigProvider,
                                  mapper: ObjectMapper): Single<List<BalanceRecord>> {
        val signedAccountsApi = apiProvider.getSignedApi(index)?.v3?.accounts
                ?: return Single.error(IllegalStateException("No signed API found for system $index"))
        val urlConfig = urlConfigProvider.getConfig(index)

        return signedAccountsApi
                .getBalances(accountId)
                .toSingle()
                .flatMap { sourceList ->
                    companiesRepository
                            .ensureCompanies(sourceList.map { it.asset.owner.id })
                            .map { sourceList to it }
                }
                .map { (sourceList, companiesMap) ->
                    sourceList
                            .mapSuccessful {
                                BalanceRecord(it, urlConfig, mapper, companiesMap, index)
                            }
                }
                .onErrorResumeNext { error ->
                    if (error is HttpException && (error.isNotFound() || error.isUnauthorized()))
                        Single.just(emptyList())
                    else
                        Single.error(error)
                }
    }

    /**
     * Creates balance for given assets,
     * updates repository on complete
     */
    fun create(accountProvider: AccountProvider,
               systemInfoRepository: SystemInfoRepository,
               txManager: TxManager,
               vararg assets: String): Completable {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Completable.error(IllegalStateException("No wallet info found"))
        val account = accountProvider.getAccount()
                ?: return Completable.error(IllegalStateException("Cannot obtain current account"))

        return systemInfoRepository.getNetworkParams()
                .flatMap { netParams ->
                    createBalanceCreationTransaction(netParams, accountId, account, assets)
                }
                .flatMap { transition ->
                    txManager.submit(transition)
                }
                .flatMapCompletable {
                    invalidate()
                    updateDeferred()
                }
                .doOnSubscribe {
                    isLoading = true
                }
                .doOnTerminate {
                    isLoading = false
                }
    }

    private fun createBalanceCreationTransaction(networkParams: NetworkParams,
                                                 sourceAccountId: String,
                                                 signer: Account,
                                                 assets: Array<out String>): Single<Transaction> {
        return Single.defer {
            val operations = assets.map {
                CreateBalanceOp(sourceAccountId, it)
            }

            val transaction =
                    TransactionBuilder(networkParams, PublicKeyFactory.fromAccountId(sourceAccountId))
                            .addOperations(operations.map(Operation.OperationBody::ManageBalance))
                            .build()

            transaction.addSignature(signer)

            Single.just(transaction)
        }.subscribeOn(Schedulers.computation())
    }

    fun updateAssetBalance(assetCode: String,
                           delta: BigDecimal) {
        itemsList
                .find { it.assetCode == assetCode }
                ?.also { balance ->
                    balance.available += delta
                    itemsCache.update(balance)
                    broadcast()
                }
    }

    fun updateBalance(balanceId: String,
                      delta: BigDecimal) {
        itemsList
                .find { it.id == balanceId }
                ?.also { balance ->
                    balance.available += delta
                    itemsCache.update(balance)
                    broadcast()
                }
    }
}