package org.tokend.template.features.kyc.storage

import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import org.json.JSONObject
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.TokenDApi
import org.tokend.sdk.api.base.params.PagingOrder
import org.tokend.sdk.api.base.params.PagingParamsV2
import org.tokend.sdk.api.generated.resources.ChangeRoleRequestResource
import org.tokend.sdk.api.generated.resources.ReviewableRequestResource
import org.tokend.sdk.api.requests.model.base.RequestState
import org.tokend.sdk.api.v3.requests.params.ChangeRoleRequestPageParams
import org.tokend.sdk.api.v3.requests.params.RequestParamsV3
import org.tokend.sdk.utils.extentions.getTypedRequestDetails
import org.tokend.template.data.repository.BlobsRepository
import org.tokend.template.data.repository.base.SingleItemRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.kyc.model.KycForm
import org.tokend.template.features.kyc.model.KycState
import org.tokend.template.features.signin.model.ForcedAccountType

/**
 * Holds user's KYC data and it's state
 */
class KycStateRepository(
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val submittedStatePersistence: SubmittedKycStatePersistence?,
        private val blobsRepository: BlobsRepository
) : SingleItemRepository<KycState>() {
    private class NoRequestFoundException : Exception()

    private data class KycRequestAttributes(
            val state: RequestState,
            val rejectReason: String,
            val blobId: String?
    )

    // region Persistence
    override fun getStoredItem(): Maybe<KycState> {
        return Maybe.defer {
            val state = submittedStatePersistence?.loadItem()

            if (state != null)
                Maybe.just(state)
            else
                Maybe.empty()
        }
    }

    override fun storeItem(item: KycState) {
        // Store only submitted states as the only valuable ones.
        if (item !is KycState.Submitted<*>) {
            return
        }

        submittedStatePersistence?.saveItem(item)
    }
    // endregion

    val itemFormData: KycForm?
        get() = (item as? KycState.Submitted<*>)?.formData

    val isFormApproved: Boolean
        get() = item is KycState.Submitted.Approved<*>

    var forcedType: ForcedAccountType? = null

    val isActualOrForcedGeneral: Boolean
        get() = itemFormData is KycForm.General
                || itemFormData is KycForm.Corporate && !isFormApproved
                || forcedType == ForcedAccountType.GENERAL

    fun set(newState: KycState) {
        onNewItem(newState)
    }

    override fun getItem(): Maybe<KycState> {
        val signedApi = apiProvider.getSignedApi()
                ?: return Maybe.error(IllegalStateException("No signed API instance found"))
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Maybe.error(IllegalStateException("No wallet info found"))

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
                .map<KycState> { (state, rejectReason, kycForm) ->
                    when (state) {
                        RequestState.REJECTED ->
                            KycState.Submitted.Rejected(kycForm, requestId, rejectReason)
                        RequestState.APPROVED ->
                            KycState.Submitted.Approved(kycForm, requestId)
                        else ->
                            KycState.Submitted.Pending(kycForm, requestId)
                    }
                }
                .onErrorResumeNext { error ->
                    if (error is NoRequestFoundException)
                        Single.just(KycState.Empty)
                    else
                        Single.error(error)
                }
                .toMaybe()
    }

    private fun getLastKycRequest(signedApi: TokenDApi,
                                  accountId: String): Maybe<ReviewableRequestResource> {
        return signedApi
                .v3
                .requests
                .getChangeRoleRequests(
                        ChangeRoleRequestPageParams(
                                requestor = accountId,
                                includes = listOf(RequestParamsV3.Includes.REQUEST_DETAILS),
                                pagingParams = PagingParamsV2(
                                        order = PagingOrder.DESC,
                                        limit = 1
                                )
                        )
                )
                .toSingle()
                .flatMapMaybe { page ->
                    page.items.firstOrNull().toMaybe()
                }
    }

    private fun getKycRequestAttributes(request: ReviewableRequestResource): KycRequestAttributes? {
        return try {
            val state = RequestState.fromI(request.stateI)
            val blobId = request.getTypedRequestDetails<ChangeRoleRequestResource>()
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
}