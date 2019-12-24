package org.tokend.template.test

import org.tokend.sdk.api.blobs.model.Blob
import org.tokend.sdk.api.blobs.model.BlobType
import org.tokend.sdk.api.ingester.assets.model.AssetState
import org.tokend.sdk.factory.GsonFactory
import org.tokend.sdk.keyserver.models.WalletCreateResult
import org.tokend.sdk.utils.extentions.encodeHexString
import org.tokend.sdk.utils.extentions.toNetworkParams
import org.tokend.template.data.model.UrlConfig
import org.tokend.template.data.repository.SystemInfoRepository
import org.tokend.template.di.providers.*
import org.tokend.template.features.assets.logic.CreateBalanceUseCase
import org.tokend.template.features.signin.logic.PostSignInManager
import org.tokend.template.features.signin.logic.SignInUseCase
import org.tokend.template.logic.Session
import org.tokend.template.logic.TxManager
import org.tokend.wallet.PublicKeyFactory
import org.tokend.wallet.TransactionBuilder
import org.tokend.wallet.xdr.*
import java.math.BigDecimal
import java.security.SecureRandom

object Util {
    private val changeRoleBlob = "{\"first_name\":\"Verified\",\"last_name\":\"User\",\"company\":\"Mak\",\"headquarters\":\"\",\"industry\":\"Food delivery, mobile development \",\"homepage\":\"https://distributedlab.com\",\"description\":\"\",\"documents\":{\"kyc_avatar\":{\"mime_type\":\"image/png\",\"name\":\"new__mak_pusheen.png\",\"key\":\"dpurex4ingy25drvezv66zjnymjxbqj2qnh4og7ctouxampddozxgq4tdh5nfcnc67uk73hsyngwjaovjox2fcdz\"},\"bravo\":{\"mime_type\":\"image/jpeg\",\"name\":\"new__IMG_6468.jpg\",\"key\":\"dpurex4ingy25drvezv66zjnymjxbqj2qnh4og7ctouxampddozxgq4tdh5nfv6hspgesuhopecraurpmfuj3ayg\"}},\"bank_account\":null,\"invite\":\"\"}"

    fun getUrlConfigProvider(url: String = Config.API): UrlConfigProvider {
        return UrlConfigProviderFactory().createUrlConfigProvider(
                UrlConfig(url, "", "")
        )
    }

    fun getVerifiedWallet(email: String,
                          password: CharArray,
                          apiProvider: ApiProvider,
                          session: Session,
                          repositoryProvider: RepositoryProvider?): WalletCreateResult {
        val createResult = apiProvider.getKeyServer()
                .createAndSaveWallet(email, password, apiProvider.getApi().ingester.keyValue)
                .execute().get()

        println("Email is $email")
        println("Account id is " + createResult.rootAccount.accountId)
        println("Password is " +
                password.joinToString(""))

        SignInUseCase(
                email,
                password,
                apiProvider.getKeyServer(),
                session,
                null,
                repositoryProvider?.let { PostSignInManager(it) }
        ).perform().blockingAwait()

        return createResult
    }

    fun makeAccountGeneral(walletInfoProvider: WalletInfoProvider,
                           apiProvider: ApiProvider,
                           systemInfoRepository: SystemInfoRepository,
                           txManager: TxManager) {
        setAccountRole("account_role:general", walletInfoProvider, apiProvider,
                systemInfoRepository, txManager)
    }

    fun makeAccountCorporate(walletInfoProvider: WalletInfoProvider,
                             apiProvider: ApiProvider,
                             systemInfoRepository: SystemInfoRepository,
                             txManager: TxManager) {
        setAccountRole("account_role:corporate", walletInfoProvider, apiProvider,
                systemInfoRepository, txManager)
    }

    private fun setAccountRole(roleKey: String,
                               walletInfoProvider: WalletInfoProvider,
                               apiProvider: ApiProvider,
                               systemInfoRepository: SystemInfoRepository,
                               txManager: TxManager) {
        val accountId = walletInfoProvider.getWalletInfo()!!.accountId
        val api = apiProvider.getApi()

        val roleToSet = api
                .ingester
                .keyValue
                .getById(roleKey)
                .execute()
                .get()
                .value
                .u64!!

        val netParams = systemInfoRepository
                .getNetworkParams()
                .blockingGet()

        val sourceAccount = Config.ADMIN_ACCOUNT

        val op = ChangeAccountRolesOp(
                destinationAccount = PublicKeyFactory.fromAccountId(accountId),
                rolesToSet = arrayOf(roleToSet),
                details = GsonFactory().getBaseGson().toJson(mapOf(
                        "blob_id" to api.blobs
                                .create(
                                        Blob(BlobType.ALPHA, changeRoleBlob),
                                        Config.ADMIN_ACCOUNT.accountId
                                )
                                .execute()
                                .get()
                                .id
                )),
                ext = EmptyExt.EmptyVersion()
        )

        val tx = TransactionBuilder(netParams, sourceAccount.accountId)
                .addOperation(Operation.OperationBody.ChangeAccountRoles(op))
                .build()

        tx.addSignature(sourceAccount)

        txManager.submit(tx).blockingGet()
    }

