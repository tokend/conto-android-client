package org.tokend.template.features.nfcpayment.view

import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.fragment_user_flow.*
import kotlinx.android.synthetic.main.toolbar.*
import org.jetbrains.anko.dip
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.activities.OnBackPressedListener
import org.tokend.template.features.assets.buy.marketplace.logic.MarketplaceOfferLoader
import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord
import org.tokend.template.features.nfcpayment.logic.NfcPaymentService
import org.tokend.template.features.nfcpayment.logic.PosPaymentRequestFulfiller
import org.tokend.template.features.nfcpayment.model.FulfilledPosPaymentRequest
import org.tokend.template.features.nfcpayment.model.PosPaymentRequest
import org.tokend.template.features.nfcpayment.model.RawPosPaymentRequest
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.navigation.Navigator
import org.tokend.template.view.util.UserFlowFragmentDisplayer
import java.math.BigDecimal
import java.util.concurrent.CancellationException

class NfcPaymentActivity : BaseActivity() {
    override val allowUnauthorized = true

    private val fragmentDisplayer =
            UserFlowFragmentDisplayer(this, R.id.fragment_container_layout)

    private lateinit var rawPaymentRequest: RawPosPaymentRequest
    private lateinit var paymentRequest: PosPaymentRequest
    private lateinit var fulfilledRequest: FulfilledPosPaymentRequest

    private var onBackPressedListener: OnBackPressedListener? = null

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.fragment_user_flow)

        val rawPaymentRequest = intent.getSerializableExtra(RAW_PAYMENT_REQUEST_EXTRA)
                as? RawPosPaymentRequest
        if (rawPaymentRequest == null) {
            finishWithMissingArgError(RAW_PAYMENT_REQUEST_EXTRA)
            return
        }
        this.rawPaymentRequest = rawPaymentRequest

        if (!canProcessPaymentRequest()) {
            Navigator.from(this).toSignIn(true)
            return
        }

        initToolbar()
        initSwipeRefresh()

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

        toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.ic_close)
        toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun initSwipeRefresh() {
        swipe_refresh.isEnabled = false
    }

    private fun canProcessPaymentRequest(): Boolean {
        return credentialsPersistence.hasCredentials()
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
                        onError = this::onFatalError
                )
                .addTo(compositeDisposable)

        fragmentDisplayer.display(fragment, "request_loading", true)
    }

    private fun onPaymentRequestLoaded(paymentRequest: PosPaymentRequest) {
        this.paymentRequest = paymentRequest
        confirmPaymentRequestIfNeeded()
    }

    private fun confirmPaymentRequestIfNeeded() {
        if (nfcPaymentConfirmationManager.isConfirmationRequired) {
            toPaymentRequestConfirmation()
        } else {
            onPaymentRequestConfirmed()
        }
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
        unlockIfNeeded()
    }

    private fun onPaymentRequestConfirmationError(error: Throwable) {
        if (error !is CancellationException) {
            errorHandlerFactory.getDefault().handle(error)
        }
        finish()
    }

    private fun unlockIfNeeded() {
        if (accountProvider.getAccount() != null) {
            onUnlocked()
        } else {
            toUnlock()
        }
    }

    private fun toUnlock() {
        val fragment = UnlockForPosPaymentFragment.newInstance()

        onBackPressedListener = fragment

        fragment
                .resultCompletable
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .subscribeBy(
                        onComplete = this::onUnlocked,
                        onError = this::onFatalError
                )
                .addTo(compositeDisposable)

        fragmentDisplayer.display(fragment, "unlock", true)
    }

    private fun onUnlocked() {
        toPaymentRequestFulfillment()
    }

    private fun toPaymentRequestFulfillment() {
        // Let it be displayed as a part of unlock.

        PosPaymentRequestFulfiller(
                repositoryProvider,
                walletInfoProvider,
                apiProvider,
                connectionStateUtil::isOnline
        )
                .fulfill(paymentRequest)
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .subscribeBy(
                        onSuccess = this::onPaymentRequestFulfilled,
                        onError = this::onPaymentRequestFulfillmentError
                )
                .addTo(compositeDisposable)
    }

    private fun onPaymentRequestFulfilled(request: FulfilledPosPaymentRequest) {
        this.fulfilledRequest = request
        toPaymentBroadcast()
    }

    private fun onPaymentRequestFulfillmentError(error: Throwable) {
        when (error) {
            is PosPaymentRequestFulfiller.InsufficientOrMissingBalanceException ->
                // If there is no balance or it's insufficient suggest user
                // to buy the asset.
                toMarketplaceOfferLoading(error.missingAmount)
            else ->
                onFatalError(error)
        }
    }

    private fun toMarketplaceOfferLoading(requiredAmount: BigDecimal) {
        // Let it be displayed as a part of unlock.

        MarketplaceOfferLoader(repositoryProvider.marketplaceOffers(null))
                .load(paymentRequest.asset.code)
                .flatMap { offer ->
                    repositoryProvider
                            .balances()
                            .updateIfNotFreshDeferred()
                            .toSingleDefault(offer)
                            .toMaybe()
                }
                .compose(ObservableTransformers.defaultSchedulersMaybe())
                .subscribeBy(
                        onSuccess = { onMarketplaceOfferLoaded(it, requiredAmount) },
                        onComplete = this::onNoMarketplaceOfferAvailable,
                        onError = this::onFatalError
                )
                .addTo(compositeDisposable)
    }

    private fun onMarketplaceOfferLoaded(offer: MarketplaceOfferRecord,
                                         requiredAmount: BigDecimal) {
        Navigator.from(this).openMarketplaceBuy(offer, requiredAmount)
        finish()
    }

    private fun onNoMarketplaceOfferAvailable() {
        toastManager.long(R.string.error_asset_cant_be_bought_now)
        finish()
    }

    private fun toPaymentBroadcast() {
        val fragment = BroadcastPosPaymentFragment.newInstance(
                BroadcastPosPaymentFragment.getBundle(fulfilledRequest)
        )

        fragmentDisplayer.display(fragment, "broadcast", true)
    }

    private fun onFatalError(error: Throwable) {
        errorHandlerFactory.getDefault().handle(error)
        finish()
    }

    override fun onBackPressed() {
        if (onBackPressedListener?.onBackPressed() != false) {
            finish()
        }
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