package org.tokend.template.features.send.logic

import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import org.tokend.sdk.keyserver.models.WalletInfo
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.balances.model.BalanceRecord
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
        private val balance: BalanceRecord,
        private val subject: String?,
        private val fee: PaymentFee,
        private val walletInfoProvider: WalletInfoProvider
) {
    class SendToYourselfException : Exception()

    private lateinit var senderAccount: String

    fun perform(): Single<PaymentRequest> {
        return getSenderInfo()
                .doOnSuccess { senderInfo ->
                    this.senderAccount = senderInfo.accountId

                    if (senderAccount == recipient.accountId) {
                        throw SendToYourselfException()
                    }
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

    private fun getPaymentRequest(): Single<PaymentRequest> {
        return Single.just(
                PaymentRequest(
                        amount = amount,
                        asset = balance.asset,
                        senderAccountId = senderAccount,
                        senderBalanceId = balance.id,
                        recipient = recipient,
                        fee = fee,
                        paymentSubject = subject,
                        actualPaymentSubject = subject
                )
        )
    }
}