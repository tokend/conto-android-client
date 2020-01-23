package org.tokend.template.features.companies.storage

import android.arch.persistence.room.*
import org.tokend.template.features.assets.model.AssetDbEntity
import org.tokend.template.features.companies.model.CompanyDbEntity

@Dao
interface CompaniesDao {
    @Query("SELECT * FROM company")
    fun selectAll(): List<CompanyDbEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg items: CompanyDbEntity)

    @Update
    fun update(vararg items: CompanyDbEntity)

    @Delete
    fun delete(vararg items: CompanyDbEntity)

    @Query("DELETE FROM company")
    fun deleteAll()
}