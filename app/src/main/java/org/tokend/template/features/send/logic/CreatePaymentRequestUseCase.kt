package org.tokend.template.features.send.logic

import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import org.tokend.sdk.factory.GsonFactory
import org.tokend.sdk.keyserver.models.WalletInfo
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.history.details.BalanceChangeCause
import org.tokend.template.data.repository.BalancesRepository
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.send.model.PaymentFee
import org.tokend.template.features.send.model.PaymentRecipient
import org.tokend.template.features.send.model.PaymentRequest
import java.math.BigDecimal

/**
 * Creates payment request with given params:
 * resolves recipient's account ID if needed,
 * loads sender's and recipient's fees
 */
class CreatePaymentRequestUseCase(
        private val recipient: PaymentRecipient,
        private val amount: BigDecimal,
        private val asset: Asset,
        private val subject: String?,
        private val fee: PaymentFee,
        private val walletInfoProvider: WalletInfoProvider,
        private val balancesRepository: BalancesRepository
) {
    class SendToYourselfException : Exception()

    private lateinit var senderAccount: String
    private lateinit var senderBalance: String
    private var subjectContent = subject

    fun perform(): Single<PaymentRequest> {
        return getSenderInfo()
                .doOnSuccess { senderInfo ->
                    this.senderAccount = senderInfo.accountId

                    if (senderAccount == recipient.accountId) {
                        throw SendToYourselfException()
                    }
                }
                .flatMap {
                    getSenderBalance()
                }
                .doOnSuccess { senderBalance ->
                    this.senderBalance = senderBalance
                }
                .flatMap {
                    getSubjectContent()
                }
                .doOnSuccess { subjectContent ->
                    this.subjectContent = subjectContent.takeIf(String::isNotEmpty)
                }
                .flatMap {
                    getPaymentRequest()
                }
    }

    private fun getSenderInfo(): Single<WalletInfo> {
        return walletInfoProvider
                .getWalletInfo()
                .toMaybe()
                .switchIfEmpty(Single.error(IllegalStateException("Missing wallet info")))
    }

    private fun getSenderBalance(): Single<String> {
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

    private fun getSubjectContent(): Single<String> {
        val content = if (recipient is PaymentRecipient.NotExisting)
            BalanceChangeCause.Payment.PaymentToNotExistingRecipientMeta(
                    senderAccount = senderAccount,
                    recipientEmail = recipient.actualEmail,
                    actualSubject = subject
            ).let { GsonFactory().getBaseGson().toJson(it) }
        else
            subject ?: ""

        return Single.just(content)
    }

    private fun getPaymentRequest(): Single<PaymentRequest> {
        return Single.just(
                PaymentRequest(
                        amount = amount,
                        asset = asset,
                        senderAccountId = senderAccount,
                        senderBalanceId = senderBalance,
                        recipient = recipient,
                        fee = fee,
                        paymentSubject = subjectContent,
                        actualPaymentSubject = subject
                )
        )
    }
}