package org.tokend.template.features.companies.model

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

/*
 val id: String,
        val name: String,
        val industry: String?,
        override val logoUrl: String?,
        val conversionAssetCode: String?,
        val bannerUrl: String?,
        val descriptionMd: String?
 */
@Entity(tableName = "company")
data class CompanyDbEntity(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val id: String,
        @ColumnInfo(name = "name")
        val name: String,
        @ColumnInfo(name = "industry")
        val industry: String?,
        @ColumnInfo(name = "logo_url")
        val logoUrl: String?,
        @ColumnInfo(name = "conversion_asset_code")
        val conversionAssetCode: String?,
        @ColumnInfo(name = "banner_url")
        val bannerUrl: String?,
        @ColumnInfo(name = "description_md")
        val descriptionMd: String?
) {
    fun toRecord() = CompanyRecord(
            id = id,
            name = name,
            logoUrl = logoUrl,
            bannerUrl = bannerUrl,
            conversionAssetCode = conversionAssetCode,
            descriptionMd = descriptionMd,
            industry = industry
    )

    companion object {
        fun fromRecord(record: CompanyRecord) = record.run { CompanyDbEntity(
                id = id,
                industry = industry,
                descriptionMd = descriptionMd,
                conversionAssetCode = conversionAssetCode,
                bannerUrl = bannerUrl,
                logoUrl = logoUrl,
                name = name
        ) }
    }
}