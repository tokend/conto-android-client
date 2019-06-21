package org.tokend.template.features.companies.view.adapter

import org.tokend.template.data.model.CompanyRecord

class CompanyListItem(
        val id: String,
        val name: String,
        val logoUrl: String?,
        val source: CompanyRecord?
) {
    constructor(source: CompanyRecord) : this(
            id = source.id,
            name = source.name,
            logoUrl = source.logoUrl,
            source = source
    )
}