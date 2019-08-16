package org.tokend.template.features.clients.model

import org.tokend.sdk.api.integrations.dns.model.ClientResource
import org.tokend.template.data.model.Asset
import java.io.Serializable
import java.math.BigDecimal

class CompanyClientRecord(
        val id: String,
        val accountId: String?,
        val email: String,
        val status: Status,
        val firstName: String?,
        val lastName: String?,
        val balances: List<Balance>
) : Serializable {
    class Balance(
            val amount: BigDecimal,
            val asset: Asset
    ) : Serializable

    enum class Status {
        NOT_REGISTERED,
        ACTIVE,
        BLOCKED
    }

    val fullName: String? =
            if (firstName != null && lastName != null)
                "$firstName\u00A0$lastName"
            else
                null

    constructor(source: ClientResource,
                assetsMap: Map<String, Asset>) : this(
            id = source.id,
            accountId = source.accountId,
            email = source.email,
            status = Status.valueOf(source.status.toUpperCase()),
            firstName = source.firstName?.takeIf(String::isNotEmpty),
            lastName = source.lastName?.takeIf(String::isNotEmpty),
            balances = source.balances
                    ?.map {
                        Balance(it.amount, assetsMap[it.assetCode]
                                ?: throw IllegalStateException("Asset ${it.assetCode} is not in the map"))
                    }
                    ?.sortedByDescending { it.amount.signum() > 0 }
                    ?: emptyList()
    )
}