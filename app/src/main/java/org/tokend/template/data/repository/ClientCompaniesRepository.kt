package org.tokend.template.data.repository

import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import org.tokend.rx.extensions.toCompletable
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.base.params.PagingParamsV2
import org.tokend.sdk.api.integrations.dns.params.ClientsPageParams
import org.tokend.sdk.utils.SimplePagedResourceLoader
import org.tokend.sdk.utils.extentions.isBadRequest
import org.tokend.sdk.utils.extentions.isNotFound
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.data.repository.base.SimpleMultipleItemsRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.UrlConfigProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.extensions.mapSuccessful
import retrofit2.HttpException

class ClientCompaniesRepository(
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val urlConfigProvider: UrlConfigProvider,
        itemsCache: RepositoryCache<CompanyRecord>
) : CompaniesRepository(apiProvider, urlConfigProvider, itemsCache) {

    override fun getItems(): Single<List<CompanyRecord>> {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))
        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))

        val loader = SimplePagedResourceLoader({ nextCursor ->
            signedApi
                    .integrations
                    .dns
                    .getClientBusinesses(
                            accountId,
                            ClientsPageParams(
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
                .onErrorResumeNext { error ->
                    if (error is HttpException && (error.isBadRequest() || error.isNotFound()))
                        Single.just(emptyList())
                    else
                        Single.error(error)
                }
    }

    fun addCompany(company: CompanyRecord): Completable {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Completable.error(IllegalStateException("No wallet info found"))

        val signedApi = apiProvider.getSignedApi()
                ?: return Completable.error(IllegalStateException("No signed API instance found"))

        return signedApi
                .integrations
                .dns
                .addClientBusiness(accountId, company.id)
                .toCompletable()
                .doOnComplete {
                    itemsCache.add(company)
                    broadcast()
                }
    }
}