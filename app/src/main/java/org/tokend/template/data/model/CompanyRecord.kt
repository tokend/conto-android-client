package org.tokend.template.data.model

import org.tokend.sdk.api.integrations.dns.model.BusinessResource

class CompanyRecord(
        val id: String,
        val name: String,
        val logoUrl: String?,
        val source: BusinessResource? = null
) {
    constructor(source: BusinessResource) : this(
            id = source.id,
            name = source.name,
            logoUrl = source.logoUrl,
            source = source
    )

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is CompanyRecord && other.id == this.id
    }
}