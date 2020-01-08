package org.tokend.template.features.nfcpayment.view

import android.animation.ValueAnimator
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import io.reactivex.Observable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.fragment_broadcast_pos_payment.*
import org.tokend.template.R
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.nfcpayment.logic.CreateAndBroadcastPosPaymentUseCase
import org.tokend.template.features.nfcpayment.logic.NfcPaymentService
import org.tokend.template.features.nfcpayment.model.PosPaymentRequest
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.AnimationUtil
import java.util.concurrent.TimeUnit

class BroadcastPosPaymentFragment : BaseFragment() {
    private lateinit var paymentRequest: PosPaymentRequest

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_broadcast_pos_payment, container, false)
    }

    override fun onInitAllowed() {
        paymentRequest = arguments?.getSerializable(PAYMENT_REQUEST_EXTRA) as? PosPaymentRequest
                ?: throw IllegalArgumentException("No $PAYMENT_REQUEST_EXTRA specified")

        initAnimations()

        createAndBroadcastPayment()
    }

    private fun initAnimations() {
        Observable.interval(500, 2500, TimeUnit.MILLISECONDS)
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    (circles_image_view.drawable as? Animatable)?.start()
                }
                .addTo(compositeDisposable)
    }

    private fun createAndBroadcastPayment() {
        CreateAndBroadcastPosPaymentUseCase(
                paymentRequest = paymentRequest,
                repositoryProvider = repositoryProvider,
                accountProvider = accountProvider,
                walletInfoProvider = walletInfoProvider,
                transactionBroadcaster = NfcPaymentService.Companion
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .subscribeBy(
                        onComplete = this::onSuccessfulBroadcast,
                        onError = this::onBroadcastError
                )
                .addTo(compositeDisposable)
    }

    private fun onSuccessfulBroadcast() {
        expandStatus()
        displayStatus(isSuccessful = true)
    }

    private fun onBroadcastError(error: Throwable) {
        error.printStackTrace()
        expandStatus()
        displayStatus(isSuccessful = false)
    }

    private fun displayStatus(isSuccessful: Boolean) {
        title_text_view.setText(
                if (isSuccessful)
                    R.string.nfc_payment_accepted
                else
                    R.string.try_again
        )

        status_image_view.setImageDrawable(ContextCompat.getDrawable(
                requireContext(),
                if (isSuccessful)
                    R.drawable.ic_check_circle_ok
                else
                    R.drawable.ic_close_circle_error
        ))
        AnimationUtil.fadeInView(status_image_view)
    }

    private fun expandStatus() {
        val targetLayoutParams = circles_image_view.layoutParams as ConstraintLayout.LayoutParams

        val animator = ValueAnimator.ofFloat(1.1f, 1.6f).apply {
            interpolator = AccelerateInterpolator()
            duration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
            addUpdateListener {
                val value = animatedValue as Float
                targetLayoutParams.dimensionRatio = "$value:1"
                circles_image_view.layoutParams = targetLayoutParams
            }
        }

        animator.start()
    }

    companion object {
        private const val PAYMENT_REQUEST_EXTRA = "payment_request"

        fun getBundle(paymentRequest: PosPaymentRequest) = Bundle().apply {
            putSerializable(PAYMENT_REQUEST_EXTRA, paymentRequest)
        }

        fun newInstance(bundle: Bundle): BroadcastPosPaymentFragment =
                BroadcastPosPaymentFragment().withArguments(bundle)
    }
}