package org.tokend.template.data.model

import com.fasterxml.jackson.databind.JsonNode
import org.tokend.sdk.api.generated.resources.ExternalSystemIDResource
import org.tokend.sdk.api.ingester.generated.resources.AccountResource
import java.io.Serializable
import java.util.*

class AccountRecord(
        val id: String,
        val kycRecoveryStatus: KycRecoveryStatus,
        val depositAccounts: List<DepositAccount>,
        val roles: MutableSet<Long>,
        val kycBlob: String?
) : Serializable {
    constructor(source: AccountResource) : this(
            id = source.id,
            kycRecoveryStatus = source
                    .kycRecoveryStatus
                    ?.name
                    ?.toUpperCase(Locale.ENGLISH)
                    ?.let(KycRecoveryStatus::valueOf)
                    ?: KycRecoveryStatus.NONE,
            roles = source.roles.map { it.id.toLong() }.toMutableSet(),
            kycBlob = source
                    .kycData
                    ?.kycData
                    ?.get("blob_id")
                    ?.takeIf(JsonNode::isTextual)
                    ?.asText(),
            // TODO: Deposit
            depositAccounts = emptyList()
    )

    class DepositAccount(
            val type: Int,
            val address: String,
            val payload: String?,
            val expirationDate: Date?
    ) : Serializable {
        constructor(source: ExternalSystemIDResource) : this(
                type = source.externalSystemType,
                address = source.data.data.address,
                payload = source.data.data.payload,
                expirationDate = source.expiresAt
        )
    }

    enum class KycRecoveryStatus {
        NONE,
        INITIATED,
        PENDING,
        REJECTED,
        PERMANENTLY_REJECTED;
    }

    val isKycRecoveryActive: Boolean
        get() = kycRecoveryStatus != KycRecoveryStatus.NONE
}