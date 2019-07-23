package org.tokend.template.features.clients.view.adapter

import org.tokend.template.features.clients.model.CompanyClientRecord

class CompanyClientListItem(
        val id: String,
        val email: String,
        val status: CompanyClientRecord.Status,
        val balances: List<CompanyClientRecord.Balance>,
        var isChecked: Boolean = false,
        val source: CompanyClientRecord?
) {
    constructor(source: CompanyClientRecord): this(
            id = source.id,
            email = source.email,
            status = source.status,
            balances = source.balances,
            source = source
    )
}