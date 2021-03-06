package org.tokend.template.di.providers

import org.tokend.template.data.repository.AccountDetailsRepository
import org.tokend.template.data.repository.AccountRepository
import org.tokend.template.data.repository.AtomicSwapAsksRepository
import org.tokend.template.data.repository.BlobsRepository
import org.tokend.template.features.assets.buy.marketplace.repository.MarketplaceOffersRepository
import org.tokend.template.features.assets.storage.AssetChartRepository
import org.tokend.template.features.assets.storage.AssetsRepository
import org.tokend.template.features.balances.storage.BalancesRepository
import org.tokend.template.features.clients.repository.CompanyClientsRepository
import org.tokend.template.features.companies.storage.ClientCompaniesRepository
import org.tokend.template.features.companies.storage.CompaniesRepository
import org.tokend.template.features.fees.repository.FeesRepository
import org.tokend.template.features.history.storage.BalanceChangesRepository
import org.tokend.template.features.invest.model.SaleRecord
import org.tokend.template.features.invest.repository.InvestmentInfoRepository
import org.tokend.template.features.invest.repository.SalesRepository
import org.tokend.template.features.keyvalue.storage.KeyValueEntriesRepository
import org.tokend.template.features.kyc.storage.ActiveKycRepository
import org.tokend.template.features.kyc.storage.KycRequestStateRepository
import org.tokend.template.features.limits.repository.LimitsRepository
import org.tokend.template.features.localaccount.storage.LocalAccountRepository
import org.tokend.template.features.offers.repository.OffersRepository
import org.tokend.template.features.polls.repository.PollsRepository
import org.tokend.template.features.send.recipient.contacts.repository.ContactsRepository
import org.tokend.template.features.systeminfo.storage.SystemInfoRepository
import org.tokend.template.features.tfa.repository.TfaFactorsRepository
import org.tokend.template.features.trade.history.repository.TradeHistoryRepository
import org.tokend.template.features.trade.orderbook.repository.OrderBookRepository
import org.tokend.template.features.trade.pairs.repository.AssetPairsRepository

interface RepositoryProvider {
    fun balances(): BalancesRepository
    fun accountDetails(): AccountDetailsRepository
    fun systemInfo(): SystemInfoRepository
    fun tfaFactors(): TfaFactorsRepository
    fun assets(): AssetsRepository
    fun assetPairs(): AssetPairsRepository
    fun orderBook(baseAsset: String, quoteAsset: String): OrderBookRepository
    fun offers(onlyPrimaryMarket: Boolean = false, baseAsset: String? = null,
               quoteAsset: String? = null): OffersRepository
    fun account(): AccountRepository
    fun sales(): SalesRepository
    fun filteredSales(): SalesRepository
    fun contacts(): ContactsRepository
    fun limits(): LimitsRepository
    fun fees(): FeesRepository
    fun balanceChanges(balanceId: String?): BalanceChangesRepository
    fun tradeHistory(base: String, quote: String): TradeHistoryRepository
    fun assetChart(asset: String): AssetChartRepository
    fun assetChart(baseAsset: String, quoteAsset: String): AssetChartRepository
    fun kycRequestState(): KycRequestStateRepository
    fun activeKyc(): ActiveKycRepository
    fun investmentInfo(sale: SaleRecord): InvestmentInfoRepository
    fun polls(ownerAccountId: String): PollsRepository
    fun atomicSwapAsks(asset: String): AtomicSwapAsksRepository
    fun clientCompanies(): ClientCompaniesRepository
    fun companies(): CompaniesRepository
    fun companyClients(): CompanyClientsRepository
    fun companyClientBalanceChanges(clientAccountId: String, assetCode: String): BalanceChangesRepository
    fun keyValueEntries(): KeyValueEntriesRepository
    fun blobs(): BlobsRepository
    fun marketplaceOffers(ownerAccountId: String?): MarketplaceOffersRepository
    fun localAccount(): LocalAccountRepository
}