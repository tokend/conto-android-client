package org.tokend.template.features.kyc.logic

import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import io.reactivex.schedulers.Schedulers
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.blobs.model.Blob
import org.tokend.sdk.api.blobs.model.BlobType
import org.tokend.sdk.factory.GsonFactory
import org.tokend.sdk.keyserver.KeyServer
import org.tokend.template.data.model.KeyValueEntryRecord
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
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
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val accountProvider: AccountProvider,
        private val repositoryProvider: RepositoryProvider,
        private val txManager: TxManager
) {
    private val requestIdToSubmit = 0L
    private val isReviewRequired = form !is KycForm.General

    private var roleToAdd: Long = 0L
    private var requestType: Long = 0L
    private lateinit var currentRoles: Set<Long>
    private lateinit var formBlobId: String
    private lateinit var networkParams: NetworkParams
    private lateinit var newKycRequestState: KycRequestState

    fun perform(): Completable {
        return ensureKeyValues()
                .flatMap {
                    getRoleToAdd()
                }
                .doOnSuccess { roleToSet ->
                    this.roleToAdd = roleToSet
                }
                .flatMap {
                    getRequestType()
                }
                .doOnSuccess { requestType ->
                    this.requestType = requestType
                }
                .flatMap {
                    getCurrentRoles()
                }
                .doOnSuccess { currentRoles ->
                    this.currentRoles = currentRoles
                }
                .flatMap {
                    validateRoleToSet()
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

    private fun ensureKeyValues(): Single<Boolean> {
        return repositoryProvider
                .keyValueEntries()
                .ensureEntries(listOf(
                        KEY_GENERAL_ACCOUNT_ROLE,
                        KEY_CORPORATE_ACCOUNT_ROLE,
                        KycRequestStateRepository.CHANGE_ROLE_REQUEST_TYPE_KEY
                ))
                .map { true }
    }

    private fun getRoleToAdd(): Single<Long> {
        val key = when (form) {
            is KycForm.General -> KEY_GENERAL_ACCOUNT_ROLE
            is KycForm.Corporate -> KEY_CORPORATE_ACCOUNT_ROLE
            else -> return Single.error(
                    IllegalArgumentException("Unsupported form type ${form.javaClass.name}")
            )
        }

        return repositoryProvider
                .keyValueEntries()
                .getEntry(key)
                .toMaybe()
                .map { it as KeyValueEntryRecord.Number }
                .map { it.value }
                .switchIfEmpty(Single.error(IllegalStateException("No role key value found: $key")))
    }

    private fun getRequestType(): Single<Long> {
        return repositoryProvider
                .keyValueEntries()
                .getEntry(KeyServer.DEFAULT_SIGNER_ROLE_KEY_VALUE_KEY)
                .toMaybe()
                .map { it as KeyValueEntryRecord.Number }
                .map { it.value }
                .switchIfEmpty(Single.error(IllegalStateException("No request type key value found: " +
                        KeyServer.DEFAULT_SIGNER_ROLE_KEY_VALUE_KEY)))
    }

    private fun getCurrentRoles(): Single<Set<Long>> {
        return repositoryProvider
                .account()
                .run {
                    updateIfNotFreshDeferred()
                            .andThen(Maybe.defer<Set<Long>> { item?.roles.toMaybe() })
                            .switchIfEmpty(Single.error(
                                    IllegalStateException("Unable to obtain current roles")))
                }
    }

    private fun validateRoleToSet(): Single<Boolean> {
        return if (currentRoles.contains(roleToAdd))
            Single.error(IllegalArgumentException(
                    "Attempt to add role $roleToAdd which is already there"))
        else
            Single.just(true)
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
            val operation = ChangeAccountRolesOp(
                    destinationAccount = PublicKeyFactory.fromAccountId(accountId),
                    rolesToSet = arrayOf(*currentRoles.toTypedArray(), roleToAdd),
                    details = "{\"blob_id\":\"$formBlobId\"}",
                    ext = EmptyExt.EmptyVersion()
            )

            val request = CreateReviewableRequestOp(
                    operations = arrayOf(ReviewableRequestOperation.ChangeAccountRoles(operation)),
                    securityType = requestType.toInt(),
                    creatorDetails = "{}",
                    ext = EmptyExt.EmptyVersion()
            )

            val transaction =
                    TransactionBuilder(networkParams, accountId)
                            .apply {
                                if (!isReviewRequired) {
                                    addOperation(Operation.OperationBody.ChangeAccountRoles(operation))
                                } else {
                                    addOperation(Operation.OperationBody.CreateReviewableRequest(request))
                                }
                            }
                            .build()

            transaction.addSignature(account)

            Single.just(transaction)
        }.subscribeOn(Schedulers.computation())
    }

    private fun getNewKycRequestState(): Single<KycRequestState.Submitted<KycForm>> {
        // As soon as we can't edit KYC request once it was submitted
        // it is not necessary to set a true request id,
        // so if it was 0 it can stay 0.
        val requestId = requestIdToSubmit

        return Single.just(
                if (isReviewRequired)
                    KycRequestState.Submitted.Pending(form, requestId)
                else
                    KycRequestState.Submitted.Approved(form, requestIdToSubmit)
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

        repositoryProvider
                .account()
                .item
                ?.roles
                ?.add(roleToAdd)

        return Single.just(true)
    }

    private companion object {
        private const val KEY_GENERAL_ACCOUNT_ROLE = "role:general"
        private const val KEY_CORPORATE_ACCOUNT_ROLE = "role:corporate"
    }
}