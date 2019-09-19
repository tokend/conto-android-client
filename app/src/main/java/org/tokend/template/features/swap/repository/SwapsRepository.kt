package org.tokend.template.features.swap.repository

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.Single
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.base.params.PagingParamsV2
import org.tokend.sdk.api.generated.resources.SwapResource
import org.tokend.sdk.api.v3.swaps.params.SwapsPageParams
import org.tokend.sdk.utils.SimplePagedResourceLoader
import org.tokend.sdk.utils.extentions.decodeHex
import org.tokend.template.data.repository.assets.AssetsRepository
import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.data.repository.base.SimpleMultipleItemsRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.UrlConfigProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.extensions.mapSuccessful
import org.tokend.template.features.swap.model.SwapRecord
import org.tokend.template.features.swap.model.SwapState
import org.tokend.template.features.swap.persistence.SwapSecretsPersistor
import java.util.*

class SwapsRepository(
        private val apiProvider: ApiProvider,
        private val urlConfigProvider: UrlConfigProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val objectMapper: ObjectMapper,
        private val secretsPersistor: SwapSecretsPersistor,
        private val assetsRepository: AssetsRepository,
        itemsCache: RepositoryCache<SwapRecord>
) : SimpleMultipleItemsRepository<SwapRecord>(itemsCache) {
    private data class SwapWithSystemIndex(
            val swap: SwapResource,
            val systemIndex: Int
    ) {
        val swapHash: String = swap.secretHash
        val swapCreatedAt: Date = swap.createdAt
    }

    override fun getItems(): Single<List<SwapRecord>> {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))

        return getAllSwaps(accountId)
                .map { allSwaps ->
                    allSwaps.groupBy(SwapWithSystemIndex::swapHash)
                            .mapValues { it.value.sortedBy(SwapWithSystemIndex::swapCreatedAt) }
                }
                .map { swapsByHash ->
                    swapsByHash.entries.mapSuccessful { (_, connectedSwaps) ->
                        val initialSwap = connectedSwaps.first()
                        if (initialSwap.swap.source.id == accountId)
                            getRecordFromSourceSwap(
                                    initialSwap,
                                    connectedSwaps.map(SwapWithSystemIndex::swap)
                            )
                        else
                            getRecordFromDestSwap(
                                    initialSwap,
                                    connectedSwaps.map(SwapWithSystemIndex::swap)
                            )
                    }
                }
                .flatMap(this::loadAndSetAssets)
    }

    private fun getAllSwaps(accountId: String): Single<List<SwapWithSystemIndex>> {
        return (0 until urlConfigProvider.getConfigsCount())
                .map { i ->
                    SimplePagedResourceLoader({ nextCursor ->
                        apiProvider.getApi(i).v3.swaps
                                .get(SwapsPageParams(
                                        pagingParams = PagingParamsV2(
                                                page = nextCursor,
                                                limit = 20
                                        )
                                ))
                    })
                            .loadAll()
                            .toSingle()
                            .map { list ->
                                list
                                        .filter { swapResource ->
                                            swapResource.source.id == accountId
                                                    || swapResource.destination.id == accountId
                                        }
                                        .map { SwapWithSystemIndex(it, i) }
                            }
                }
                .let { Single.merge(it) }
                .collect<MutableList<List<SwapWithSystemIndex>>>(
                        { mutableListOf() },
                        { a, b -> a.add(b) }
                )
                .map { it.flatten() }
    }

    private fun getRecordFromSourceSwap(swapWithSystemIndex: SwapWithSystemIndex,
                                        connectedSwaps: List<SwapResource>): SwapRecord {
        val swapResource = swapWithSystemIndex.swap
        val systemIndex = swapWithSystemIndex.systemIndex
        val hash = swapResource.secretHash
        val remoteState =
                org.tokend.sdk.api.v3.swaps.model.SwapState.fromValue(swapResource.state.value)

        val secret = secretsPersistor.loadSecret(hash)
        var destId: String? = null

        val state = when {
            remoteState == org.tokend.sdk.api.v3.swaps.model.SwapState.CANCELED ->
                SwapState.CANCELED
            connectedSwaps.size == 1 ->
                SwapState.CREATED
            connectedSwaps.size == 2 -> {
                val swapByDest = connectedSwaps.first { it.source.id == swapResource.destination.id }
                destId = swapByDest.id

                when (org.tokend.sdk.api.v3.swaps.model.SwapState.fromValue(swapByDest.state.value)) {
                    org.tokend.sdk.api.v3.swaps.model.SwapState.OPEN ->
                        SwapState.WAITING_FOR_CLOSE_BY_SOURCE
                    org.tokend.sdk.api.v3.swaps.model.SwapState.CLOSED ->
                        SwapState.COMPLETED
                    org.tokend.sdk.api.v3.swaps.model.SwapState.CANCELED ->
                        SwapState.CANCELED_BY_COUNTERPARTY
                }
            }
            else -> throw IllegalStateException("Unable to define state of swap $hash")
        }

        return SwapRecord.fromResource(swapResource, secret, state,
                false, objectMapper, systemIndex, destId)
    }

    private fun getRecordFromDestSwap(swapWithSystemIndex: SwapWithSystemIndex,
                                      connectedSwaps: List<SwapResource>): SwapRecord {
        val swapBySource = swapWithSystemIndex.swap
        val systemIndex = swapWithSystemIndex.systemIndex
        val hash = swapBySource.secretHash
        val sourceSwapState =
                org.tokend.sdk.api.v3.swaps.model.SwapState.fromValue(swapBySource.state.value)

        var secret: ByteArray? = null

        val state = when {
            sourceSwapState == org.tokend.sdk.api.v3.swaps.model.SwapState.CANCELED ->
                SwapState.CANCELED
            connectedSwaps.size == 1 ->
                SwapState.CREATED
            connectedSwaps.size == 2 -> {
                val ourSwap = connectedSwaps.first { it.destination.id == swapBySource.source.id }

                secret = ourSwap.secret?.decodeHex()

                when (org.tokend.sdk.api.v3.swaps.model.SwapState.fromValue(ourSwap.state.value)) {
                    org.tokend.sdk.api.v3.swaps.model.SwapState.OPEN ->
                        SwapState.WAITING_FOR_CLOSE_BY_SOURCE
                    org.tokend.sdk.api.v3.swaps.model.SwapState.CLOSED -> {
                        if (sourceSwapState == org.tokend.sdk.api.v3.swaps.model.SwapState.OPEN)
                            SwapState.CAN_BE_RECEIVED_BY_DEST
                        else
                            SwapState.COMPLETED
                    }
                    else -> throw IllegalStateException("Unable to define state of swap $hash")
                }
            }
            else -> throw IllegalStateException("Unable to define state of swap $hash")
        }

        return SwapRecord.fromResource(swapBySource, secret, state,
                true, objectMapper, systemIndex, null)
    }

    private fun loadAndSetAssets(items: List<SwapRecord>): Single<List<SwapRecord>> {
        val codes = items
                .map { listOf(it.quoteAsset.code, it.baseAsset.code) }
                .flatten()
                .distinct()

        return assetsRepository.ensureAssets(codes)
                .map { assetsMap ->
                    items.apply {
                        forEach { swap ->
                            swap.quoteAsset = assetsMap.getValue(swap.quoteAsset.code)
                            swap.baseAsset = assetsMap.getValue(swap.baseAsset.code)
                        }
                    }
                }
    }

    fun updateSwapState(hash: String,
                        state: SwapState) {
        itemsList
                .find { it.hash == hash }
                ?.also { swap ->
                    swap.state = state
                    itemsCache.update(swap)
                    broadcast()
                }
    }
}