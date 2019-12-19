package org.tokend.template.features.kyc.storage

import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import org.json.JSONObject
import org.tokend.template.data.model.AccountRecord
import org.tokend.template.data.repository.AccountRepository
import org.tokend.template.data.repository.BlobsRepository
import org.tokend.template.data.repository.base.SimpleSingleItemRepository
import org.tokend.template.features.kyc.model.ActiveKyc
import org.tokend.template.features.kyc.model.KycForm
import org.tokend.template.features.signin.model.ForcedAccountType

/**
 * Holds user's active KYC data
 */
class ActiveKycRepository(
        private val accountRepository: AccountRepository,
        private val blobsRepository: BlobsRepository,
        private val persistor: ActiveKycPersistor?
) : SimpleSingleItemRepository<ActiveKyc>() {
    val itemFormData: KycForm?
        get() = (item as? ActiveKyc.Form)?.formData

    var forcedType: ForcedAccountType? = null

    val isActualOrForcedGeneral: Boolean
        get() = itemFormData is KycForm.General
                || forcedType == ForcedAccountType.GENERAL

    // region Persistence
    override fun getStoredItem(): Observable<ActiveKyc> {
        return Observable.defer {
            val state = persistor?.load()

            if (state != null)
                Observable.just(state)
            else
                Observable.empty()
        }
    }

    override fun storeItem(item: ActiveKyc) {
        persistor?.save(item)
    }
    // endregion

    override fun getItem(): Observable<ActiveKyc> {
        return getAccount()
                .flatMap { account ->
                    if (account.kycBlob != null)
                        getForm(account.kycBlob)
                                .map(ActiveKyc::Form)
                    else
                        Single.just(ActiveKyc.Missing)
                }
                .toObservable()
    }

    private fun getAccount(): Single<AccountRecord> {
        return accountRepository
                .run {
                    updateDeferred()
                            .andThen(Maybe.defer { item.toMaybe() })
                            .switchIfEmpty(Single.error(IllegalStateException("Missing account")))
                }
    }

    private fun getForm(blobId: String): Single<KycForm> {
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