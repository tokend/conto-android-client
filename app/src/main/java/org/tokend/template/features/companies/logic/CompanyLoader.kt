package org.tokend.template.features.companies.logic

import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import org.tokend.template.features.companies.model.CompanyRecord
import org.tokend.template.features.companies.storage.CompaniesRepository

class CompanyLoader(
        private val companiesRepository: CompaniesRepository
) {
    class NoCompanyFoundException(accountId: String) : Exception(
            "No company found with ID $accountId"
    )

    fun load(companyAccountId: String): Single<CompanyRecord> {
        return companiesRepository
                .ensureCompanies(listOf(companyAccountId))
                .flatMapMaybe { companiesMap ->
                    companiesMap[companyAccountId].toMaybe()
                }
                .switchIfEmpty(Single.error(NoCompanyFoundException(companyAccountId)))
    }
}