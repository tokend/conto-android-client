package org.tokend.template.di

import dagger.Module
import dagger.Provides
import org.tokend.template.di.providers.CompanyInfoProvider
import org.tokend.template.logic.Session
import javax.inject.Singleton

@Module
class CompanyInfoProviderModule {
    @Provides
    @Singleton
    fun companyInfoProvider(session: Session): CompanyInfoProvider {
        return session
    }
}