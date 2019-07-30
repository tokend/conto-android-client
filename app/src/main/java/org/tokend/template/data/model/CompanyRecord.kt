package org.tokend.template.data.model

import org.tokend.sdk.api.base.model.RemoteFile
import org.tokend.sdk.api.integrations.dns.model.BusinessResource
import org.tokend.sdk.factory.GsonFactory
import java.io.Serializable

class CompanyRecord(
        val id: String,
        val name: String,
        val logoUrl: String?,
        val conversionAssetCode: String?
): Serializable {
    constructor(source: BusinessResource, urlConfig: UrlConfig?) : this(
            id = source.accountId,
            name = source.name,
            logoUrl = source.logoJson
                    .let { GsonFactory().getBaseGson().fromJson(it, RemoteFile::class.java) }
                    .getUrl(urlConfig?.storage),
            // TODO: get from resource
            conversionAssetCode = null
    )

    override fun equals(other: Any?): Boolean {
        return other is CompanyRecord && other.id == this.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}