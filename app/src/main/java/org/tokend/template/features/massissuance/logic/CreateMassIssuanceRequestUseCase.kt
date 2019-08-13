package org.tokend.template.features.massissuance.logic

import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import io.reactivex.rxkotlin.toSingle
import org.tokend.template.data.model.Asset
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.massissuance.model.MassIssuanceRequest
import java.math.BigDecimal

/**
 * Creates [MassIssuanceRequest] with given params. Resolves emails
 */
class CreateMassIssuanceRequestUseCase(
        private val emails: Collection<String>,
        private val asset: Asset,
        private val amount: BigDecimal,
        private val walletInfoProvider: WalletInfoProvider,
        private val repositoryProvider: RepositoryProvider
) {
    class NoValidRecipientsException : Exception("There are no valid recipients")

    private lateinit var issuerAccountId: String
    private lateinit var issuerBalanceId: String
    private lateinit var recipients: Collection<MassIssuanceRequest.Account>

    fun perform(): Single<MassIssuanceRequest> {
        return getIssuerAccount()
                .doOnSuccess { issuerAccountId ->
                    this.issuerAccountId = issuerAccountId
                }
                .flatMap {
                    getIssuerBalance()
                }
                .doOnSuccess { issuerBalanceId ->
                    this.issuerBalanceId = issuerBalanceId
                }
                .flatMap {
                    getRecipients()
                }
                .doOnSuccess { recipients ->
                    this.recipients = recipients
                }
                .flatMap {
                    getMassIssuanceRequest()
                }

    }

    private fun getIssuerAccount(): Single<String> {
        return walletInfoProvider
                .getWalletInfo()
                ?.accountId
                .toMaybe()
                .switchIfEmpty(Single.error(IllegalStateException("Missing account ID")))
    }

    private fun getIssuerBalance(): Single<String> {
        val balancesRepository = repositoryProvider.balances()
        return balancesRepository
                .updateIfNotFreshDeferred()
                .toSingleDefault(true)
                .flatMapMaybe {
                    balancesRepository
                            .itemsList
                            .find { it.assetCode == asset.code }
                            ?.id
                            .toMaybe()
                }
                .switchIfEmpty(Single.error(
                        IllegalStateException("No balance ID found for $asset")
                ))
    }

    private fun getRecipients(): Single<List<MassIssuanceRequest.Account>> {
        val accountDetailsRepository = repositoryProvider.accountDetails()
        return Maybe.merge(
                emails.map { email ->
                    accountDetailsRepository
                            .getAccountIdByIdentifier(email)
                            .onErrorReturnItem("")
                            .flatMapMaybe { it.takeIf(String::isNotEmpty).toMaybe() }
                            .map { MassIssuanceRequest.Account(it, email) }
                }
        )
                .toList()
                .map { recipients ->
                    if (recipients.isEmpty())
                        throw NoValidRecipientsException()
                    else
                        recipients
                }
    }

    private fun getMassIssuanceRequest(): Single<MassIssuanceRequest> {
        return MassIssuanceRequest(
                amount,
                asset,
                recipients,
                issuerAccountId,
                issuerBalanceId
        ).toSingle()
    }
}