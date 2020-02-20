package org.tokend.template.features.kyc.view

import android.os.Bundle
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_loading_data.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.kyc.model.KycRequestState
import org.tokend.template.features.kyc.storage.KycRequestStateRepository
import org.tokend.template.util.IntervalPoller
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.navigation.Navigator
import java.util.concurrent.TimeUnit

class WaitForKycApprovalActivity : BaseActivity() {
    private val kycRequestStateRepository: KycRequestStateRepository
        get() = repositoryProvider.kycRequestState()

    private val isKycRequestApproved: Boolean
        get() = kycRequestStateRepository.item is KycRequestState.Submitted.Approved<*>

    private var pollingDisposable: Disposable? = null

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_loading_data)
        loading_message_text_view.setText(R.string.waiting_for_kyc_approval_message)
    }

    private fun startKycStatePolling() {
        pollingDisposable = IntervalPoller(
                POLL_INTERVAL_S,
                TimeUnit.SECONDS,
                deferredDataSource = Single.defer {
                    kycRequestStateRepository
                            .updateDeferred()
                            .toSingleDefault(true)
                            .map {
                                if (!isKycRequestApproved) {
                                    throw IllegalStateException("KYC request is not approved yet")
                                }
                            }
                }
        )
                .asSingle()
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .subscribeBy(
                        onSuccess = { onKycApproved() },
                        onError = this::onCheckError
                )
                .addTo(compositeDisposable)
    }

    private fun stopPolling() {
        pollingDisposable?.dispose()
    }

    override fun onResume() {
        super.onResume()
        startKycStatePolling()
    }

    override fun onPause() {
        super.onPause()
        stopPolling()
    }

    private fun onKycApproved() {
        toastManager.short(R.string.account_setup_completed)
        Navigator.from(this).toClientMainActivity(true)
    }

    private fun onCheckError(error: Throwable) {
        errorHandlerFactory.getDefault().handle(error)
        finish()
    }

    companion object {
        private const val POLL_INTERVAL_S = 2L
    }
}
