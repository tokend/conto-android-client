package org.tokend.template.di.providers

import org.tokend.template.data.model.UrlConfig

class SingleEnvironmentUrlConfigProvider(
        private val config: UrlConfig
) : UrlConfigProvider {
    override fun getConfigsCount(): Int = 1

    override fun hasConfig(index: Int): Boolean = true

    override fun getConfig(index: Int): UrlConfig = config
}