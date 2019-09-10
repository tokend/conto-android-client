package org.tokend.template.data.repository

import io.reactivex.Single
import org.tokend.rx.extensions.toSingle
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.data.repository.base.SimpleMultipleItemsRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.UrlConfigProvider

open class CompaniesRepository(
        private val apiProvider: ApiProvider,
        private val urlConfigProvider: UrlConfigProvider,
        itemsCache: RepositoryCache<CompanyRecord>
) : SimpleMultipleItemsRepository<CompanyRecord>(itemsCache) {
    private val mItemsMap = mutableMapOf<String, CompanyRecord>()
    val itemsMap: Map<String, CompanyRecord> = mItemsMap

    private val nonCompanyAccounts = mutableSetOf<String>()

    override fun getItems(): Single<List<CompanyRecord>> {
        return apiProvider.getApi()
                .integrations
                .dns
                .getBusiness(FORCED_COMPANY)
                .toSingle()
                .map { listOf(CompanyRecord(it, urlConfigProvider.getConfig())) }
    }

    override fun broadcast() {
        mItemsMap.clear()
        itemsCache.items.associateByTo(mItemsMap, CompanyRecord::id)
        super.broadcast()
    }

    override fun invalidate() = synchronized(this) {
        isFresh = false
        nonCompanyAccounts.clear()
    }

    /**
     * Ensures that given companies are loaded
     */
    fun ensureCompanies(accounts: Collection<String>): Single<Map<String, CompanyRecord>> {
        val toRequest = accounts.filterNot(nonCompanyAccounts::contains)

        return if (itemsMap.keys.containsAll(toRequest))
            Single.just(itemsMap)
        else
            updateDeferred()
                    .doOnComplete {
                        nonCompanyAccounts.addAll(accounts.filterNot(itemsMap::containsKey))
                    }
                    .toSingle { itemsMap }
    }

    companion object {
        const val FORCED_COMPANY = "GB6RXMSM77D4PAJKAD3LWLZ2YTDB47P72VVU27QCDZ6O4FSHBECQYVCV"
    }
}