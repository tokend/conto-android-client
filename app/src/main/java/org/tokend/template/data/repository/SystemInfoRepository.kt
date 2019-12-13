package org.tokend.template.data.repository

import io.reactivex.Observable
import io.reactivex.Single
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.ingester.generated.resources.IngesterStateResource
import org.tokend.sdk.utils.extentions.toNetworkParams
import org.tokend.template.data.repository.base.SimpleSingleItemRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.wallet.NetworkParams

class SystemInfoRepository(
        private val apiProvider: ApiProvider
) : SimpleSingleItemRepository<IngesterStateResource>() {
    override fun getItem(): Observable<IngesterStateResource> {
        return apiProvider.getApi()
                .ingester
                .info
                .get()
                .toSingle()
                .toObservable()
    }

    fun getNetworkParams(): Single<NetworkParams> {
        return updateIfNotFreshDeferred()
                .toSingle {
                    item?.toNetworkParams()
                            ?: throw IllegalStateException("Missing network passphrase")
                }
    }
}