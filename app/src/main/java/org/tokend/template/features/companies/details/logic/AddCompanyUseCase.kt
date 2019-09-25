package org.tokend.template.features.companies.details.logic

import io.reactivex.Completable
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.data.repository.ClientCompaniesRepository

class AddCompanyUseCase(
        private val company: CompanyRecord,
        private val clientCompaniesRepository: ClientCompaniesRepository
) {
    fun perform(): Completable {
        return clientCompaniesRepository
                .addCompany(company)
    }
}