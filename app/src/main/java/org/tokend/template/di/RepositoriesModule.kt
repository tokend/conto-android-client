package org.tokend.template.di

import android.content.Context
import com.fasterxml.jackson.databind.ObjectMapper
import dagger.Module
import dagger.Provides
import org.tokend.template.di.providers.*
import org.tokend.template.features.kyc.storage.ActiveKycPersistor
import org.tokend.template.features.localaccount.storage.LocalAccountPersistor
import javax.inject.Singleton

@Module
class RepositoriesModule {
    @Provides
    @Singleton
    fun repositoriesProvider(
            apiProvider: ApiProvider,
            walletInfoProvider: WalletInfoProvider,
            urlConfigProvider: UrlConfigProvider,
            mapper: ObjectMapper,
            context: Context,
            localAccountPersistor: LocalAccountPersistor,
            activeKycPersistor: ActiveKycPersistor
    ): RepositoryProvider {
        return RepositoryProviderImpl(apiProvider, walletInfoProvider, urlConfigProvider,
                mapper, context, localAccountPersistor, activeKycPersistor)
    }
}