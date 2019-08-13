package org.tokend.template.features.massissuance.model

import org.tokend.sdk.utils.extentions.encodeBase64String
import org.tokend.template.data.model.Asset
import java.io.Serializable
import java.math.BigDecimal
import java.security.SecureRandom

class MassIssuanceRequest(
        val amount: BigDecimal,
        val asset: Asset,
        val recipients: Collection<Account>,
        val issuerAccountId: String,
        val issuerBalanceId: String,
        val referenceSeed: String = SecureRandom.getSeed(16).encodeBase64String()
): Serializable {
    class Account(
            val accountId: String,
            val email: String
    ): Serializable
}