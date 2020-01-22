package org.tokend.template.features.signin.logic

import io.reactivex.Completable
import org.tokend.sdk.utils.extentions.isUnauthorized
import org.tokend.template.data.model.AccountRecord
import org.tokend.template.di.providers.AccountProvider
import org.tokend.template.di.providers.ApiProvider
import org.tokend.template.di.providers.RepositoryProvider
import org.tokend.template.di.providers.WalletInfoProvider
import org.tokend.template.features.kyc.model.KycForm
import org.tokend.template.features.recovery.logic.SubmitKycRecoveryRequestUseCase
import org.tokend.template.features.signin.model.ForcedAccountType
import org.tokend.template.logic.TxManager
import retrofit2.HttpException

class PostSignInManager(
        private val apiProvider: ApiProvider,
        private val accountProvider: AccountProvider,
        private val walletInfoProvider: WalletInfoProvider,
        private val repositoryProvider: RepositoryProvider,
        private val forcedAccountType: ForcedAccountType? = null,
        private val connectionStateProvider: (() -> Boolean)? = null
) {
    class AuthMismatchException : Exception()

    private val isOnline: Boolean
        get() = connectionStateProvider?.invoke() ?: true

    /**
     * Updates all important repositories.
     */
    fun doPostSignIn(): Completable {
        val parallelActions = listOf<Completable>(
                // Added actions will be performed simultaneously.
                repositoryProvider.kycState()
                        .run {
                            if (isOnline)
                                updateDeferred()
                            else
                                ensureData().doOnComplete {
                                    if (!isFresh) {
                                        update()
                                    }
                                }
                        }
                        .doOnComplete {
                            repositoryProvider.kycState().forcedType = forcedAccountType
                        },
                repositoryProvider.balances().ensureData()
        )
        val syncActions = listOf<Completable>(
                // Added actions will be performed on after another in
                // provided order.
                repositoryProvider.account()
                        .run {
                            repositoryProvider.account()
                                    .run {
                                        if (isOnline)
                                            updateDeferred()
                                        else
                                            ensureData()
                                    }
                        }
                        .andThen(Completable.defer { sendEmptyKycRecoveryRequestIfNeeded() })
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

    private fun sendEmptyKycRecoveryRequestIfNeeded(): Completable {
        if (!isOnline || repositoryProvider.account().item!!.kycRecoveryStatus
                != AccountRecord.KycRecoveryStatus.INITIATED) {
            return Completable.complete()
        }

        return SubmitKycRecoveryRequestUseCase(
                form = KycForm.Empty,
                apiProvider = apiProvider,
                repositoryProvider = repositoryProvider,
                accountProvider = accountProvider,
                walletInfoProvider = walletInfoProvider,
                txManager = TxManager(apiProvider)
        )
                .perform()
    }
}