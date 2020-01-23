package org.tokend.template.features.companies.view

import org.tokend.template.features.companies.model.CompanyRecord
import org.tokend.template.data.repository.base.MultipleItemsRepository

class ClientCompaniesFragment: CompaniesListFragment() {
    override val repository: MultipleItemsRepository<CompanyRecord>
        get() = repositoryProvider.clientCompanies()
}