package org.tokend.template.features.clients.repository

import io.reactivex.Single
import io.reactivex.functions.BiFunction
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.base.model.DataPage
import org.tokend.sdk.api.base.params.PagingOrder
import org.tokend.sdk.api.base.params.PagingParamsV2
import org.tokend.sdk.api.integrations.dns.model.ClientBalanceResource
import org.tokend.sdk.api.integrations.dns.model.ClientResource
import org.tokend.sdk.api.integrations.dns.params.ClientsPageParams
import org.tokend.template.data.repository.base.pagination.PagedDataRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.assets.storage.AssetsRepository
import org.tokend.template.features.clients.model.CompanyClientRecord

class CompanyClientsRepository(
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val assetsRepository: AssetsRepository
) : PagedDataRepository<CompanyClientRecord>(PagingOrder.DESC, null) {

    override fun getRemotePage(nextCursor: Long?,
                               requiredOrder: PagingOrder): Single<DataPage<CompanyClientRecord>> {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))

        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))

        val getPage = signedApi
                .integrations
                .dns
                .getBusinessClients(
                        businessId = accountId,
                        params = ClientsPageParams(
                                include = listOf(ClientsPageParams.Includes.BALANCES),
                                pagingParams = PagingParamsV2(
                                        order = requiredOrder,
                                        page = nextCursor?.toString(),
                                        limit = pageLimit
                                )
                        )
                )
                .toSingle()

        val updateAssets = assetsRepository.updateIfNotFreshDeferred().toSingleDefault(true)

        return Single.zip(
                getPage,
                updateAssets,
                BiFunction { page: DataPage<ClientResource>, _: Boolean -> page }
        )
                .flatMap { clientsPage ->
                    assetsRepository.ensureAssets(
                            clientsPage
                                    .items
                                    .map {
                                        it.balances?.map(ClientBalanceResource::getAssetCode)
                                                ?: emptyList()
                                    }
                                    .flatten()
                    )
                            .map { clientsPage to it }
                }
                .map { (clientsPage, assetsMap) ->
                    DataPage(
                            clientsPage.nextCursor,
                            clientsPage.items.map { CompanyClientRecord(it, assetsMap)},
                            clientsPage.isLast
                    )
                }
    }
}