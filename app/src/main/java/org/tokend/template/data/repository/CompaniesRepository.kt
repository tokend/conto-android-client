package org.tokend.template.data.repository

import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.base.params.PagingParamsV2
import org.tokend.sdk.api.integrations.dns.params.ClientsPageParams
import org.tokend.sdk.utils.SimplePagedResourceLoader
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.data.repository.base.SimpleMultipleItemsRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.WalletInfoProvider
import java.util.concurrent.TimeUnit

class CompaniesRepository(
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider,
        itemsCache: RepositoryCache<CompanyRecord>
) : SimpleMultipleItemsRepository<CompanyRecord>(itemsCache) {

    override fun getItems(): Single<List<CompanyRecord>> {
        return listOf(
                CompanyRecord("GBA4EX43M25UPV4WIE6RRMQOFTWXZZRIPFAI5VPY6Z2ZVVXVWZ6NEOOB", "UA Hardware", "http://31c6a837.ngrok.io/_/storage/api/dpurgh4inenif63zciolkasyncu43ubu6qmvqchadtfloz26bjuent6noxjdtjudkg7snt44zur6o5auqnihqpr2"),
                CompanyRecord("GDLWLDE33BN7SG6V4P63V2HFA56JYRMODESBLR2JJ5F3ITNQDUVKS2JE", "Pet shop", null),
                CompanyRecord("2", "Distributed Lab", null)
        ).toSingle()
                .delay(1, TimeUnit.SECONDS)

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
                    companiesResources.map(::CompanyRecord)
                }
    }
}