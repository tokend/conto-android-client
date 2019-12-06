package org.tokend.template.di.providers

import android.content.Context
import android.support.v4.util.LruCache
import com.fasterxml.jackson.databind.ObjectMapper
import org.tokend.template.BuildConfig
import org.tokend.template.data.model.history.converter.DefaultParticipantEffectConverter
import org.tokend.template.data.repository.*
import org.tokend.template.data.repository.assets.AssetChartRepository
import org.tokend.template.data.repository.assets.AssetsRepository
import org.tokend.template.data.repository.balancechanges.BalanceChangesCache
import org.tokend.template.data.repository.balancechanges.BalanceChangesRepository
import org.tokend.template.data.repository.base.MemoryOnlyRepositoryCache
import org.tokend.template.data.repository.pairs.AssetPairsRepository
import org.tokend.template.extensions.getOrPut
import org.tokend.template.features.assets.buy.marketplace.repository.MarketplaceOffersRepository
import org.tokend.template.features.booking.repository.ActiveBookingsRepository
import org.tokend.template.features.booking.repository.BookingBusinessRepository
import org.tokend.template.features.clients.repository.CompanyClientsRepository
import org.tokend.template.features.invest.model.SaleRecord
import org.tokend.template.features.invest.repository.InvestmentInfoRepository
import org.tokend.template.features.invest.repository.SalesRepository
import org.tokend.template.features.kyc.storage.KycStateRepository
import org.tokend.template.features.kyc.storage.SubmittedKycStatePersistor
import org.tokend.template.features.localaccount.repository.LocalAccountRepository
import org.tokend.template.features.localaccount.storage.LocalAccountPersistor
import org.tokend.template.features.offers.repository.OffersCache
import org.tokend.template.features.offers.repository.OffersRepository
import org.tokend.template.features.polls.repository.PollsCache
import org.tokend.template.features.polls.repository.PollsRepository
import org.tokend.template.features.send.recipient.contacts.repository.ContactsRepository
import org.tokend.template.features.trade.orderbook.repository.OrderBookRepository

/**
 * @param context if not specified then android-related repositories
 * will be unavailable
 */
