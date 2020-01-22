package org.tokend.template.features.companies.details.logic

import io.reactivex.Completable
import org.tokend.template.features.companies.model.CompanyRecord
import org.tokend.template.features.companies.storage.ClientCompaniesRepository

class AddCompanyUseCase(
        private val company: CompanyRecord,
        private val clientCompaniesRepository: ClientCompaniesRepository
) {
    fun perform(): Completable {
        return clientCompaniesRepository
                .addCompany(company)
    }
}