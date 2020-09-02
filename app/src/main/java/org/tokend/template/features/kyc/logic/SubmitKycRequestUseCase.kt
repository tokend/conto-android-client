package org.tokend.template.features.kyc.logic

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import org.tokend.sdk.api.blobs.model.Blob
import org.tokend.sdk.api.blobs.model.BlobType
import org.tokend.sdk.factory.GsonFactory
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.keyvalue.model.KeyValueEntryRecord
import org.tokend.template.features.kyc.model.ActiveKyc
import org.tokend.template.features.kyc.model.KycForm
import org.tokend.template.features.kyc.model.KycRequestState
import org.tokend.template.features.kyc.storage.KycRequestStateRepository
import org.tokend.template.logic.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.PublicKeyFactory
import org.tokend.wallet.Transaction
import org.tokend.wallet.TransactionBuilder
import org.tokend.wallet.xdr.*

/**
 * Creates and submits change-role request with general KYC data.
 * Sets new KYC state in [KycRequestStateRepository] on complete.
 */
class SubmitKycRequestUseCase(
        private val form: KycForm,
        private val walletInfoProvider: WalletInfoProvider,
        private val accountProvider: AccountProvider,
        private val repositoryProvider: RepositoryProvider,
        private val txManager: TxManager,
        private val requestIdToSubmit: Long = 0L,
        private val explicitRoleToSet: Long? = null
) {
    private data class SubmittedRequestAttributes(
            val id: Long,
            val isReviewRequired: Boolean
    )

    private var roleToSet: Long = 0L
    private lateinit var formBlobId: String
    private lateinit var networkParams: NetworkParams
    private lateinit var transactionResultXdr: String
    private lateinit var submittedRequestAttributes: SubmittedRequestAttributes
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
                    getSubmittedRequestAttributes()
                }
                .doOnSuccess { submittedRequestAttributes ->
                    this.submittedRequestAttributes = submittedRequestAttributes
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
        if (explicitRoleToSet != null && explicitRoleToSet > 0) {
            return Single.just(explicitRoleToSet)
        }

        val roleKey = form.roleKey

        return repositoryProvider
                .keyValueEntries()
                .ensureEntries(listOf(roleKey))
                .map { it[roleKey] }
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

    private fun getSubmittedRequestAttributes(): Single<SubmittedRequestAttributes> {
        return {
            val request = (TransactionMeta.fromBase64(transactionResultXdr) as TransactionMeta.EmptyVersion)
                    .operations
                    .first()
                    .changes
                    .filter {
                        it is LedgerEntryChange.Created || it is LedgerEntryChange.Updated
                    }
                    .map {
                        if (it is LedgerEntryChange.Created)
                            it.created.data
                        else
                            (it as LedgerEntryChange.Updated).updated.data
                    }
                    .filterIsInstance(LedgerEntry.LedgerEntryData.ReviewableRequest::class.java)
                    .first()
                    .reviewableRequest

            SubmittedRequestAttributes(
                    id = request.requestID,
                    isReviewRequired = request.tasks.pendingTasks > 0
            )
        }.toSingle()
    }

    private fun getNewKycRequestState(): Single<KycRequestState.Submitted<KycForm>> {
        return Single.just(
                if (submittedRequestAttributes.isReviewRequired)
                    KycRequestState.Submitted.Pending(form, submittedRequestAttributes.id, roleToSet)
                else
                    KycRequestState.Submitted.Approved(form, submittedRequestAttributes.id, roleToSet)
        )
    }

    private fun updateRepositories(): Single<Boolean> {
        repositoryProvider
                .kycRequestState()
                .set(newKycRequestState)

        if (!submittedRequestAttributes.isReviewRequired) {
            repositoryProvider
                    .activeKyc()
                    .set(ActiveKyc.Form(form))
        }

        return Single.just(true)
    }

}