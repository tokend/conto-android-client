package org.tokend.template.data.repository

import io.reactivex.Maybe
import io.reactivex.Single
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
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

class CompaniesRepository(
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val urlConfigProvider: UrlConfigProvider,
        itemsCache: RepositoryCache<CompanyRecord>
) : SimpleMultipleItemsRepository<CompanyRecord>(itemsCache) {

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
                    companiesResources.map {
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

    fun getCompanyById(companyAccountId: String): Maybe<CompanyRecord> {
        return Maybe.just(
                CompanyRecord(
                        id = companyAccountId,
                        name = "Acme LTD",
                        logoUrl = "https://www.iconfinder.com/icons/1874854/download/png/128"
                )
        )
                .delay(1, TimeUnit.SECONDS)
    }
}