package org.tokend.template.data.repository

import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.toMaybe
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.general.model.SystemInfo
import org.tokend.template.data.repository.base.SimpleSingleItemRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.wallet.NetworkParams

class SystemInfoRepository(
        private val apiProvider: ApiProvider
) : SimpleSingleItemRepository<SystemInfo>() {
    private val systemInfoMap = mutableMapOf<Int, SystemInfo>()

    override fun getItem(): Observable<SystemInfo> {
        return getSystemInfo(0).toObservable()
    }

    private fun getSystemInfo(index: Int): Single<SystemInfo> {
        return systemInfoMap[index]
                .toMaybe()
                .switchIfEmpty(
                        apiProvider.getApi(index).general.getSystemInfo().toSingle()
                                .doOnSuccess { systemInfo ->
                                    systemInfoMap[index] = systemInfo
                                }
                )
    }

    override fun invalidate() {
        super.invalidate()
        systemInfoMap.clear()
    }

    fun getNetworkParams(index: Int = 0): Single<NetworkParams> {
        return getSystemInfo(index).map(SystemInfo::toNetworkParams)
    }
}