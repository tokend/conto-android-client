package org.tokend.template.di

import android.content.Context
import com.fasterxml.jackson.databind.ObjectMapper
import dagger.Module
import dagger.Provides
import org.tokend.template.di.providers.*
import org.tokend.template.features.kyc.storage.SubmittedKycStatePersistor

@Module
class RepositoriesModule {
    private var currentProvider: RepositoryProvider? = null
    private var currentCompanyId: String? = null

    @Provides
    fun repositoriesProvider(
            apiProvider: ApiProvider,
            walletInfoProvider: WalletInfoProvider,
            urlConfigProvider: UrlConfigProvider,
            companyInfoProvider: CompanyInfoProvider,
            mapper: ObjectMapper,
            context: Context,
            kycStatePersistor: SubmittedKycStatePersistor
    ): RepositoryProvider {
        val company = companyInfoProvider.getCompany()
        val currentProvider = this.currentProvider

        return if (currentCompanyId != company?.id
                || currentProvider == null) {
            currentCompanyId = company?.id

            RepositoryProviderImpl(apiProvider, walletInfoProvider, urlConfigProvider,
                    mapper, context, company, kycStatePersistor)
                    .also { this.currentProvider = it }
        } else {
            return currentProvider
        }
    }
}