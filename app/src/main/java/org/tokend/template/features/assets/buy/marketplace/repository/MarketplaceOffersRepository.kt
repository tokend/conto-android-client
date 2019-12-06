package org.tokend.template.features.assets.buy.marketplace.repository

import io.reactivex.Single
import io.reactivex.functions.BiFunction
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.base.model.DataPage
import org.tokend.sdk.api.base.params.PagingOrder
import org.tokend.sdk.api.base.params.PagingParamsV2
import org.tokend.sdk.api.integrations.marketplace.model.MarketplaceOfferResource
import org.tokend.sdk.api.integrations.marketplace.model.MarketplacePaymentMethodResource
import org.tokend.sdk.api.integrations.marketplace.params.MarketplaceOfferParams
import org.tokend.sdk.api.integrations.marketplace.params.MarketplaceOffersPageParams
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.data.repository.CompaniesRepository
import org.tokend.template.data.repository.assets.AssetsRepository
import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.data.repository.base.pagination.PagedDataRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.extensions.tryOrNull
import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord
import java.math.BigDecimal

class MarketplaceOffersRepository(
        private val ownerAccountId: String?,
        private val apiProvider: ApiProvider,
        private val assetsRepository: AssetsRepository,
        private val companiesRepository: CompaniesRepository,
        itemsCache: RepositoryCache<MarketplaceOfferRecord>
) : PagedDataRepository<MarketplaceOfferRecord>(itemsCache) {
    override fun getPage(nextCursor: String?): Single<DataPage<MarketplaceOfferRecord>> {
        return apiProvider.getApi().integrations.marketplace
                .getOffers(MarketplaceOffersPageParams(
                        owner = ownerAccountId,
                        include = listOf(MarketplaceOfferParams.Includes.PAYMENT_METHODS),
                        pagingParams = PagingParamsV2(
                                order = PagingOrder.DESC,
                                limit = PAGE_LIMIT,
                                page = nextCursor
                        )
                ))
                .toSingle()
                .flatMap { page ->
                    val assetCodes = page.items
                            .map { resource ->
                                mutableListOf(
                                        resource.baseAsset,
                                        resource.priceAsset
                                ).apply {
                                    addAll(resource.paymentMethods
                                            .map(MarketplacePaymentMethodResource::getAsset))
                                }
                            }
                            .flatten()
                            .distinct()

                    val companyAccounts = page.items
                            .map(MarketplaceOfferResource::getOwner)
                            .distinct()

                    Single.zip(
                            assetsRepository.ensureAssets(assetCodes),
                            companiesRepository.ensureCompanies(companyAccounts),
                            BiFunction { a: Map<String, Asset>, b: Map<String, CompanyRecord> ->
                                a to b
                            }
                    )
                            .map { (assetsMap, companiesMap) ->
                                Triple(page, assetsMap, companiesMap)
                            }
                }
                .map { (page, assetsMap, companiesMap) ->
                    page.mapItemsNotNull {
                        tryOrNull {
                            MarketplaceOfferRecord(it, assetsMap, companiesMap)
                        }
                    }
                }
    }

    fun updateAvailableAmount(offerId: String,
                              delta: BigDecimal) {
        itemsList
                .find { it.id == offerId }
                ?.also { offer ->
                    offer.amount += delta
                    itemsCache.update(offer)
                    broadcast()
                }
    }

    private companion object {
        private const val PAGE_LIMIT = 20
    }
}