package org.tokend.template.di.providers

import org.tokend.template.data.model.CompanyRecord

class CompanyInfoProviderImpl : CompanyInfoProvider {
    private var company: CompanyRecord? = null

    override fun getCompany(): CompanyRecord? {
        return this.company
    }

    override fun setCompany(company: CompanyRecord?) {
        this.company = company
    }
}