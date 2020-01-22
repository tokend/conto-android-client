package org.tokend.template.features.companies.view.adapter

import org.tokend.template.features.companies.model.CompanyRecord

class CompanyListItem(
        val id: String,
        val name: String,
        val industry: String?,
        val logoUrl: String?,
        var exist: Boolean,
        val source: CompanyRecord?
) {
    constructor(source: CompanyRecord, exist: Boolean = false) : this(
            id = source.id,
            name = source.name,
            industry = source.industry,
            logoUrl = source.logoUrl,
            exist = exist,
            source = source
    )
}