class RepositoryProviderImpl(
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val urlConfigProvider: UrlConfigProvider,
        private val mapper: ObjectMapper,
        private val context: Context? = null,
        private val kycStatePersistor: SubmittedKycStatePersistor? = null,
        private val localAccountPersistor: LocalAccountPersistor? = null
) : RepositoryProvider {
    private val conversionAssetCode =
            if (BuildConfig.ENABLE_BALANCES_CONVERSION)
                BuildConfig.BALANCES_CONVERSION_ASSET
            else
                null

    private val balancesRepository: BalancesRepository by lazy {
        BalancesRepository(
                apiProvider,
                walletInfoProvider,
                urlConfigProvider,
                mapper,
                conversionAssetCode,
                clientCompanies(),
                MemoryOnlyRepositoryCache()
        )
    }
    private val accountDetails: AccountDetailsRepository by lazy {
        AccountDetailsRepository(apiProvider)
    }
    private val systemInfoRepository: SystemInfoRepository by lazy {
        SystemInfoRepository(apiProvider)
    }
    private val tfaFactorsRepository: TfaFactorsRepository by lazy {
        TfaFactorsRepository(apiProvider, walletInfoProvider, MemoryOnlyRepositoryCache())
    }
    private val assetsRepository: AssetsRepository by lazy {
        AssetsRepository(null, apiProvider, urlConfigProvider,
                mapper, MemoryOnlyRepositoryCache())
    }
    private val orderBookRepositories =
            LruCache<String, OrderBookRepository>(MAX_SAME_REPOSITORIES_COUNT)
    private val assetPairsRepositories =
            LruCache<String, AssetPairsRepository>(MAX_SAME_REPOSITORIES_COUNT)
    private val offersRepositories =
            LruCache<String, OffersRepository>(MAX_SAME_REPOSITORIES_COUNT)
    private val accountRepository: AccountRepository by lazy {
        AccountRepository(apiProvider, walletInfoProvider)
    }

    private val salesRepository: SalesRepository by lazy {
        SalesRepository(
                walletInfoProvider,
                apiProvider,
                urlConfigProvider,
                mapper,
                MemoryOnlyRepositoryCache())
    }

    private val filteredSalesRepository: SalesRepository by lazy {
        SalesRepository(
                walletInfoProvider,
                apiProvider,
                urlConfigProvider,
                mapper,
                MemoryOnlyRepositoryCache())
    }

    private val contactsRepository: ContactsRepository by lazy {
        context ?: throw IllegalStateException("This provider has no context " +
                "required to provide contacts repository")
        ContactsRepository(context, MemoryOnlyRepositoryCache())
    }

    private val limitsRepository: LimitsRepository by lazy {
        LimitsRepository(apiProvider, walletInfoProvider)
    }

    private val feesRepository: FeesRepository by lazy {
        FeesRepository(apiProvider, walletInfoProvider)
    }

    private val clientCompaniesRepository: ClientCompaniesRepository by lazy {
        ClientCompaniesRepository(apiProvider, walletInfoProvider, urlConfigProvider,
                MemoryOnlyRepositoryCache())
    }

    private val clientBalanceChangesRepositories =
            LruCache<String, BalanceChangesRepository>(MAX_SAME_REPOSITORIES_COUNT)

    private val pollsRepositoriesByOwnerAccountId =
            LruCache<String, PollsRepository>(MAX_SAME_REPOSITORIES_COUNT)

    private val balanceChangesRepositoriesByBalanceId =
            LruCache<String, BalanceChangesRepository>(MAX_SAME_REPOSITORIES_COUNT)

    private val tradesRepositoriesByAssetPair =
            LruCache<String, TradeHistoryRepository>(MAX_SAME_REPOSITORIES_COUNT)

    private val chartRepositoriesByCode =
            LruCache<String, AssetChartRepository>(MAX_SAME_REPOSITORIES_COUNT)

    private val investmentInfoRepositoriesBySaleId =
            LruCache<Long, InvestmentInfoRepository>(MAX_SAME_REPOSITORIES_COUNT)

    private val atomicSwapRepositoryByAsset =
            LruCache<String, AtomicSwapAsksRepository>(MAX_SAME_REPOSITORIES_COUNT)

    private val companyClientsRepository: CompanyClientsRepository by lazy {
        CompanyClientsRepository(apiProvider, walletInfoProvider,
                assets(), MemoryOnlyRepositoryCache())
    }

    private val marketplaceOffersRepositories =
            LruCache<String, MarketplaceOffersRepository>(MAX_SAME_REPOSITORIES_COUNT)

    private val keyValueEntries: KeyValueEntriesRepository by lazy {
        KeyValueEntriesRepository(apiProvider, MemoryOnlyRepositoryCache())
    }

    private val blobs: BlobsRepository by lazy {
        BlobsRepository(apiProvider)
    }

    private val companiesRepository: CompaniesRepository by lazy {
        CompaniesRepository(apiProvider, urlConfigProvider, MemoryOnlyRepositoryCache())
    }

    private val localAccount: LocalAccountRepository by lazy {
        localAccountPersistor
                ?: throw IllegalStateException("LocalAccountPersistor is required for this repo")
        LocalAccountRepository(localAccountPersistor)
    }

    private val activeBookings: ActiveBookingsRepository by lazy {
        ActiveBookingsRepository(apiProvider, walletInfoProvider, bookingBusiness(),
                MemoryOnlyRepositoryCache())
    }

    private val bookingBusiness: BookingBusinessRepository by lazy {
        BookingBusinessRepository(apiProvider, assetsRepository)
    }

    override fun balances(): BalancesRepository {
        return balancesRepository
    }

    override fun accountDetails(): AccountDetailsRepository {
        return accountDetails
    }

    override fun systemInfo(): SystemInfoRepository {
        return systemInfoRepository
    }

    override fun tfaFactors(): TfaFactorsRepository {
        return tfaFactorsRepository
    }

    override fun assets(): AssetsRepository {
        return assetsRepository
    }

    override fun assetPairs(): AssetPairsRepository {
        val key = conversionAssetCode.toString()
        return assetPairsRepositories.getOrPut(key) {
            AssetPairsRepository(apiProvider, urlConfigProvider, mapper,
                    conversionAssetCode, MemoryOnlyRepositoryCache())
        }
    }

    override fun orderBook(baseAsset: String,
                           quoteAsset: String): OrderBookRepository {
        val key = "$baseAsset.$quoteAsset"
        return orderBookRepositories.getOrPut(key) {
            OrderBookRepository(apiProvider, baseAsset, quoteAsset)
        }
    }

    override fun offers(onlyPrimaryMarket: Boolean,
                        baseAsset: String?,
                        quoteAsset: String?): OffersRepository {
        val key = "$onlyPrimaryMarket-$baseAsset-$quoteAsset"
        return offersRepositories.getOrPut(key) {
            OffersRepository(apiProvider, walletInfoProvider, onlyPrimaryMarket,
                    baseAsset, quoteAsset, OffersCache())
        }
    }

    private val kycStateRepository: KycStateRepository by lazy {
        KycStateRepository(apiProvider, walletInfoProvider, kycStatePersistor, blobs())
    }

    override fun account(): AccountRepository {
        return accountRepository
    }

    override fun sales(): SalesRepository {
        return salesRepository
    }

    override fun filteredSales(): SalesRepository {
        return filteredSalesRepository
    }

    override fun contacts(): ContactsRepository {
        return contactsRepository
    }

    override fun limits(): LimitsRepository {
        return limitsRepository
    }

    override fun fees(): FeesRepository {
        return feesRepository
    }

    override fun balanceChanges(balanceId: String?): BalanceChangesRepository {
        return balanceChangesRepositoriesByBalanceId.getOrPut(balanceId.toString()) {
            BalanceChangesRepository(
                    balanceId,
                    walletInfoProvider.getWalletInfo()?.accountId,
                    null,
                    apiProvider,
                    DefaultParticipantEffectConverter(),
                    accountDetails(),
                    BalanceChangesCache()
            )
        }
    }

    override fun tradeHistory(base: String, quote: String): TradeHistoryRepository {
        return tradesRepositoriesByAssetPair.getOrPut("$base:$quote") {
            TradeHistoryRepository(
                    base,
                    quote,
                    apiProvider,
                    MemoryOnlyRepositoryCache()
            )
        }
    }

    override fun assetChart(asset: String): AssetChartRepository {
        return chartRepositoriesByCode.getOrPut(asset) {
            AssetChartRepository(
                    asset,
                    apiProvider
            )
        }
    }

    override fun assetChart(baseAsset: String, quoteAsset: String): AssetChartRepository {
        return chartRepositoriesByCode.getOrPut("$baseAsset-$quoteAsset") {
            AssetChartRepository(
                    baseAsset,
                    quoteAsset,
                    apiProvider
            )
        }
    }

    override fun kycState(): KycStateRepository {
        return kycStateRepository
    }

    override fun investmentInfo(sale: SaleRecord): InvestmentInfoRepository {
        return investmentInfoRepositoriesBySaleId.getOrPut(sale.id) {
            InvestmentInfoRepository(sale, offers(), sales())
        }
    }

    override fun polls(ownerAccountId: String): PollsRepository {
        return pollsRepositoriesByOwnerAccountId.getOrPut(ownerAccountId) {
            PollsRepository(ownerAccountId, apiProvider, walletInfoProvider,
                    keyValueEntries(), PollsCache())
        }
    }

    override fun atomicSwapAsks(asset: String): AtomicSwapAsksRepository {
        return atomicSwapRepositoryByAsset.getOrPut(asset) {
            AtomicSwapAsksRepository(
                    apiProvider,
                    asset,
                    assets(),
                    urlConfigProvider,
                    mapper,
                    MemoryOnlyRepositoryCache()
            )
        }
    }

    override fun clientCompanies(): ClientCompaniesRepository {
        return clientCompaniesRepository
    }

    override fun companies(): CompaniesRepository {
        return companiesRepository
    }

    override fun companyClients(): CompanyClientsRepository {
        return companyClientsRepository
    }

    override fun companyClientBalanceChanges(clientAccountId: String, assetCode: String): BalanceChangesRepository {
        val key = "${clientAccountId}_$assetCode"
        return clientBalanceChangesRepositories.getOrPut(key) {
            BalanceChangesRepository(
                    null,
                    clientAccountId,
                    assetCode,
                    apiProvider,
                    DefaultParticipantEffectConverter(),
                    accountDetails(),
                    BalanceChangesCache()
            )
        }
    }

    override fun keyValueEntries(): KeyValueEntriesRepository {
        return keyValueEntries
    }

    override fun blobs(): BlobsRepository {
        return blobs
    }

    override fun marketplaceOffers(ownerAccountId: String?): MarketplaceOffersRepository {
        return marketplaceOffersRepositories.getOrPut(ownerAccountId.toString()) {
            MarketplaceOffersRepository(
                    ownerAccountId,
                    apiProvider,
                    assets(),
                    companies(),
                    MemoryOnlyRepositoryCache()
            )
        }
    }

    override fun localAccount(): LocalAccountRepository {
        return localAccount
    }

    override fun activeBookings(): ActiveBookingsRepository {
        return activeBookings
    }

    override fun bookingBusiness(): BookingBusinessRepository {
        return bookingBusiness
    }

    companion object {
        private const val MAX_SAME_REPOSITORIES_COUNT = 10
    }
}