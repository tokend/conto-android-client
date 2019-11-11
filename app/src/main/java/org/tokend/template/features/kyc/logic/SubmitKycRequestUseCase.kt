package org.tokend.template.features.kyc.logic

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.blobs.model.Blob
import org.tokend.sdk.api.blobs.model.BlobType
import org.tokend.sdk.factory.GsonFactory
import org.tokend.template.data.model.KeyValueEntryRecord
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.kyc.model.KycForm
import org.tokend.template.features.kyc.model.KycState
import org.tokend.template.features.kyc.storage.KycStateRepository
import org.tokend.template.logic.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.PublicKeyFactory
import org.tokend.wallet.Transaction
import org.tokend.wallet.TransactionBuilder
import org.tokend.wallet.xdr.CreateChangeRoleRequestOp
import org.tokend.wallet.xdr.Operation

/**
 * Creates and submits change-role request with general KYC data.
 * Sets new KYC state in [KycStateRepository] on complete.
 */
class SubmitKycRequestUseCase(
        private val form: KycForm,
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val accountProvider: AccountProvider,
        private val repositoryProvider: RepositoryProvider,
        private val txManager: TxManager
) {
    private val requestIdToSubmit = 0L

    private var roleToSet: Long = 0L
    private lateinit var formBlobId: String
    private lateinit var networkParams: NetworkParams
    private lateinit var newKycState: KycState

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
                .flatMap {
                    getNewKycState()
                }
                .doOnSuccess { newKycState ->
                    this.newKycState = newKycState
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
        val signedApi = apiProvider.getSignedApi()
                ?: return Single.error(IllegalStateException("No signed API instance found"))
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))

        val formJson = GsonFactory().getBaseGson().toJson(form)

        return signedApi
                .blobs
                .create(
                        ownerAccountId = accountId,
                        blob = Blob(
                                type = BlobType.KYC_FORM,
                                value = formJson
                        )
                )
                .map(Blob::id)
                .toSingle()
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

    private fun getNewKycState(): Single<KycState.Submitted.Pending<KycForm>> {
        return Single.just(
                KycState.Submitted.Pending(
                        formData = form,
                        // As soon as we can't edit KYC request once it was submitted
                        // it is not necessary to set a true request id,
                        // so if it was 0 it can stay 0.
                        requestId = requestIdToSubmit
                )
        )
    }

    private fun updateRepositories(): Single<Boolean> {
        repositoryProvider
                .kycState()
                .set(newKycState)

        return Single.just(true)
    }

    private companion object {
        private const val KEY_GENERAL_ACCOUNT_ROLE = "account_role:general"
        private const val KEY_CORPORATE_ACCOUNT_ROLE = "account_role:corporate"
    }
}