package org.tokend.template.features.companies.logic

import io.reactivex.Completable
import org.tokend.template.di.providers.RepositoryProvider

/**
 * Loads user data related to a company once it is selected
 */
class CompanyUserDataLoader(
        private val repositoryProvider: RepositoryProvider
) {
    fun load(): Completable {
        val parallelActions = listOf<Completable>(
                // Added actions will be performed simultaneously.
                repositoryProvider.balances().updateDeferred()
        )
        val syncActions = listOf<Completable>()

        val performParallelActions = Completable.merge(parallelActions)
        val performSyncActions = Completable.concat(syncActions)

        return performSyncActions
                .andThen(performParallelActions)
    }
}