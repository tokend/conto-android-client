package org.tokend.template.features.nfcpayment.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.Toolbar
import android.view.Gravity
import android.widget.ImageView
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.toolbar.*
import org.jetbrains.anko.dip
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.nfcpayment.logic.NfcPaymentService
import org.tokend.template.features.nfcpayment.model.PosPaymentRequest
import org.tokend.template.features.nfcpayment.model.RawPosPaymentRequest
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.UserFlowFragmentDisplayer
import java.util.concurrent.CancellationException

class NfcPaymentActivity : BaseActivity() {
    override val allowUnauthorized = true

    private val fragmentDisplayer =
            UserFlowFragmentDisplayer(this, R.id.fragment_container_layout)

    private lateinit var rawPaymentRequest: RawPosPaymentRequest
    private lateinit var paymentRequest: PosPaymentRequest

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.fragment_user_flow)

        val rawPaymentRequest = intent.getSerializableExtra(RAW_PAYMENT_REQUEST_EXTRA)
                as? RawPosPaymentRequest
        if (rawPaymentRequest == null) {
            finishWithMissingArgError(RAW_PAYMENT_REQUEST_EXTRA)
            return
        }
        this.rawPaymentRequest = rawPaymentRequest

        initToolbar()

        toPaymentRequestLoading()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        title = ""

        toolbar.addView(
                ImageView(this).apply {
                    layoutParams = Toolbar.LayoutParams(
                            Toolbar.LayoutParams.WRAP_CONTENT,
                            dip(24)
                    ).apply {
                        gravity = Gravity.CENTER
                    }

                    setImageDrawable(
                            ContextCompat.getDrawable(
                                    this@NfcPaymentActivity,
                                    R.mipmap.product_logo)
                    )

                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
        )
    }

    private fun toPaymentRequestLoading() {
        val fragment = LoadPosPaymentRequestFragment.newInstance(
                LoadPosPaymentRequestFragment.getBundle(rawPaymentRequest)
        )

        fragment
                .resultSingle
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .subscribeBy(
                        onSuccess = this::onPaymentRequestLoaded,
                        onError = this::onPaymentRequestLoadingError
                )
                .addTo(compositeDisposable)

        fragmentDisplayer.display(fragment, "request_loading", true)
    }

    private fun onPaymentRequestLoaded(paymentRequest: PosPaymentRequest) {
        this.paymentRequest = paymentRequest
        toPaymentRequestConfirmation()
    }

    private fun onPaymentRequestLoadingError(error: Throwable) {
        errorHandlerFactory.getDefault().handle(error)
        finish()
    }

    private fun toPaymentRequestConfirmation() {
        val fragment = PosPaymentRequestConfirmationFragment.newInstance(
                PosPaymentRequestConfirmationFragment.getBundle(paymentRequest)
        )

        fragment
                .resultCompletable
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .subscribeBy(
                        onComplete = this::onPaymentRequestConfirmed,
                        onError = this::onPaymentRequestConfirmationError
                )

        fragmentDisplayer.display(fragment, "confirmation", true)
    }

    private fun onPaymentRequestConfirmed() {
        toPaymentBroadcast()
    }

    private fun onPaymentRequestConfirmationError(error: Throwable) {
        if (error !is CancellationException) {
            errorHandlerFactory.getDefault().handle(error)
        }
        finish()
    }

    private fun toPaymentBroadcast() {
        val fragment = BroadcastPosPaymentFragment.newInstance(
                BroadcastPosPaymentFragment.getBundle(paymentRequest)
        )

        fragmentDisplayer.display(fragment, "broadcast", true)
    }

    override fun onBackPressed() {
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        NfcPaymentService.clearPendingTransactions()
    }

    companion object {
        private const val RAW_PAYMENT_REQUEST_EXTRA = "raw_payment_request"

        fun getBundle(request: RawPosPaymentRequest) = Bundle().apply {
            putSerializable(RAW_PAYMENT_REQUEST_EXTRA, request)
        }
    }
}