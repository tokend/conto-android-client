package org.tokend.template.data.model

import org.tokend.sdk.api.identity.model.IdentityResource

/**
 * Holds account identity data
 */
data class IdentityRecord(
        val email: String,
        val accountId: String,
        var phoneNumber: String?
) {
    constructor(source: IdentityResource) : this(
            email = source.email,
            accountId = source.address,
            phoneNumber = source.phoneNumber?.takeIf(String::isNotEmpty)
    )
}