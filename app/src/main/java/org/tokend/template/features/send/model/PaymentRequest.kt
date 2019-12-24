package org.tokend.template.features.send.model

import org.tokend.sdk.utils.extentions.encodeHexString
import org.tokend.template.data.model.Asset
import org.tokend.wallet.utils.Hashing
import java.io.Serializable
import java.math.BigDecimal
import java.security.SecureRandom

data class PaymentRequest(
        val type: PaymentType,
        val amount: BigDecimal,
        val asset: Asset,
        val senderAccountId: String,
        val senderBalanceId: String,
        val recipient: PaymentRecipient,
        val fee: PaymentFee,
        val paymentSubject: String?,
        val actualPaymentSubject: String?,
        val reference: String = Hashing.sha256(SecureRandom.getSeed(16)).encodeHexString()
) : Serializable