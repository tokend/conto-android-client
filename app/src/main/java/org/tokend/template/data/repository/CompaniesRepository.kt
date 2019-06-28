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
                CompanyRecord("GBA4EX43M25UPV4WIE6RRMQOFTWXZZRIPFAI5VPY6Z2ZVVXVWZ6NEOOB", "UA Hardware", "http://31c6a837.ngrok.io/_/storage/api/dpurgh4inenif63zciolkasyncu43ubu6qmvqchadtfloz26bjuent6noxjdtjudkg7snt44zur6o5auqnihqpr2"),
                CompanyRecord("GDLWLDE33BN7SG6V4P63V2HFA56JYRMODESBLR2JJ5F3ITNQDUVKS2JE", "Pet shop", null),
                CompanyRecord("2", "Distributed Lab", null)
        ).toSingle()
                .delay(1, TimeUnit.SECONDS)
    }
}