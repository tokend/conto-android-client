package org.tokend.template.features.balances.storage

import io.reactivex.Completable
import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.extensions.mapSuccessful
import org.tokend.template.features.assets.model.AssetRecord
import org.tokend.template.features.balances.model.BalanceDbEntity
import org.tokend.template.features.balances.model.BalanceRecord
import org.tokend.template.features.companies.model.CompanyRecord

class BalancesDbCache(
        private val dao: BalancesDao,
        private val assetsCache: RepositoryCache<AssetRecord>,
        private val companiesCache: RepositoryCache<CompanyRecord>
) : RepositoryCache<BalanceRecord>() {
    override fun isContentSame(first: BalanceRecord, second: BalanceRecord): Boolean =
            first.contentEquals(second)

    override fun getAllFromDb(): List<BalanceRecord> {
        Completable.merge(listOf(
                assetsCache.loadFromDb(),
                companiesCache.loadFromDb()
        )).blockingAwait()
        val assets = assetsCache.items.associateBy(AssetRecord::code)
        val companies = companiesCache.items.associateBy(CompanyRecord::id)
        return dao.selectAll().mapSuccessful { it.toRecord(assets, companies) }
    }

    override fun addToDb(items: Collection<BalanceRecord>) {
        val assets = items.map(BalanceRecord::asset).distinct()
        assetsCache.add(*assets.toTypedArray())
        val companies = items.mapNotNull(BalanceRecord::company).distinct()
        companiesCache.add(*companies.toTypedArray())
        dao.insert(*items.map(BalanceDbEntity.Companion::fromRecord).toTypedArray())
    }

    override fun updateInDb(items: Collection<BalanceRecord>) {
        val assets = items.map(BalanceRecord::asset).distinct()
        assetsCache.update(*assets.toTypedArray())
        val companies = items.mapNotNull(BalanceRecord::company).distinct()
        companiesCache.update(*companies.toTypedArray())
        dao.update(*items.map(BalanceDbEntity.Companion::fromRecord).toTypedArray())
    }

    override fun deleteFromDb(items: Collection<BalanceRecord>) {
        dao.delete(*items.map(BalanceDbEntity.Companion::fromRecord).toTypedArray())
    }

    override fun clearDb() =
            dao.deleteAll()
}