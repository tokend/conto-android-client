package org.tokend.template.data.repository

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.base.params.PagingOrder
import org.tokend.sdk.api.base.params.PagingParamsV2
import org.tokend.sdk.api.generated.resources.AtomicSwapAskResource
import org.tokend.sdk.api.generated.resources.AtomicSwapQuoteAssetResource
import org.tokend.sdk.api.v3.atomicswap.params.AtomicSwapAskParams
import org.tokend.sdk.api.v3.atomicswap.params.AtomicSwapAsksPageParams
import org.tokend.sdk.utils.SimplePagedResourceLoader
import org.tokend.template.data.model.AtomicSwapAskRecord
import org.tokend.template.data.repository.assets.AssetsRepository
import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.data.repository.base.MultipleItemsRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.UrlConfigProvider
import org.tokend.template.extensions.mapSuccessful

class AtomicSwapAsksRepository(
        private val apiProvider: ApiProvider,
        private val asset: String,
        private val assetsRepository: AssetsRepository,
        private val urlConfigProvider: UrlConfigProvider,
        private val objectMapper: ObjectMapper,
        itemsCache: RepositoryCache<AtomicSwapAskRecord>
) : MultipleItemsRepository<AtomicSwapAskRecord>(itemsCache) {

    override fun getItems(): Single<List<AtomicSwapAskRecord>> {
        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))

        val loader = SimplePagedResourceLoader({ nextCursor ->
            signedApi.v3.atomicSwaps.getAtomicSwapAsks(
                    AtomicSwapAsksPageParams(
                            baseAsset = asset,
                            include = listOf(
                                    AtomicSwapAskParams.Includes.BASE_BALANCE,
                                    AtomicSwapAskParams.Includes.QUOTE_ASSETS,
                                    AtomicSwapAskParams.Includes.BASE_ASSET
                            ),
                            pagingParams = PagingParamsV2(
                                    page = nextCursor,
                                    order = PagingOrder.DESC
                            )
                    )
            )
        }, distinct = true)

        return Single.zip(
                loader.loadAll().toSingle(),
                assetsRepository.updateIfNotFreshDeferred().toSingleDefault(true),
                BiFunction { items: List<AtomicSwapAskResource>, _: Boolean ->
                    items
                }
        )
                .flatMap { items ->
                    assetsRepository
                            .ensureAssets(
                                    items
                                            .map { it.quoteAssets.map(AtomicSwapQuoteAssetResource::getQuoteAsset) }
                                            .flatten()
                            )
                            .map { items to it }
                }
                .map { (items, assetsMap) ->
                    items.mapSuccessful {
                        AtomicSwapAskRecord(it, assetsMap, emptyMap(),
                                urlConfigProvider.getConfig(), objectMapper)
                    }
                }
    }
}