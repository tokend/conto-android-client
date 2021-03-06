package org.tokend.template.di.providers

import org.tokend.template.features.urlconfig.model.UrlConfig

class UrlConfigProviderImpl : UrlConfigProvider {
    private var config: UrlConfig? = null

    override fun hasConfig(): Boolean {
        return config != null
    }

    override fun getConfig(): UrlConfig {
        return config!!
    }

    override fun setConfig(config: UrlConfig) {
        this.config = config
    }
}