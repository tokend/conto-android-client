package org.tokend.template.features.assets.logic

import io.reactivex.Completable
import io.reactivex.Single
import org.tokend.template.data.repository.SystemInfoRepository
import org.tokend.template.data.repository.BalancesRepository
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.logic.transactions.TxManager

/**
 * Creates balance of given asset
 */
class CreateBalanceUseCase(
        private val asset: String,
        private val balancesRepository: BalancesRepository,
        private val systemInfoRepository: SystemInfoRepository,
        private val accountProvider: AccountProvider,
        private val txManager: TxManager
) {
    fun perform(): Completable {
        // Well, quite simple (¬_¬).
        return createBalance()
                .ignoreElement()
    }

    private fun createBalance(): Single<Boolean> {
        return balancesRepository
                .create(
                        accountProvider,
                        systemInfoRepository,
                        txManager,
                        asset
                )
                .toSingleDefault(true)
    }
}