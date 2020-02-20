package org.tokend.template.util.environments

import org.tokend.template.features.urlconfig.model.UrlConfig

class AppEnvironment(
        val id: String,
        val name: String,
        val config: UrlConfig
) {
    override fun equals(other: Any?): Boolean {
        return other is AppEnvironment && other.id == this.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}