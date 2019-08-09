package org.tokend.template.features.massissuance.logic

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import org.tokend.sdk.api.transactions.model.SubmitTransactionResponse
import org.tokend.template.data.model.history.SimpleFeeRecord
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.logic.transactions.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.Transaction
import org.tokend.wallet.xdr.Operation
import org.tokend.wallet.xdr.PaymentFeeData
import org.tokend.wallet.xdr.op_extensions.SimplePaymentOp
import java.math.BigDecimal

/**
 * Creates and submits transaction with
 * many issuance operations.
 *
 * Updates balances and clients on success
 *
 * @param emails list of issuance receivers, max size is 100
 */
class PerformMassIssuanceUseCase(
        private val emails: List<String>,
        private val assetCode: String,
        private val amount: BigDecimal,
        private val walletInfoProvider: WalletInfoProvider,
        private val repositoryProvider: RepositoryProvider,
        private val accountProvider: AccountProvider,
        private val txManager: TxManager
) {
    private val balancesRepository = repositoryProvider.balances()

    private lateinit var balanceId: String
    private lateinit var accountId: String
    private lateinit var accounts: List<String>
    private lateinit var networkParams: NetworkParams
    private lateinit var transaction: Transaction

    fun perform(): Completable {
        return checkArguments()
                .flatMap {
                    getBalanceId()
                }
                .doOnSuccess { balanceId ->
                    this.balanceId = balanceId
                }
                .flatMap {
                    getAccountId()
                }
                .doOnSuccess { account ->
                    this.accountId = account
                }
                .flatMap {
                    getAccounts()
                }
                .doOnSuccess { accounts ->
                    this.accounts = accounts
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
                .doOnSuccess { transaction ->
                    this.transaction = transaction
                }
                .flatMap {
                    submitTransaction()
                }
                .doOnSuccess {
                    updateRepositories()
                }
                .ignoreElement()
    }

    private fun checkArguments(): Single<Boolean> {
        return if (emails.size > 100)
            Single.error(IllegalArgumentException("Max emails count is 100"))
        else
            Single.just(true)
    }

    private fun getBalanceId(): Single<String> {
        return balancesRepository
                .updateIfNotFreshDeferred()
                .toSingleDefault(true)
                .flatMapMaybe {
                    balancesRepository
                            .itemsList
                            .find { it.assetCode == assetCode }
                            ?.id
                            .toMaybe()
                }
                .switchIfEmpty(Single.error(
                        IllegalStateException("No balance ID found for $assetCode")
                ))
    }

    private fun getAccountId(): Single<String> {
        return walletInfoProvider
                .getWalletInfo()
                ?.accountId
                .toMaybe()
                .switchIfEmpty(Single.error(IllegalStateException("Missing account ID")))
    }

    private fun getAccounts(): Single<List<String>> {
        val accountDetailsRepository = repositoryProvider.accountDetails()
        return Single.merge(
                emails.map { email ->
                    accountDetailsRepository
                            .getAccountIdByIdentifier(email)
                            .onErrorReturnItem("")
                }
        )
                .filter(String::isNotBlank)
                .toList()
    }

    private fun getNetworkParams(): Single<NetworkParams> {
        return repositoryProvider
                .systemInfo()
                .getNetworkParams()
    }

    private fun getTransaction(): Single<Transaction> {
        val zeroFee = SimpleFeeRecord(BigDecimal.ZERO, BigDecimal.ZERO)
                .toXdrFee(networkParams)

        val operations = accounts.map { accountId ->
            Operation.OperationBody.Payment(
                    SimplePaymentOp(
                            sourceBalanceId = balanceId,
                            destAccountId = accountId,
                            amount = networkParams.amountToPrecised(amount),
                            feeData = PaymentFeeData(
                                    zeroFee, zeroFee, false,
                                    PaymentFeeData.PaymentFeeDataExt.EmptyVersion()
                            ),
                            subject = "Mass issuance"
                    )
            )
        }

        val account = accountProvider.getAccount()
                ?: return Single.error(IllegalStateException("Cannot obtain current account"))

        return TxManager.createSignedTransaction(networkParams, accountId, account,
                *operations.toTypedArray())
    }

    private fun submitTransaction(): Single<SubmitTransactionResponse> {
        return txManager.submit(transaction)
    }

    private fun updateRepositories() {
        repositoryProvider.balances().updateIfEverUpdated()
        repositoryProvider.companyClients().updateIfEverUpdated()
        repositoryProvider.balanceChanges(null).updateIfEverUpdated()
    }
}