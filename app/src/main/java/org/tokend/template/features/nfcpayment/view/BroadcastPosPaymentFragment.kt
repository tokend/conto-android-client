package org.tokend.template.features.nfcpayment.view

import android.animation.ValueAnimator
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.fragment_broadcast_pos_payment.*
import org.tokend.template.R
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.nfcpayment.logic.CreateAndBroadcastPosPaymentTxUseCase
import org.tokend.template.features.nfcpayment.logic.NfcPaymentService
import org.tokend.template.features.nfcpayment.model.FulfilledPosPaymentRequest
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.util.ObservableTransformers
import java.util.concurrent.TimeUnit

class BroadcastPosPaymentFragment : BaseFragment() {
    private lateinit var fulfilledRequest: FulfilledPosPaymentRequest

    private val animationsDisposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_broadcast_pos_payment, container, false)
    }

    override fun onInitAllowed() {
        fulfilledRequest = arguments?.getSerializable(FULFILLED_REQUEST_EXTRA)
                as? FulfilledPosPaymentRequest
                ?: throw IllegalArgumentException("No $FULFILLED_REQUEST_EXTRA specified")

        initAnimations()

        createAndBroadcastPayment()
    }

    private fun initAnimations() {
        Observable.interval(500, 2500, TimeUnit.MILLISECONDS)
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    (circles_image_view.drawable as? Animatable)?.start()
                }
                .addTo(animationsDisposable)

        animationsDisposable.addTo(compositeDisposable)
    }

    private fun createAndBroadcastPayment() {
        CreateAndBroadcastPosPaymentTxUseCase(
                paymentRequest = fulfilledRequest,
                accountProvider = accountProvider,
                walletInfoProvider = walletInfoProvider,
                repositoryProvider = repositoryProvider,
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
        stopAnimations()
        expandStatus()
        displayStatus(isSuccessful = true)
    }

    private fun onBroadcastError(error: Throwable) {
        error.printStackTrace()
        stopAnimations()
        expandStatus()
        displayStatus(isSuccessful = false)
    }

    private fun stopAnimations() {
        animationsDisposable.dispose()
    }

    private fun displayStatus(isSuccessful: Boolean) {
        title_text_view.setText(
                if (isSuccessful)
                    R.string.nfc_payment_accepted
                else
                    R.string.try_again
        )

        phone_image_view.setImageDrawable(ContextCompat.getDrawable(
                requireContext(),
                if (isSuccessful)
                    R.drawable.ic_check_circle_ok
                else
                    R.drawable.ic_close_circle_error
        ))

        if (isSuccessful) {
            amount_text_view.text = amountFormatter.formatAssetAmount(
                    fulfilledRequest.amount,
                    fulfilledRequest.asset,
                    withAssetName = true
            )
            amount_text_view.visibility = View.VISIBLE
        } else {
            amount_text_view.visibility = View.GONE
        }
    }

    private fun expandStatus() {
        val targetLayoutParams = circles_image_view.layoutParams as ConstraintLayout.LayoutParams

        val animator = ValueAnimator.ofFloat(1.1f, 1.4f).apply {
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
        private const val FULFILLED_REQUEST_EXTRA = "fulfilled_request"

        fun getBundle(paymentRequest: FulfilledPosPaymentRequest) = Bundle().apply {
            putSerializable(FULFILLED_REQUEST_EXTRA, paymentRequest)
        }

        fun newInstance(bundle: Bundle): BroadcastPosPaymentFragment =
                BroadcastPosPaymentFragment().withArguments(bundle)
    }
}