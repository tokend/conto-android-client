package org.tokend.template.features.clients.model

import org.tokend.sdk.api.integrations.dns.model.ClientResource
import org.tokend.template.data.model.Asset
import org.tokend.template.data.repository.base.pagination.PagingRecord
import org.tokend.template.extensions.tryOrNull
import java.io.Serializable
import java.math.BigDecimal

class CompanyClientRecord(
        val id: Long,
        val accountId: String?,
        val email: String,
        val status: Status,
        val firstName: String?,
        val lastName: String?,
        val balances: List<Balance>
) : Serializable, PagingRecord {
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

    override fun equals(other: Any?): Boolean =
            other is CompanyClientRecord && other.id == this.id

    override fun hashCode(): Int =
            id.hashCode()

    override fun getPagingId(): Long = id

    constructor(source: ClientResource,
                assetsMap: Map<String, Asset>) : this(
            id = source.id.toLong(),
            accountId = source.accountId,
            email = source.email,
            status = Status.valueOf(source.status.toUpperCase()),
            firstName = source.firstName?.takeIf(String::isNotEmpty),
            lastName = source.lastName?.takeIf(String::isNotEmpty),
            balances = source.balances
                    ?.mapNotNull {
                        tryOrNull {
                            Balance(it.amount, assetsMap[it.assetCode]
                                    ?: throw IllegalStateException("Asset ${it.assetCode} is not in the map"))
                        }
                    }
                    ?.sortedByDescending { it.amount.signum() > 0 }
                    ?: emptyList()
    )
}