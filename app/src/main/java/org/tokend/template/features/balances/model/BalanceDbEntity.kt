package org.tokend.template.features.balances.model

import android.arch.persistence.room.*
import org.tokend.sdk.factory.GsonFactory
import org.tokend.template.data.model.*
import org.tokend.template.features.companies.model.CompanyRecord
import java.math.BigDecimal

@Entity(
        tableName = "balance",
        indices = [Index("asset_code"), Index("company_id")]
)
@TypeConverters(BalanceDbEntity.Converters::class)
data class BalanceDbEntity(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val id: String,
        @ColumnInfo(name = "asset_code")
        val assetCode: String,
        @ColumnInfo(name = "company_id")
        val companyId: String?,
        @ColumnInfo(name = "available")
        val available: BigDecimal,
        @ColumnInfo(name = "converted_amount")
        val convertedAmount: BigDecimal?,
        @ColumnInfo(name = "conversion_price")
        val conversionPrice: BigDecimal?,
        @ColumnInfo(name = "conversion_asset")
        val conversionAsset: Asset?
) {
    class Converters {
        private val gson = GsonFactory().getBaseGson()

        @TypeConverter
        fun assetFromJson(value: String?): Asset? {
            return value?.let { gson.fromJson(value, SimpleAsset::class.java) }
        }

        @TypeConverter
        fun assetToJson(value: Asset?): String? {
            return value?.let { gson.toJson(SimpleAsset(it)) }
        }
    }

    fun toRecord(assetsMap: Map<String, AssetRecord>,
                 companiesMap: Map<String, CompanyRecord>) = BalanceRecord(
            id = id,
            asset = assetsMap.getValue(assetCode),
            company = companyId?.let(companiesMap::getValue),
            available = available,
            conversionPrice = conversionPrice,
            convertedAmount = convertedAmount,
            conversionAsset = conversionAsset
    )

    companion object {
        fun fromRecord(record: BalanceRecord) = record.run {
            BalanceDbEntity(
                    id = id,
                    conversionAsset = conversionAsset,
                    convertedAmount = convertedAmount,
                    conversionPrice = conversionPrice,
                    available = available,
                    assetCode = assetCode,
                    companyId = company?.id
            )
        }
    }
}