package org.tokend.template.features.kyc.view

import android.os.Bundle
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_loading_data.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.kyc.model.KycState
import org.tokend.template.features.kyc.storage.KycStateRepository
import org.tokend.template.util.IntervalPoller
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import java.util.concurrent.TimeUnit

class WaitForKycApprovalActivity : BaseActivity() {
    private val kycStateRepository: KycStateRepository
        get() = repositoryProvider.kycState()

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
                    kycStateRepository
                            .updateDeferred()
                            .toSingleDefault(true)
                            .map {
                                val isApproved =
                                        kycStateRepository.item is KycState.Submitted.Approved<*>

                                if (!isApproved) {
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
        Navigator.from(this).toCompaniesActivity()
    }

    private fun onCheckError(error: Throwable) {
        errorHandlerFactory.getDefault().handle(error)
        finish()
    }

    companion object {
        private const val POLL_INTERVAL_S = 2L
    }
}