    fun getSomeMoney(asset: String,
                     amount: BigDecimal,
                     repositoryProvider: RepositoryProvider,
                     accountProvider: AccountProvider,
                     txManager: TxManager): BigDecimal {
        val netParams = repositoryProvider.systemInfo().getNetworkParams().blockingGet()

        val hasBalance = repositoryProvider.balances()
                .itemsList.find { it.assetCode == asset } != null

        if (!hasBalance) {
            CreateBalanceUseCase(
                    asset,
                    repositoryProvider.balances(),
                    repositoryProvider.systemInfo(),
                    accountProvider,
                    txManager
            ).perform().blockingAwait()
        }

        val balanceId = repositoryProvider.balances()
                .itemsList
                .find { it.assetCode == asset }!!
                .id

        val op = IssuanceOp(
                securityType = 0,
                creatorDetails = "{}",
                reference = System.currentTimeMillis().toString(),
                destination = MovementDestination.Balance(PublicKeyFactory.fromBalanceId(balanceId)),
                amount = netParams.amountToPrecised(amount),
                fee = Fee(0, 0, Fee.FeeExt.EmptyVersion()),
                asset = asset,
                ext = EmptyExt.EmptyVersion()
        )

        val sourceAccount = Config.ADMIN_ACCOUNT

        val tx = TransactionBuilder(netParams, sourceAccount.accountId)
                .addOperation(Operation.OperationBody.Issuance(op))
                .build()
        tx.addSignature(sourceAccount)

        txManager.submit(tx).blockingGet()

        repositoryProvider.balances().updateBalance(balanceId, amount)

        return amount
    }

//    fun addFeeForAccount(
//            rootAccountId: String,
//            apiProvider: ApiProvider,
//            txManager: TxManager,
//            feeType: FeeType,
//            feeSubType: Int = 0,
//            asset: String
//    ): Boolean {
//        val sourceAccount = Config.ADMIN_ACCOUNT
//
//        val netParams = apiProvider.getApi().general.getSystemInfo().execute().get().toNetworkParams()
//
//        val fixedFee = netParams.amountToPrecised(BigDecimal("0.050000"))
//        val percentFee = netParams.amountToPrecised(BigDecimal("0.001000"))
//        val upperBound = netParams.amountToPrecised(BigDecimal.TEN)
//        val lowerBound = netParams.amountToPrecised(BigDecimal.ONE)
//
//        val feeOp =
//                CreateFeeOp(
//                        feeType,
//                        asset,
//                        fixedFee,
//                        percentFee,
//                        upperBound,
//                        lowerBound,
//                        feeSubType.toLong(),
//                        accountId = rootAccountId
//                )
//
//        val op = Operation.OperationBody.SetFees(feeOp)
//
//        val tx = TransactionBuilder(netParams, sourceAccount.accountId)
//                .addOperation(op)
//                .build()
//
//        tx.addSignature(sourceAccount)
//
//        val response = txManager.submit(tx).blockingGet()
//
//        return response.isSuccess
//    }

    fun createAsset(
            apiProvider: ApiProvider,
            txManager: TxManager,
            externalSystemType: String? = null
    ): String {
        val sourceAccount = Config.ADMIN_ACCOUNT

        val code = SecureRandom.getSeed(3).encodeHexString().toUpperCase()

        val systemInfo =
                apiProvider.getApi()
                        .ingester
                        .info
                        .get()
                        .execute()
                        .get()
        val netParams = systemInfo.toNetworkParams()

        val assetDetailsJson = GsonFactory().getBaseGson().toJson(mapOf(
                "name" to "$code token",
                "external_system_type" to externalSystemType,
                "description" to "$code description"
        ))

        val op = CreateAssetOp(
                code = code,
                securityType = 0,
                state = AssetState.ACTIVE.value,
                details = assetDetailsJson,
                maxIssuanceAmount = netParams.amountToPrecised(BigDecimal("10000")),
                trailingDigitsCount = 6,
                ext = CreateAssetOp.CreateAssetOpExt.EmptyVersion()
        )

        val tx = TransactionBuilder(netParams, sourceAccount.accountId)
                .addOperation(Operation.OperationBody.CreateAsset(op))
                .build()

        tx.addSignature(sourceAccount)

        txManager.submit(tx).blockingGet()

        return code
    }

    private val random = java.util.Random()

    fun getEmail(): String {
        val adjectives = listOf("adorable", "immortal", "quantum", "casual", "hierarchicalDeterministic",
                "fresh", "lovely", "strange", "sick", "creative", "lucky", "successful", "tired")
        val nouns = listOf("lawyer", "pumpkin", "wallet", "oleg", "dog", "tester", "pen",
                "robot", "think", "bottle", "flower", "AtbBag", "dungeonMaster", "kitten")

        val salt = 1000 + random.nextInt(9000)

        val domain = "mail.com"

        val pickRandom: (List<String>) -> String = { it[random.nextInt(it.size)] }

        return "${pickRandom(adjectives)}${pickRandom(nouns).capitalize()}$salt@$domain"
    }
}