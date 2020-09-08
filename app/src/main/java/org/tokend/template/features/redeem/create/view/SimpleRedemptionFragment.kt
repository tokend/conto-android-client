package org.tokend.template.features.redeem.create.view

import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
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
import org.jetbrains.anko.textColor
import org.tokend.sdk.utils.BigDecimalUtil
import org.tokend.sdk.utils.extentions.encodeBase64String
import org.tokend.template.R
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.balances.model.BalanceRecord
import org.tokend.template.features.balances.storage.BalancesRepository
import org.tokend.template.features.history.model.BalanceChange
import org.tokend.template.features.redeem.create.logic.CreateRedemptionRequestUseCase
import org.tokend.template.features.redeem.logic.NfcRedemptionService
import org.tokend.template.features.redeem.model.RedemptionRequest
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

    private val balance: BalanceRecord
        get() = balancesRepository.itemsList.first { it.id == balanceId }

    private var currentRequest: RedemptionRequest? = null
    private var currentRequestSerialized: ByteArray = byteArrayOf()
    override val data: String
        get() = currentRequestSerialized.encodeBase64String()
    override val referenceToPoll: String?
        get() = currentRequest?.salt?.toString()

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

    override val title: String
        get() = balance.asset.run { name ?: code }

    private val requiredAmount: BigDecimal by lazy {
        arguments?.getString(AMOUNT_EXTRA).let { BigDecimalUtil.valueOf(it, DEFAULT_AMOUNT) }
    }

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
        data_text_view.visibility = View.GONE
        bottom_text_view.visibility = View.GONE
    }

    private fun initAmountViews() {
        // Prevent soft keyboard pop up.
        qr_code_layout.isFocusable = true
        qr_code_layout.isFocusableInTouchMode = true
        qr_code_layout.requestFocus()

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
        amountView.amountWrapper.maxPlacesAfterComa = balance.asset.trailingDigits
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
        amountView.amountWrapper.setAmount(requiredAmount.min(balance.available))
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

        currentRequest = null
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
        currentRequest = result.request
        currentRequestSerialized = result
                .request
                .serialize(result.networkParams)

        isAccepted = false

        startPollingIfNeeded()
        displayData()
    }

    override fun onRedemptionAccepted(balanceChange: BalanceChange) {
        super.onRedemptionAccepted(balanceChange)
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

    override fun startPollingIfNeeded() {
        if (currentRequest?.amount?.signum()?.let { it > 0 } == true) {
            super.startPollingIfNeeded()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                currentRequestSerialized
                        .takeIf(ByteArray::isNotEmpty)
                        ?.also(NfcRedemptionService.Companion::broadcast)
            }
        }
    }

    override fun stopPolling() {
        super.stopPolling()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            NfcRedemptionService.cancelBroadcast()
        }
    }

    companion object {
        val ID = "simple_redemption".hashCode().toLong()
        private val DEFAULT_AMOUNT = BigDecimal.ONE
        private const val BALANCE_ID_EXTRA = "balance_id"
        private const val AMOUNT_EXTRA = "amount"

        fun getBundle(balanceId: String,
                      amount: BigDecimal? = null) = Bundle().apply {
            putString(BALANCE_ID_EXTRA, balanceId)
            if (amount != null) {
                putString(AMOUNT_EXTRA, BigDecimalUtil.toPlainString(amount))
            }
        }

        fun newInstance(bundle: Bundle): SimpleRedemptionFragment = SimpleRedemptionFragment()
                .withArguments(bundle)
    }
}