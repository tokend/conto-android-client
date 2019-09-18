package org.tokend.template.util.environments

import org.tokend.template.data.model.UrlConfig

class AppEnvironment(
        val id: String,
        val name: String,
        val configs: List<UrlConfig>
) {
    constructor(id: String, name: String, config: UrlConfig)
            : this(id, name, listOf(config))

    override fun equals(other: Any?): Boolean {
        return other is AppEnvironment && other.id == this.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}