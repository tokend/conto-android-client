package org.tokend.template.features.shaketopay.model

import org.tokend.sdk.api.integrations.locator.model.NearbyUser

class NearbyUserRecord(
        val accountId: String,
        val email: String,
        val name: String,
        val avatarUrl: String?
) {
    constructor(source: NearbyUser): this(
            accountId = source.accountId,
            email = source.userData.email,
            name = source.userData.name,
            avatarUrl = source.userData.avatar
    )

    override fun equals(other: Any?): Boolean {
        return other is NearbyUserRecord && other.accountId == this.accountId
    }

    override fun hashCode(): Int {
        return accountId.hashCode()
    }
}