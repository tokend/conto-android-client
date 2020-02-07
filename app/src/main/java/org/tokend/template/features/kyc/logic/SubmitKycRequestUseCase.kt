package org.tokend.template.features.kyc.logic

import android.util.Log
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import org.tokend.sdk.api.blobs.model.Blob
import org.tokend.sdk.api.blobs.model.BlobType
import org.tokend.sdk.factory.GsonFactory
import org.tokend.template.data.model.KeyValueEntryRecord
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.kyc.model.ActiveKyc
import org.tokend.template.features.kyc.model.KycForm
import org.tokend.template.features.kyc.model.KycRequestState
import org.tokend.template.logic.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.PublicKeyFactory
import org.tokend.wallet.Transaction
import org.tokend.wallet.TransactionBuilder
import org.tokend.wallet.xdr.*

/**
 * Creates and submits change-role request with general KYC data.
 * Sets new KYC state in [KycStateRepository] on complete.
 */
class SubmitKycRequestUseCase(
        private val form: KycForm,
        private val walletInfoProvider: WalletInfoProvider,
        private val accountProvider: AccountProvider,
        private val repositoryProvider: RepositoryProvider,
        private val txManager: TxManager
) {
    private val isReviewRequired = form !is KycForm.General
    private val requestIdToSubmit = 0L

    private var roleToSet: Long = 0L
    private lateinit var formBlobId: String
    private lateinit var networkParams: NetworkParams
    private lateinit var transactionResultXdr: String
    private var submittedRequestId = 0L
    private lateinit var newKycRequestState: KycRequestState

    fun perform(): Completable {
        return getRoleToSet()
                .doOnSuccess { roleToSet ->
                    this.roleToSet = roleToSet
                }
                .flatMap {
                    uploadFormAsBlob()
                }
                .doOnSuccess { formBlobId ->
                    this.formBlobId = formBlobId
                }
                .flatMap {
                    getNetworkParams()
                }
                .doOnSuccess { networkParams ->
                    this.networkParams = networkParams
                }
                .flatMap {
                    getTransaction()
                }
                .flatMap { transaction ->
                    txManager.submit(transaction)
                }
                .doOnSuccess { response ->
                    this.transactionResultXdr = response.resultMetaXdr!!
                }
                .flatMap {
                    getSubmittedRequestId()
                }
                .doOnSuccess { submittedRequestId ->
                    this.submittedRequestId = submittedRequestId
                }
                .flatMap {
                    getNewKycRequestState()
                }
                .doOnSuccess { newKycState ->
                    this.newKycRequestState = newKycState
                }
                .flatMap {
                    updateRepositories()
                }
                .ignoreElement()
    }

    private fun getRoleToSet(): Single<Long> {
        val key = when (form) {
            is KycForm.General -> KEY_GENERAL_ACCOUNT_ROLE
            is KycForm.Corporate -> KEY_CORPORATE_ACCOUNT_ROLE
            else -> return Single.error(
                    IllegalArgumentException("Unsupported form type ${form.javaClass.name}")
            )
        }

        return repositoryProvider
                .keyValueEntries()
                .ensureEntries(listOf(key))
                .map { it[key] }
                .map { it as KeyValueEntryRecord.Number }
                .map { it.value }
    }

    private fun uploadFormAsBlob(): Single<String> {
        val formJson = GsonFactory().getBaseGson().toJson(form)

        return repositoryProvider
                .blobs()
                .create(Blob(BlobType.KYC_FORM, formJson))
                .map(Blob::id)
    }

    private fun getNetworkParams(): Single<NetworkParams> {
        return repositoryProvider
                .systemInfo()
                .getNetworkParams()
    }

    private fun getTransaction(): Single<Transaction> {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))
        val account = accountProvider.getAccount()
                ?: return Single.error(IllegalStateException("Cannot obtain current account"))

        return Single.defer {
            val operation = CreateChangeRoleRequestOp(
                    requestID = requestIdToSubmit,
                    destinationAccount = PublicKeyFactory.fromAccountId(accountId),
                    accountRoleToSet = roleToSet,
                    creatorDetails = "{\"blob_id\":\"$formBlobId\"}",
                    allTasks = null,
                    ext = CreateChangeRoleRequestOp.CreateChangeRoleRequestOpExt.EmptyVersion()
            )

            val transaction =
                    TransactionBuilder(networkParams, accountId)
                            .addOperation(Operation.OperationBody.CreateChangeRoleRequest(operation))
                            .build()

            transaction.addSignature(account)

            Single.just(transaction)
        }.subscribeOn(Schedulers.newThread())
    }

    private fun getSubmittedRequestId(): Single<Long> {
        return {
            (TransactionMeta.fromBase64(transactionResultXdr) as? TransactionMeta.EmptyVersion)
                    ?.operations
                    ?.firstOrNull()
                    ?.changes
                    ?.filterIsInstance(LedgerEntryChange.Created::class.java)
                    ?.map { it.created.data }
                    ?.filterIsInstance(LedgerEntry.LedgerEntryData.ReviewableRequest::class.java)
                    ?.first()
                    ?.reviewableRequest
                    ?.requestID
                    ?: 0L
        }.toSingle()
    }

    private fun getNewKycRequestState(): Single<KycRequestState.Submitted<KycForm>> {
        return Single.just(
                if (isReviewRequired)
                    KycRequestState.Submitted.Pending(form, submittedRequestId)
                else
                    KycRequestState.Submitted.Approved(form, submittedRequestId)
        )
    }


    private fun updateRepositories(): Single<Boolean> {
        repositoryProvider
                .kycRequestState()
                .set(newKycRequestState)

        if (!isReviewRequired) {
            repositoryProvider
                    .activeKyc()
                    .set(ActiveKyc.Form(form))
        }

        return Single.just(true)
    }

    private companion object {
        private const val KEY_GENERAL_ACCOUNT_ROLE = "account_role:general"
        private const val KEY_CORPORATE_ACCOUNT_ROLE = "account_role:corporate"
    }
}