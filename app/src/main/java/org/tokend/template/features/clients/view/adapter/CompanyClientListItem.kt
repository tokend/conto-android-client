package org.tokend.template.features.clients.view.adapter

import org.tokend.template.features.clients.model.CompanyClientRecord

class CompanyClientListItem(
        val email: String,
        val balances: List<CompanyClientRecord.Balance>,
        val source: CompanyClientRecord?
) {
    constructor(source: CompanyClientRecord): this(
            email = source.email,
            balances = source.balances,
            source = source
    )
}