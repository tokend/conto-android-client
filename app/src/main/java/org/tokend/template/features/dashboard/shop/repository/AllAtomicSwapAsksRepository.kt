package org.tokend.template.features.dashboard.shop.repository

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.Single
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.base.model.DataPage
import org.tokend.sdk.api.base.params.PagingOrder
import org.tokend.sdk.api.base.params.PagingParamsV2
import org.tokend.sdk.api.generated.resources.AtomicSwapQuoteAssetResource
import org.tokend.sdk.api.v3.atomicswap.params.AtomicSwapAskParams
import org.tokend.sdk.api.v3.atomicswap.params.AtomicSwapAsksPageParams
import org.tokend.template.data.model.AtomicSwapAskRecord
import org.tokend.template.data.repository.assets.AssetsRepository
import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.data.repository.base.pagination.PagedDataRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.UrlConfigProvider

class AllAtomicSwapAsksRepository(
        private val ownerAccountId: String?,
        private val apiProvider: ApiProvider,
        private val assetsRepository: AssetsRepository,
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
                .flatMap { page ->
                    val quoteAssetCodes = page.items
                            .map { it.quoteAssets.map(AtomicSwapQuoteAssetResource::getQuoteAsset) }
                            .flatten()

                    assetsRepository
                            .ensureAssets(quoteAssetCodes)
                            .map { page to it }
                }
                .map { (page, assetsMap) ->
                    page.mapItems {
                        AtomicSwapAskRecord(it, assetsMap,
                                urlConfigProvider.getConfig(), objectMapper)
                    }
                }
    }

    private companion object {
        private const val PAGE_LIMIT = 20
    }
}