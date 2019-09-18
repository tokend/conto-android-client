package org.tokend.template.di.providers

import org.tokend.template.data.model.UrlConfig
import org.tokend.template.util.environments.AppEnvironmentsManager

class UrlConfigProviderFactory {
    fun createUrlConfigProvider(appEnvironmentsManager: AppEnvironmentsManager): UrlConfigProvider {
        return MultipleEnvironmentsUrlConfigProvider(appEnvironmentsManager)
    }

    fun createUrlConfigProvider(config: UrlConfig): UrlConfigProvider {
        return SingleEnvironmentUrlConfigProvider(config)
    }
}