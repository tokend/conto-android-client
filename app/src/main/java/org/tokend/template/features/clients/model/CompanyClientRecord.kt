package org.tokend.template.features.clients.model

import org.tokend.sdk.api.integrations.dns.model.ClientResource
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.SimpleAsset
import java.math.BigDecimal

class CompanyClientRecord(
        val accountId: String,
        val balances: List<Balance>
) {
    class Balance(
            val amount: BigDecimal,
            val asset: Asset
    )

    constructor(source: ClientResource): this(
            accountId = source.accountId,
            balances = listOf(
                    Balance(BigDecimal("0.5"), SimpleAsset("ECO")),
                    Balance(BigDecimal("0.009"), SimpleAsset("KMP")),
                    Balance(BigDecimal("1.43"), SimpleAsset("GH")),
                    Balance(BigDecimal("5"), SimpleAsset("MBR")),
                    Balance(BigDecimal("344.2"), SimpleAsset("JNU")),
                    Balance(BigDecimal("6.483"), SimpleAsset("GBI"))
            )
    )
}