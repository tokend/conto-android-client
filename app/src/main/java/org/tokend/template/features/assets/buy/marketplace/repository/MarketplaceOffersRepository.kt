package org.tokend.template.features.assets.buy.marketplace.repository

import io.reactivex.Single
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.base.model.DataPage
import org.tokend.sdk.api.base.params.PagingOrder
import org.tokend.sdk.api.base.params.PagingParamsV2
import org.tokend.sdk.api.integrations.marketplace.model.MarketplaceOfferResource
import org.tokend.sdk.api.integrations.marketplace.model.MarketplacePaymentMethodResource
import org.tokend.sdk.api.integrations.marketplace.params.MarketplaceOfferParams
import org.tokend.sdk.api.integrations.marketplace.params.MarketplaceOffersPageParams
import org.tokend.template.data.model.Asset
import org.tokend.template.data.repository.assets.AssetsRepository
import org.tokend.template.data.repository.base.pagination.PagedDataRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.extensions.tryOrNull
import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord
import org.tokend.template.features.companies.model.CompanyRecord
import org.tokend.template.features.companies.storage.CompaniesRepository
import org.tokend.template.util.BiFunctionToPair
import java.math.BigDecimal

class MarketplaceOffersRepository(
        private val ownerAccountId: String?,
        private val apiProvider: ApiProvider,
        private val assetsRepository: AssetsRepository,
        private val companiesRepository: CompaniesRepository
) : PagedDataRepository<MarketplaceOfferRecord>(PagingOrder.DESC, null) {
    override fun getRemotePage(nextCursor: Long?,
                               requiredOrder: PagingOrder): Single<DataPage<MarketplaceOfferRecord>> {
        return apiProvider.getApi().integrations.marketplace
                .getOffers(MarketplaceOffersPageParams(
                        owner = ownerAccountId,
                        include = listOf(MarketplaceOfferParams.Includes.PAYMENT_METHODS),
                        pagingParams = PagingParamsV2(
                                order = requiredOrder,
                                limit = pageLimit,
                                page = nextCursor?.toString()
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
                            BiFunctionToPair<Map<String, Asset>, Map<String, CompanyRecord>>()
                    )
                            .map { (assetsMap, companiesMap) ->
                                Triple(page, assetsMap, companiesMap)
                            }
                }
                .map { (page, assetsMap, companiesMap) ->
                    page.mapItemsNotNull {
                        tryOrNull {
                            if (it.isCanceled) {
                                return@tryOrNull null
                            }

                            MarketplaceOfferRecord(it, assetsMap, companiesMap)
                        }
                    }
                }
    }

    fun updateAvailableAmount(offerId: Long,
                              delta: BigDecimal) {
        itemsList
                .find { it.id == offerId }
                ?.also { offer ->
                    offer.amount += delta
                    cache?.update(offer)
                    broadcast()
                }
    }
}