package org.tokend.template.features.companies.add.logic

import io.reactivex.Single
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.data.repository.CompaniesRepository

class CompanyLoader(
        private val companiesRepository: CompaniesRepository
) {
    class NoCompanyFoundException(accountId: String) : Exception(
            "No company found with ID $accountId"
    )

    fun load(companyAccountId: String): Single<CompanyRecord> {
        return companiesRepository
                .getCompanyById(companyAccountId)
                .switchIfEmpty(Single.error(NoCompanyFoundException(companyAccountId)))
    }
}