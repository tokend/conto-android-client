package org.tokend.template.features.dashboard.shop.repository

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.Single
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.base.model.DataPage
import org.tokend.sdk.api.base.params.PagingOrder
import org.tokend.sdk.api.base.params.PagingParamsV2
import org.tokend.sdk.api.v3.atomicswap.params.AtomicSwapAskParams
import org.tokend.sdk.api.v3.atomicswap.params.AtomicSwapAsksPageParams
import org.tokend.template.data.model.AtomicSwapAskRecord
import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.data.repository.base.pagination.PagedDataRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.UrlConfigProvider
import org.tokend.template.extensions.mapSuccessful

class AllAtomicSwapAsksRepository(
        private val ownerAccountId: String?,
        private val apiProvider: ApiProvider,
        private val urlConfigProvider: UrlConfigProvider,
        private val objectMapper: ObjectMapper,
        itemsCache: RepositoryCache<AtomicSwapAskRecord>
) : PagedDataRepository<AtomicSwapAskRecord>(itemsCache) {
    override fun getPage(nextCursor: String?): Single<DataPage<AtomicSwapAskRecord>> {
        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))

        return signedApi.v3.atomicSwaps.getAtomicSwapAsks(
                AtomicSwapAsksPageParams(
                        owner = ownerAccountId,
                        include = listOf(
                                AtomicSwapAskParams.Includes.BASE_BALANCE,
                                AtomicSwapAskParams.Includes.QUOTE_ASSETS,
                                AtomicSwapAskParams.Includes.BASE_ASSET
                        ),
                        pagingParams = PagingParamsV2(
                                page = nextCursor,
                                order = PagingOrder.DESC,
                                limit = PAGE_LIMIT
                        )
                )
        )
                .toSingle()
                .map { page ->
                    DataPage(
                            nextCursor = page.nextCursor,
                            isLast = page.isLast,
                            items = page.items.mapSuccessful {
                                AtomicSwapAskRecord(it, emptyMap(),
                                        urlConfigProvider.getConfig(), objectMapper)
                            }
                    )
                }
    }

    private companion object {
        private const val PAGE_LIMIT = 20
    }
}