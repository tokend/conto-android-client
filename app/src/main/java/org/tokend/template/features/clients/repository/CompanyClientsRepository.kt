package org.tokend.template.features.clients.repository

import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.base.model.DataPage
import org.tokend.sdk.api.base.params.PagingOrder
import org.tokend.sdk.api.base.params.PagingParamsV2
import org.tokend.sdk.api.integrations.dns.params.ClientsPageParams
import org.tokend.template.data.model.SimpleAsset
import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.data.repository.base.pagination.PagedDataRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.clients.model.CompanyClientRecord
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class CompanyClientsRepository(
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider,
        itemsCache: RepositoryCache<CompanyClientRecord>
) : PagedDataRepository<CompanyClientRecord>(itemsCache) {

    override fun getPage(nextCursor: String?): Single<DataPage<CompanyClientRecord>> {
        val balances = listOf(
                CompanyClientRecord.Balance(BigDecimal("0.5"), SimpleAsset("ECO")),
                CompanyClientRecord.Balance(BigDecimal("0.009"), SimpleAsset("KMP")),
                CompanyClientRecord.Balance(BigDecimal("1.43"), SimpleAsset("GH")),
                CompanyClientRecord.Balance(BigDecimal("5"), SimpleAsset("MBR")),
                CompanyClientRecord.Balance(BigDecimal("344.2"), SimpleAsset("JNU")),
                CompanyClientRecord.Balance(BigDecimal("6.483"), SimpleAsset("GBI"))
        )
        return listOf(
                CompanyClientRecord("0", "ole@mail.com", balances.shuffled()),
                CompanyClientRecord("1", "bob@mail.com", balances.subList(0, 2)),
                CompanyClientRecord("2", "dh@gmail.com", listOf())

        ).toSingle().map { DataPage(null, it, true) }
                .delay(1, TimeUnit.SECONDS)

        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))

        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))

        return signedApi
                .integrations
                .dns
                .getBusinessClients(
                        businessId = accountId,
                        params = ClientsPageParams(
                                include = listOf(ClientsPageParams.Includes.BALANCES),
                                pagingParams = PagingParamsV2(
                                        order = PagingOrder.DESC,
                                        page = nextCursor
                                )
                        )
                )
                .toSingle()
                .map { clientsPage ->
                    DataPage(
                            clientsPage.nextCursor,
                            clientsPage.items.map(::CompanyClientRecord),
                            clientsPage.isLast
                    )
                }
    }
}