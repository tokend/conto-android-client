package org.tokend.template.features.companies.model

import org.tokend.sdk.api.base.model.RemoteFile
import org.tokend.sdk.api.integrations.dns.model.BusinessResource
import org.tokend.sdk.factory.GsonFactory
import org.tokend.template.data.model.RecordWithLogo
import org.tokend.template.data.model.UrlConfig
import java.io.Serializable

class CompanyRecord(
        val id: String,
        val name: String,
        val industry: String?,
        override val logoUrl: String?,
        val conversionAssetCode: String?,
        val bannerUrl: String?,
        val descriptionMd: String?
) : Serializable, RecordWithLogo {
    constructor(source: BusinessResource, urlConfig: UrlConfig?) : this(
            id = source.accountId,
            name = source.name.takeIf(String::isNotEmpty) ?: "Unnamed company",
            industry = source.industry.takeIf(String::isNotEmpty),
            logoUrl = source.logoJson
                    ?.takeIf(String::isNotEmpty)
                    ?.let { GsonFactory().getBaseGson().fromJson(it, RemoteFile::class.java) }
                    ?.getUrl(urlConfig?.storage),
            bannerUrl = source.bannerJson
                    ?.takeIf(String::isNotEmpty)
                    ?.let { GsonFactory().getBaseGson().fromJson(it, RemoteFile::class.java) }
                    ?.getUrl(urlConfig?.storage),
            descriptionMd = source.description?.takeIf(String::isNotEmpty),
            conversionAssetCode = source.statsQuoteAsset.takeIf(String::isNotEmpty)
    )

    override fun equals(other: Any?): Boolean {
        return other is CompanyRecord && other.id == this.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun contentEquals(other: CompanyRecord): Boolean {
        return name == other.name
                && industry == other.industry
                && logoUrl == other.logoUrl
                && bannerUrl == other.bannerUrl
                && conversionAssetCode == other.conversionAssetCode
                && descriptionMd == other.descriptionMd
    }
}