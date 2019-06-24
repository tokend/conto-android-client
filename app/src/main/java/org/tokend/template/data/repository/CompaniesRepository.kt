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
                CompanyRecord("GBA4EX43M25UPV4WIE6RRMQOFTWXZZRIPFAI5VPY6Z2ZVVXVWZ6NEOOB", "UA Hardware", null),
                CompanyRecord("GDLWLDE33BN7SG6V4P63V2HFA56JYRMODESBLR2JJ5F3ITNQDUVKS2JE", "Pub Lolek", null),
                CompanyRecord("2", "Mama mia santa Maria", null)
        ).toSingle()
                .delay(1, TimeUnit.SECONDS)
    }
}