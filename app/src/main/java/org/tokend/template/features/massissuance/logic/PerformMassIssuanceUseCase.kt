package org.tokend.template.features.massissuance.logic

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.transactions.model.SubmitTransactionResponse
import org.tokend.sdk.api.v3.accounts.params.AccountParamsV3
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.logic.transactions.TxManager
import org.tokend.wallet.NetworkParams
import org.tokend.wallet.PublicKeyFactory
import org.tokend.wallet.Transaction
import org.tokend.wallet.xdr.CreateIssuanceRequestOp
import org.tokend.wallet.xdr.Fee
import org.tokend.wallet.xdr.IssuanceRequest
import org.tokend.wallet.xdr.Operation
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
        private val reference: String,
        private val apiProvider: ApiProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val repositoryProvider: RepositoryProvider,
        private val accountProvider: AccountProvider,
        private val txManager: TxManager
) {
    private lateinit var accountId: String
    private lateinit var accounts: List<String>
    private lateinit var balances: List<String>
    private lateinit var networkParams: NetworkParams
    private lateinit var transaction: Transaction

    fun perform(): Completable {
        return checkArguments()
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
                    getBalances()
                }
                .doOnSuccess { balances ->
                    this.balances = balances
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

    private fun getBalances(): Single<List<String>> {
        val api = apiProvider.getApi()

        return Single.merge(
                accounts.map { accountId ->
                    api
                            .v3
                            .accounts
                            .getById(
                                    accountId = accountId,
                                    params = AccountParamsV3(
                                            include = listOf(
                                                    AccountParamsV3.Includes.BALANCES,
                                                    AccountParamsV3.Includes.BALANCES_ASSET
                                            )
                                    ))
                            .toSingle()
                            .map { account ->
                                account.balances.find { balance ->
                                    balance.asset.id == assetCode
                                }?.id ?: ""
                            }
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
        val operations = balances.map { balanceId ->
            Operation.OperationBody.CreateIssuanceRequest(
                    CreateIssuanceRequestOp(
                            request = IssuanceRequest(
                                    amount = networkParams.amountToPrecised(amount),
                                    asset = assetCode,
                                    receiver = PublicKeyFactory.fromBalanceId(balanceId),
                                    creatorDetails = "{}",
                                    fee = Fee(0, 0, Fee.FeeExt.EmptyVersion()),
                                    ext = IssuanceRequest.IssuanceRequestExt.EmptyVersion()
                            ),
                            reference = reference,
                            allTasks = null,
                            ext = CreateIssuanceRequestOp.CreateIssuanceRequestOpExt.EmptyVersion()
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
    }
}