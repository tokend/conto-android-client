package org.tokend.template.features.recovery.logic

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.blobs.model.Blob
import org.tokend.sdk.api.blobs.model.BlobType
import org.tokend.sdk.factory.GsonFactory
import org.tokend.sdk.keyserver.KeyServer
import org.tokend.sdk.keyserver.models.SignerData
import org.tokend.template.data.model.AccountRecord
import org.tokend.template.data.model.KeyValueEntryRecord
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.kyc.model.KycForm
import org.tokend.template.logic.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.PublicKeyFactory
import org.tokend.wallet.Transaction
import org.tokend.wallet.xdr.*

class SubmitKycRecoveryRequestUseCase(
        private val form: KycForm,
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val accountProvider: AccountProvider,
        private val repositoryProvider: RepositoryProvider,
        private val txManager: TxManager
) {
    private lateinit var networkParams: NetworkParams
    private lateinit var formBlobId: String
    private lateinit var currentSignerData: SignerData
    private var signerRoleToSet: Long = 0L
    private lateinit var resultMetaXdr: String

    fun perform(): Completable {
        return getNetworkParams()
                .doOnSuccess { networkParams ->
                    this.networkParams = networkParams
                }
                .flatMap {
                    getFormBlobId()
                }
                .doOnSuccess { formBlobId ->
                    this.formBlobId = formBlobId
                }
                .flatMap {
                    getCurrentSigner()
                }
                .doOnSuccess { currentSignerData ->
                    this.currentSignerData = currentSignerData
                }
                .flatMap {
                    getSignerRoleToSet()
                }
                .doOnSuccess { signerRoleToSet ->
                    this.signerRoleToSet = signerRoleToSet
                }
                .flatMap {
                    getTransaction()
                }
                .flatMap { transaction ->
                    txManager.submit(transaction)
                }
                .doOnSuccess { result ->
                    this.resultMetaXdr = result.resultMetaXdr!!
                }
                .doOnSuccess {
                    updateRepositories()
                }
                .ignoreElement()
    }

    private fun getNetworkParams(): Single<NetworkParams> {
        return repositoryProvider
                .systemInfo()
                .getNetworkParams()
    }

    private fun getFormBlobId(): Single<String> {
        if (form is KycForm.Empty) {
            return "".toSingle()
        }

        val formJson = GsonFactory().getBaseGson().toJson(form)

        return repositoryProvider
                .blobs()
                .create(Blob(BlobType.KYC_FORM, formJson))
                .map(Blob::id)
    }

    private fun getSignerRoleToSet(): Single<Long> {
        return repositoryProvider
                .keyValueEntries()
                .ensureEntries(listOf(KeyServer.DEFAULT_SIGNER_ROLE_KEY_VALUE_KEY))
                .map { it[KeyServer.DEFAULT_SIGNER_ROLE_KEY_VALUE_KEY] as KeyValueEntryRecord.Number }
                .map { it.value }
    }

    private fun getCurrentSigner(): Single<SignerData> {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))
        val account = accountProvider.getAccount()
                ?: return Single.error(IllegalStateException("Cannot obtain current account"))

        return apiProvider.getApi()
                .v3
                .signers
                .get(accountId)
                .map { signers ->
                    signers
                            .find { it.id == account.accountId }
                            ?.let { SignerData(it) }
                            ?: throw IllegalStateException("No current signer data found")
                }
                .toSingle()
    }

    private fun getTransaction(): Single<Transaction> {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return Single.error(IllegalStateException("No wallet info found"))
        val account = accountProvider.getAccount()
                ?: return Single.error(IllegalStateException("Cannot obtain current account"))

        val operation = CreateKYCRecoveryRequestOp(
                requestID = 0L,
                targetAccount = PublicKeyFactory.fromAccountId(accountId),
                creatorDetails = """{"blob_id":"$formBlobId"}""",
                allTasks = null,
                signersData = arrayOf(UpdateSignerData(
                        publicKey = PublicKeyFactory.fromAccountId(currentSignerData.id),
                        details = currentSignerData.detailsJson ?: "{}",
                        identity = currentSignerData.identity,
                        weight = currentSignerData.weight,
                        roleID = signerRoleToSet,
                        ext = EmptyExt.EmptyVersion()
                )),
                ext = CreateKYCRecoveryRequestOp.CreateKYCRecoveryRequestOpExt.EmptyVersion()
        )

        return TxManager.createSignedTransaction(networkParams, accountId, account,
                Operation.OperationBody.CreateKycRecoveryRequest(operation))
    }

    private fun updateRepositories() {
        try {
            val transactionMeta = TransactionMeta.fromBase64(resultMetaXdr)
                    as TransactionMeta.EmptyVersion
            val requiresReview = transactionMeta
                    .operations
                    .first()
                    .changes
                    .filterIsInstance(LedgerEntryChange.Created::class.java)
                    .map(LedgerEntryChange.Created::created)
                    .map(LedgerEntry::data)
                    .filterIsInstance(LedgerEntry.LedgerEntryData.ReviewableRequest::class.java)
                    .first()
                    .reviewableRequest
                    .tasks
                    .pendingTasks != 0

            repositoryProvider.account().updateKycRecoveryStatus(
                    if (requiresReview)
                        AccountRecord.KycRecoveryStatus.PENDING
                    else
                        AccountRecord.KycRecoveryStatus.NONE
            )
        } catch (_: Exception) {
        }
    }
}