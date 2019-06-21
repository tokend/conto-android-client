package org.tokend.template.di.providers

import org.tokend.template.data.model.CompanyRecord

interface CompanyInfoProvider {
    fun getCompany(): CompanyRecord?
    fun setCompany(company: CompanyRecord?)
}