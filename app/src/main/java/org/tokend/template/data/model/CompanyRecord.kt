package org.tokend.template.data.model

import org.tokend.sdk.api.integrations.dns.model.BusinessResource

class CompanyRecord(
        val id: String,
        val name: String,
        val logoUrl: String?
) {
    constructor(source: BusinessResource) : this(
            id = source.id,
            name = source.name,
            logoUrl = source.logoUrl
    )

    override fun equals(other: Any?): Boolean {
        return other is CompanyRecord && other.id == this.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}