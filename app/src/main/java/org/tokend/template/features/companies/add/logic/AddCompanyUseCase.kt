package org.tokend.template.features.companies.add.logic

import io.reactivex.Completable
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.data.repository.CompaniesRepository

class AddCompanyUseCase(
        private val company: CompanyRecord,
        private val companiesRepository: CompaniesRepository
) {
    fun perform(): Completable {
        return companiesRepository
                .addCompany(company)
    }
}