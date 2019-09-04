package org.tokend.template.data.repository

import io.reactivex.Single
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.base.params.PagingParamsV2
import org.tokend.sdk.api.v3.base.PageQueryParams
import org.tokend.sdk.utils.SimplePagedResourceLoader
import org.tokend.sdk.utils.extentions.isBadRequest
import org.tokend.sdk.utils.extentions.isNotFound
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.data.repository.base.SimpleMultipleItemsRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.UrlConfigProvider
import org.tokend.template.extensions.mapSuccessful
import retrofit2.HttpException

open class CompaniesRepository(
        private val apiProvider: ApiProvider,
        private val urlConfigProvider: UrlConfigProvider,
        itemsCache: RepositoryCache<CompanyRecord>
) : SimpleMultipleItemsRepository<CompanyRecord>(itemsCache) {
    private val mItemsMap = mutableMapOf<String, CompanyRecord>()
    val itemsMap: Map<String, CompanyRecord> = mItemsMap

    private val nonCompanyAccounts = mutableSetOf<String>()

    override fun getItems(): Single<List<CompanyRecord>> {

        val loader = SimplePagedResourceLoader({ nextCursor ->
            apiProvider.getApi()
                    .integrations
                    .dns
                    .getBusinesses(
                            PageQueryParams(
                                    PagingParamsV2(page = nextCursor),
                                    null
                            )
                    )
        })

        return loader
                .loadAll()
                .toSingle()
                .map { companiesResources ->
                    companiesResources.mapSuccessful {
                        CompanyRecord(it, urlConfigProvider.getConfig())
                    }
                }
                .onErrorReturn { error ->
                    if (error is HttpException && (error.isBadRequest() || error.isNotFound()))
                        emptyList()
                    else
                        throw error
                }
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
}