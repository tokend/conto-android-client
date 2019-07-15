package org.tokend.template.features.clients.view.adapter

import org.tokend.template.features.clients.model.CompanyClientRecord

class CompanyClientListItem(
        val email: String,
        val status: CompanyClientRecord.Status,
        val balances: List<CompanyClientRecord.Balance>,
        val source: CompanyClientRecord?
) {
    constructor(source: CompanyClientRecord): this(
            email = source.email,
            status = source.status,
            balances = source.balances,
            source = source
    )
}