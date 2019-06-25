package org.tokend.template.features.redeem.create.logic

import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import org.tokend.template.data.model.BalanceRecord
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.redeem.model.RedemptionRequest
import org.tokend.wallet.Account
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction
import org.tokend.wallet.TransactionBuilder
import org.tokend.wallet.xdr.Fee
import org.tokend.wallet.xdr.Operation
import org.tokend.wallet.xdr.PaymentFeeData
import org.tokend.wallet.xdr.op_extensions.SimplePaymentOp
import java.math.BigDecimal
import kotlin.random.Random

/**
 * Creates [RedemptionRequest] based on payment
 * transaction addressed to [assetCode] owner account
 */
class CreateRedemptionRequestUseCase(
        private val amount: BigDecimal,
        private val assetCode: String,
        private val repositoryProvider: RepositoryProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val accountProvider: AccountProvider
) {
    data class Result(
            val request: RedemptionRequest,
            val networkParams: NetworkParams
    )

    private lateinit var account: Account
    private lateinit var networkParams: NetworkParams
    private lateinit var senderAccountId: String
    private lateinit var balance: BalanceRecord
    private lateinit var transaction: Transaction

    fun perform(): Single<Result> {
        return getNetworkParams()
                .doOnSuccess { networkParams ->
                    this.networkParams = networkParams
                }
                .flatMap {
                    getAccount()
                }
                .doOnSuccess { account ->
                    this.account = account
                }
                .flatMap {
                    getSenderAccountId()
                }
                .doOnSuccess { senderAccountId ->
                    this.senderAccountId = senderAccountId
                }
                .flatMap {
                    getBalance()
                }
                .doOnSuccess { balance ->
                    this.balance = balance
                }
                .flatMap {
                    getTransaction()
                }
                .doOnSuccess { transaction ->
                    this.transaction = transaction
                }
                .flatMap {
                    getRedemptionRequest()
                }
                .map { redemptionRequest ->
                    Result(redemptionRequest, networkParams)
                }
    }

    private fun getNetworkParams(): Single<NetworkParams> {
        return repositoryProvider
                .systemInfo()
                .getNetworkParams()
    }

    private fun getBalance(): Single<BalanceRecord> {
        return repositoryProvider
                .balances()
                .itemsList
                .find { it.assetCode == assetCode }
                .toMaybe()
                .switchIfEmpty(Single.error(IllegalStateException("Missing balance record")))
    }

    private fun getAccount(): Single<Account> {
        return accountProvider
                .getAccount()
                .toMaybe()
                .switchIfEmpty(Single.error(IllegalStateException("Missing account")))
    }

    private fun getSenderAccountId(): Single<String> {
        return walletInfoProvider
                .getWalletInfo()
                ?.accountId
                .toMaybe()
                .switchIfEmpty(Single.error(IllegalStateException("Missing account ID")))
    }

    private fun getTransaction(): Single<Transaction> {
        val recipientAccountId = balance.asset.ownerAccountId

        val emptyFee = Fee(0L, 0L, Fee.FeeExt.EmptyVersion())

        val salt = Random.nextLong() and 0xffffffffL

        val op = SimplePaymentOp(
                sourceBalanceId = balance.id,
                destAccountId = recipientAccountId,
                amount = networkParams.amountToPrecised(amount),
                feeData = PaymentFeeData(
                        sourceFee = emptyFee,
                        destinationFee = emptyFee,
                        sourcePaysForDest = false,
                        ext = PaymentFeeData.PaymentFeeDataExt.EmptyVersion()
                ),
                reference = salt.toString()
        )

        return {
            TransactionBuilder(networkParams, senderAccountId)
                    .addOperation(Operation.OperationBody.Payment(op))
                    .setSalt(salt)
                    .addSigner(account)
                    .build()
        }.toSingle().subscribeOn(Schedulers.computation())
    }

    private fun getRedemptionRequest(): Single<RedemptionRequest> {
        return {
            RedemptionRequest
                    .fromTransaction(transaction, assetCode)
        }.toSingle()
    }
}