package org.tokend.template.features.massissuance.model

import org.tokend.sdk.utils.extentions.encodeBase64String
import org.tokend.template.features.assets.model.Asset
import org.tokend.template.features.send.model.PaymentRecipient
import java.io.Serializable
import java.math.BigDecimal
import java.security.SecureRandom

class MassIssuanceRequest(
        val amount: BigDecimal,
        val asset: Asset,
        val recipients: Collection<PaymentRecipient>,
        val issuerAccountId: String,
        val issuerBalanceId: String,
        val referenceSeed: String = SecureRandom.getSeed(16).encodeBase64String()
): Serializable