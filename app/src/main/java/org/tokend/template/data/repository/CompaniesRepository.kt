package org.tokend.template.data.repository

import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.data.repository.base.SimpleMultipleItemsRepository
import java.util.concurrent.TimeUnit

class CompaniesRepository(
        itemsCache: RepositoryCache<CompanyRecord>
) : SimpleMultipleItemsRepository<CompanyRecord>(itemsCache) {

    override fun getItems(): Single<List<CompanyRecord>> {
        return listOf(
                CompanyRecord("0", "UA Hardware", null),
                CompanyRecord("1", "Pub Lolek", null),
                CompanyRecord("2", "Mama mia santa Maria", null)
        ).toSingle()
                .delay(1, TimeUnit.SECONDS)
    }
}