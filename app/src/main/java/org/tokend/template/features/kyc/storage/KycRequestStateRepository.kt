package org.tokend.template.features.kyc.storage

import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import org.json.JSONObject
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.TokenDApi
import org.tokend.sdk.api.base.params.PagingOrder
import org.tokend.sdk.api.base.params.PagingParamsV2
import org.tokend.sdk.api.ingester.generated.resources.ReviewableRequestResource
import org.tokend.sdk.api.ingester.requests.model.RequestState
import org.tokend.sdk.api.ingester.requests.params.IngesterRequestParams
import org.tokend.sdk.api.ingester.requests.params.IngesterRequestsPageParams
import org.tokend.template.data.model.KeyValueEntryRecord
import org.tokend.template.data.repository.BlobsRepository
import org.tokend.template.data.repository.KeyValueEntriesRepository
import org.tokend.template.data.repository.base.SimpleSingleItemRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.kyc.model.KycForm
import org.tokend.template.features.kyc.model.KycRequestState

/**
 * Holds user's KYC request data and it's state
 */
class KycRequestStateRepository(
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val blobsRepository: BlobsRepository,
        private val keyValueEntriesRepository: KeyValueEntriesRepository
) : SimpleSingleItemRepository<KycRequestState>() {
    private class NoRequestFoundException : Exception()

    private data class KycRequestAttributes(
            val state: RequestState,
            val rejectReason: String,
            val blobId: String?
    )

    override fun getItem(): Observable<KycRequestState> {
        val signedApi = apiProvider.getSignedApi()
                ?: return Observable.error(IllegalStateException("No signed API instance found"))
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Observable.error(IllegalStateException("No wallet info found"))

        var requestId: Long = 0

        return getLastKycRequest(signedApi, accountId)
                .switchIfEmpty(Single.error(NoRequestFoundException()))
                .doOnSuccess { request ->
                    requestId = request.id.toLong()
                }
                .map { request ->
                    getKycRequestAttributes(request)
                            ?: throw InvalidKycDataException()
                }
                .flatMap { (state, rejectReason, blobId) ->
                    loadKycFormFromBlob(blobId)
                            .map { kycForm ->
                                Triple(state, rejectReason, kycForm)
                            }
                }
                .map<KycRequestState> { (state, rejectReason, kycForm) ->
                    when (state) {
                        RequestState.REJECTED ->
                            KycRequestState.Submitted.Rejected(kycForm, requestId, rejectReason)
                        RequestState.APPROVED ->
                            KycRequestState.Submitted.Approved(kycForm, requestId)
                        else ->
                            KycRequestState.Submitted.Pending(kycForm, requestId)
                    }
                }
                .onErrorResumeNext { error ->
                    if (error is NoRequestFoundException)
                        Single.just(KycRequestState.Empty)
                    else
                        Single.error(error)
                }
                .toObservable()
    }

    private fun getLastKycRequest(signedApi: TokenDApi,
                                  accountId: String): Maybe<ReviewableRequestResource> {
        return keyValueEntriesRepository
                .ensureEntries(listOf(CHANGE_ROLE_REQUEST_TYPE_KEY))
                .map { it[CHANGE_ROLE_REQUEST_TYPE_KEY] as KeyValueEntryRecord.Number }
                .map { it.value.toInt() }
                .flatMap { requestType ->
                    signedApi
                            .ingester
                            .requests
                            .getPage(IngesterRequestsPageParams(
                                    requestor = accountId,
                                    type = requestType,
                                    include = listOf(IngesterRequestParams.REQUEST_DETAILS),
                                    pagingParams = PagingParamsV2(
                                            order = PagingOrder.DESC,
                                            limit = 1
                                    )
                            ))
                            .toSingle()
                }
                .flatMapMaybe { page ->
                    page.items.firstOrNull().toMaybe()
                }
    }

    private fun getKycRequestAttributes(request: ReviewableRequestResource): KycRequestAttributes? {
        return try {
            val state = RequestState.fromValue(request.stateI)
            val blobId = request.requestDetails
                    .creatorDetails
                    .get("blob_id")
                    ?.asText()
            val rejectReason = request.rejectReason ?: ""

            KycRequestAttributes(state, rejectReason, blobId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadKycFormFromBlob(blobId: String?): Single<KycForm> {
        if (blobId == null) {
            return Single.just(KycForm.Empty)
        }

        return blobsRepository
                .getById(blobId, true)
                .map { blob ->
                    try {
                        val valueJson = JSONObject(blob.valueString)

                        val isGeneral = valueJson.has(KycForm.General.FIRST_NAME_KEY)
                        val isCorporate = valueJson.has(KycForm.Corporate.COMPANY_KEY)

                        when {
                            isGeneral ->
                                blob.getValue(KycForm.General::class.java)
                            isCorporate ->
                                blob.getValue(KycForm.Corporate::class.java)
                            else ->
                                throw IllegalStateException("Unknown KYC form type")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        KycForm.Empty
                    }
                }
    }

    companion object {
        const val CHANGE_ROLE_REQUEST_TYPE_KEY = "request:change_role"
    }
}