package org.tokend.template.features.signin.logic

import io.reactivex.Completable
import org.tokend.sdk.utils.extentions.isUnauthorized
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.features.signin.model.ForcedAccountType
import retrofit2.HttpException

class PostSignInManager(
        private val repositoryProvider: RepositoryProvider,
        private val forcedAccountType: ForcedAccountType? = null
) {
    class AuthMismatchException : Exception()

    /**
     * Updates all important repositories.
     */
    fun doPostSignIn(): Completable {
        val parallelActions = listOf<Completable>(
                // Added actions will be performed simultaneously.
                repositoryProvider.account().updateDeferred(),
                repositoryProvider.kycState().updateDeferred()
                        .doOnComplete {
                            repositoryProvider.kycState().forcedType = forcedAccountType
                        },
                repositoryProvider.balances().updateDeferred()
        )
        val syncActions = listOf<Completable>(
                // Added actions will be performed on after another in
                // provided order.
        )

        val performParallelActions = Completable.merge(parallelActions)
        val performSyncActions = Completable.concat(syncActions)

        repositoryProvider.tfaFactors().invalidate()

        return performSyncActions
                .andThen(performParallelActions)
                .onErrorResumeNext {
                    if (it is HttpException && it.isUnauthorized())
                        Completable.error(AuthMismatchException())
                    else
                        Completable.error(it)
                }
    }
}