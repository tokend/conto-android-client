package org.tokend.template.di.providers

import org.tokend.template.data.model.UrlConfig
import org.tokend.template.util.environments.AppEnvironmentsManager

class MultipleEnvironmentsUrlConfigProvider(
        private val appEnvironmentsManager: AppEnvironmentsManager
) : UrlConfigProvider {
    private val configs: List<UrlConfig>
        get() = appEnvironmentsManager.getEnvironment().configs

    override fun getConfigsCount(): Int {
        return configs.size
    }

    override fun hasConfig(index: Int): Boolean {
        return index < getConfigsCount()
    }

    override fun getConfig(index: Int): UrlConfig {
        return configs[index]
    }
}