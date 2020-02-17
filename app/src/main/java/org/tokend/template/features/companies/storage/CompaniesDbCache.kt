package org.tokend.template.features.companies.storage

import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.extensions.mapSuccessful
import org.tokend.template.features.companies.model.CompanyDbEntity
import org.tokend.template.features.companies.model.CompanyRecord

class CompaniesDbCache(
        private val dao: CompaniesDao
) : RepositoryCache<CompanyRecord>() {
    override fun isContentSame(first: CompanyRecord, second: CompanyRecord): Boolean =
            first.contentEquals(second)

    override fun getAllFromDb(): List<CompanyRecord> =
            dao.selectAll().mapSuccessful(CompanyDbEntity::toRecord)

    override fun addToDb(items: Collection<CompanyRecord>) =
            dao.insert(*items.map(CompanyDbEntity.Companion::fromRecord).toTypedArray())

    override fun updateInDb(items: Collection<CompanyRecord>) =
            dao.update(*items.map(CompanyDbEntity.Companion::fromRecord).toTypedArray())

    override fun deleteFromDb(items: Collection<CompanyRecord>) =
            dao.delete(*items.map(CompanyDbEntity.Companion::fromRecord).toTypedArray())

    override fun clearDb() =
            dao.deleteAll()
}