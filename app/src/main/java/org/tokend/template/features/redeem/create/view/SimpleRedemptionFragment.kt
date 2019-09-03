package org.tokend.template.features.redeem.create.view

import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.activity_share_qr.*
import kotlinx.android.synthetic.main.appbar.*
import org.jetbrains.anko.textColor
import org.tokend.sdk.utils.extentions.encodeBase64String
import org.tokend.template.R
import org.tokend.template.data.model.BalanceRecord
import org.tokend.template.data.repository.BalancesRepository
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.redeem.create.logic.CreateRedemptionRequestUseCase
import org.tokend.template.features.wallet.view.PlusMinusAmountInputView
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.LoadingIndicatorManager
import java.io.IOException
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import kotlin.math.min

class SimpleRedemptionFragment : ShareRedemptionQrFragment() {
    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = {
                qr_code_image_view.apply {
                    clearAnimation()
                    visibility = View.INVISIBLE
                }
            },
            hideLoading = {}
    )

    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    override lateinit var balanceId: String

    override var amount: BigDecimal = BigDecimal.ZERO

    override var referenceToPoll: String? = null

    private val balance: BalanceRecord
        get() = balancesRepository.itemsList.first { it.id == balanceId }

    private var serializedRequestBase64: String = ""
    override val data: String
        get() = serializedRequestBase64

    private lateinit var amountView: PlusMinusAmountInputView
    private lateinit var availableTextView: TextView

    private val amountChanges = BehaviorSubject.create<BigDecimal>()

    private var defaultAvailableLabelColor: Int = 0
    private val errorColor: Int by lazy {
        ContextCompat.getColor(requireContext(), R.color.error)
    }

    override val shareText: String
        get() = getString(
                R.string.template_redemption_qr_explanation_amount,
                amountFormatter.formatAssetAmount(
                        amountView.amountWrapper.scaledAmount,
                        balance.asset,
                        withAssetName = true
                )
        )

    override fun onInitAllowed() {
        balanceId = arguments?.getString(BALANCE_ID_EXTRA)
                ?: throw  IllegalArgumentException("No $BALANCE_ID_EXTRA specified")

        super.onInitAllowed()

        hideRedundantViews()
        initAmountViews()
        initShareButton()

        subscribeToBalances()
        subscribeToAmountChanges()

        loadingIndicator.show()
    }

    override fun displayData() {
        if (data.isEmpty()) {
            return
        }
        super.displayData()
    }

    override fun shareData() {
        if (data.isEmpty()) {
            return
        }
        super.shareData()
    }

    private fun hideRedundantViews() {
        appbar.visibility = View.GONE
        data_text_view.visibility = View.GONE
        bottom_text_view.visibility = View.GONE
    }

    private fun initAmountViews() {
        availableTextView = TextView(ContextThemeWrapper(requireContext(), R.style.SecondaryText),
                null, R.style.SecondaryText)
        availableTextView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        )
                .apply {
                    topMargin = requireContext().resources
                            .getDimensionPixelOffset(R.dimen.quarter_standard_margin)
                }
        availableTextView.gravity = Gravity.CENTER_HORIZONTAL
        defaultAvailableLabelColor = availableTextView.currentTextColor

        amountView = PlusMinusAmountInputView(requireContext())
        amountView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        )
                .apply {
                    topMargin = requireContext().resources
                            .getDimensionPixelOffset(R.dimen.standard_margin)
                    gravity = Gravity.CENTER_HORIZONTAL
                }
        amountView.amountWrapper.onAmountChanged { _, _ -> onAmountChanged() }
        scrollable_root_layout.addView(amountView)

        preFillAmount()

        scrollable_root_layout.addView(availableTextView)
    }

    private fun initShareButton() {
        val shareButton = ImageButton(
                ContextThemeWrapper(requireContext(), R.style.SecondaryButton),
                null,
                R.style.SecondaryButton
        )
        shareButton.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        )
                .apply {
                    topMargin = requireContext().resources
                            .getDimensionPixelOffset(R.dimen.standard_margin)
                    gravity = Gravity.CENTER
                }
        shareButton.background = null
        shareButton.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_share))
        shareButton.setOnClickListener {
            shareData()
        }

        scrollable_root_layout.addView(shareButton)
    }

    private fun preFillAmount() {
        amountView.amountWrapper.setAmount(
                if (PRE_FILLED_AMOUNT > balance.available)
                    BigDecimal.ZERO
                else
                    PRE_FILLED_AMOUNT
        )
    }

    private fun subscribeToBalances() {
        balancesRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { onBalancesUpdated() }
                .addTo(compositeDisposable)
    }

    private fun onBalancesUpdated() {
        displayBalance()
        updateAmountMaximum()
        updateAmountError()
    }

    private fun displayBalance() {
        availableTextView.text = getString(
                R.string.template_available,
                amountFormatter.formatAssetAmount(
                        balance.available,
                        balance.asset,
                        withAssetCode = false
                )
        )
    }

    private fun updateAmountMaximum() {
        amountView.maxAmount = balance.available
    }

    private fun updateAmountError() {
        if (amountView.amountWrapper.scaledAmount > balance.available) {
            availableTextView.textColor = errorColor
        } else {
            availableTextView.textColor = defaultAvailableLabelColor
        }
    }

    private fun onAmountChanged() {
        amountChanges.onNext(amountView.amountWrapper.scaledAmount)
        updateAmountError()
    }

    private fun subscribeToAmountChanges() {
        amountChanges
                .debounce(400L, TimeUnit.MILLISECONDS)
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe(this::createAndDisplayRedemptionRequest)
                .addTo(compositeDisposable)
    }

    private var redemptionCreationDisposable: Disposable? = null
    private fun createAndDisplayRedemptionRequest(amount: BigDecimal) {
        if (amount > balance.available) {
            return
        }

        redemptionCreationDisposable?.dispose()

        referenceToPoll = null
        stopPolling()

        val assetCode = balance.assetCode

        redemptionCreationDisposable = CreateRedemptionRequestUseCase(
                amount, assetCode, repositoryProvider, walletInfoProvider, accountProvider
        )
                .perform()
                .retryWhen { errors ->
                    errors.filter { it is IOException }.delay(2, TimeUnit.SECONDS)
                }
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .doOnSubscribe { loadingIndicator.show() }
                .doOnEvent { _, _ -> loadingIndicator.hide() }
                .subscribeBy(
                        onSuccess = this::onRedemptionRequestCreated,
                        onError = {
                            errorHandlerFactory.getDefault().handle(it)
                        }
                )
                .addTo(compositeDisposable)
    }

    private fun onRedemptionRequestCreated(result: CreateRedemptionRequestUseCase.Result) {
        serializedRequestBase64 = result
                .request
                .serialize(result.networkParams)
                .encodeBase64String()

        referenceToPoll = result.request.salt.toString()
        amount = result.request.amount

        isAccepted = false

        startPollingIfNeeded()
        displayData()
    }

    override fun onRedemptionAccepted() {
        super.onRedemptionAccepted()
        overlayQrCodeCheck()
    }

    private fun overlayQrCodeCheck() {
        val source = (qr_code_image_view.drawable as? BitmapDrawable)
                ?.bitmap
                ?: return

        val canvas = Canvas(source)

        canvas.drawColor(ContextCompat.getColor(requireContext(), R.color.white_almost))

        val checkDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_circle_ok)!!
        val sourceSize = min(source.width, source.height)
        val sourceRadius = sourceSize / 2
        val checkSize = min(source.width, source.height) / 3
        val checkRadius = checkSize / 2
        checkDrawable.setBounds(
                sourceRadius - checkRadius,
                sourceRadius - checkRadius,
                sourceRadius + checkRadius,
                sourceRadius + checkRadius
        )
        checkDrawable.draw(canvas)

        qr_code_image_view.setImageBitmap(source)
    }

    companion object {
        private val PRE_FILLED_AMOUNT = BigDecimal.ONE
        private const val BALANCE_ID_EXTRA = "balance_id"

        fun getBundle(balanceId: String) = Bundle().apply {
            putString(BALANCE_ID_EXTRA, balanceId)
        }

        fun newInstance(bundle: Bundle): SimpleRedemptionFragment = SimpleRedemptionFragment()
                .withArguments(bundle)
    }
}