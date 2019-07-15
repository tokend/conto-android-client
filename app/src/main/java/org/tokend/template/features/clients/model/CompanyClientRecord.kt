package org.tokend.template.features.clients.model

import org.tokend.sdk.api.integrations.dns.model.ClientResource
import org.tokend.template.data.model.Asset
import java.math.BigDecimal

class CompanyClientRecord(
        val accountId: String,
        val email: String,
        val balances: List<Balance>
) {
    class Balance(
            val amount: BigDecimal,
            val asset: Asset
    )

    constructor(source: ClientResource): this(
            accountId = source.accountId,
            email = source.email,
            balances = listOf()
    )
}