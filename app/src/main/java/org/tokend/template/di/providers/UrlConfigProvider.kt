package org.tokend.template.di.providers

import org.tokend.template.data.model.UrlConfig

interface UrlConfigProvider {
    fun getConfigsCount(): Int
    fun hasConfig(index: Int = 0): Boolean
    fun getConfig(index: Int = 0): UrlConfig
}