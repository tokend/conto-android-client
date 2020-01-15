package org.tokend.template.features.wallet.view

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v4.widget.NestedScrollView
import android.support.v7.view.ContextThemeWrapper
import android.support.v7.widget.GridLayout
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.TextView
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_simple_balance_details.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.toolbar.*
import org.jetbrains.anko.childrenSequence
import org.jetbrains.anko.textColor
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.BalanceRecord
import org.tokend.template.data.repository.BalancesRepository
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.AnimationUtil
import org.tokend.template.view.util.CircleLogoUtil
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.LoadingIndicatorManager
import java.math.BigDecimal
import kotlin.math.roundToInt

class SimpleBalanceDetailsActivity : BaseActivity() {
    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    private lateinit var balanceId: String

    private val balance: BalanceRecord
        get() = balancesRepository.itemsList.first { it.id == balanceId }

    private var defaultAmountInputColor: Int = 0
    private val errorColor: Int by lazy {
        ContextCompat.getColor(this, R.color.error)
    }

    private var canSend: Boolean = false
        set(value) {
            field = value
            send_button.isEnabled = value
        }

    private var canRedeem: Boolean = false
        set(value) {
            field = value
            redeem_button.isEnabled = value
        }

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_simple_balance_details)

        val balanceId = intent.getStringExtra(BALANCE_ID_EXTRA)
        if (balanceId == null) {
            finishWithMissingArgError(BALANCE_ID_EXTRA)
            return
        }
        this.balanceId = balanceId

        initToolbar()
        initSwipeRefresh()
        initFields()
        initButtons()
        initCompanyBadge()
        initAssetLogo()
        initActions()

        subscribeToBalances()
    }

    // region Init
    private fun initToolbar() {
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { onBackPressed() }
        toolbar.setSubtitleTextAppearance(this, R.style.ToolbarSubtitleAppearance)

        initToolbarAnimations()
    }

    private fun initToolbarAnimations() {
        val elevationScrollListener =
                ElevationUtil.initScrollElevation(scroll_view, appbar_elevation_view)

        // Force toolbar to create title and subtitle views.
        toolbar.title = "*"
        toolbar.subtitle = "*"

        val fadingToolbarViews = toolbar
                .childrenSequence()
                .filter { it is TextView }

        val fadeDuration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

        fadingToolbarViews.forEach {
            it.visibility = View.INVISIBLE
        }

        val location = IntArray(2)
        var wasVisible = true
        scroll_view.setOnScrollChangeListener { a: NestedScrollView?,
                                                b: Int,
                                                c: Int,
                                                d: Int,
                                                e: Int ->
            elevationScrollListener.onScrollChange(a, b, c, d, e)

            val cutPoint = location.apply(appbar_elevation_view::getLocationInWindow)[1]
            val availableTop = location.apply(available_text_view::getLocationInWindow)[1]

            val isVisible = availableTop + available_text_view.height / 2 > cutPoint

            if (isVisible != wasVisible) {
                wasVisible = isVisible
                fadingToolbarViews.forEach {
                    if (!isVisible) {
                        AnimationUtil.fadeInView(it, fadeDuration)
                    } else {
                        AnimationUtil.fadeOutView(it, fadeDuration)
                    }
                }
            }
        }
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent))
        swipe_refresh.setOnRefreshListener {
            update(force = true)
        }
    }

    private fun initFields() {
        defaultAmountInputColor = amount_view.editText.currentTextColor

        amount_view.amountWrapper.onAmountChanged { _, _ ->
            onAmountChanged()
        }
        amount_view.amountWrapper.maxPlacesAfterComa = balance.asset.trailingDigits

        amount_card_content_layout.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            val width = right - left
            val oldWidth = oldRight - oldLeft
            if (width != oldWidth) {
                // Max amount input field width is 45% of the container width.
                amount_view.editText.maxWidth = (width * 0.45).roundToInt()
            }
        }

        preFillAmount()
    }

    private fun initButtons() {
        val tintColorStateList =
                ContextCompat.getColorStateList(this, R.color.color_dialog_button_content)
        send_button.setCompoundDrawablesRelativeWithIntrinsicBounds(
                ContextCompat.getDrawable(this, R.drawable.ic_send_fab)?.also {
                    DrawableCompat.setTintList(it, tintColorStateList)
                },
                null, null, null
        )
        send_button.setOnClickListener {
            if (canSend) {
                openSend()
            }
        }

        redeem_button.setCompoundDrawablesRelativeWithIntrinsicBounds(
                ContextCompat.getDrawable(this, R.drawable.ic_qr_code)?.also {
                    DrawableCompat.setTintList(it, tintColorStateList)
                },
                null, null, null
        )
        redeem_button.setOnClickListener {
            if (canRedeem) {
                openRedemption()
            }
        }

        asset_logo_image_view.setOnClickListener {
            openAssetDetails()
        }

        company_badge.setOnClickListener {
            openCompanyDetails()
        }
    }

    private fun initCompanyBadge() {
        val company = balance.company
        if (company != null) {
            company_badge.visibility = View.VISIBLE
            company_name_text_view.text = company.name
            CircleLogoUtil.setLogo(company_logo_image_view, company.name, company.logoUrl)
        } else {
            company_badge.visibility = View.GONE
        }
    }

    private fun initAssetLogo() {
        CircleLogoUtil.setAssetLogo(asset_logo_image_view, balance.asset)
    }

    private fun initActions() {
        val themedContext = ContextThemeWrapper(this, R.style.BalanceActionButton_Secondary)
        val tintColorStateList =
                ContextCompat.getColorStateList(this, R.color.color_dialog_button_content)

        val addAction = { icon: Int, text: Int, clickListener: () -> Unit ->
            actions_layout.addView(
                    Button(themedContext, null, R.style.BalanceActionButton_Secondary).apply {
                        layoutParams = GridLayout.LayoutParams().apply {
                            width = 0
                            columnSpec = GridLayout.spec(GridLayout.UNDEFINED,1f)
                        }
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        setCompoundDrawablesRelativeWithIntrinsicBounds(
                                ContextCompat.getDrawable(themedContext, icon)?.also {
                                    DrawableCompat.setTintList(it, tintColorStateList)
                                },
                                null, null, null
                        )
                        setText(text)
                        setOnClickListener { clickListener() }
                    }
            )
        }

        actions_layout.removeAllViews()
        addAction(R.drawable.ic_history, R.string.operations_history_short, this::openHistory)
        addAction(R.drawable.ic_information, R.string.details, this::openAssetDetails)
    }
    // endregion

    // region Amount
    private fun preFillAmount() {
        amount_view.amountWrapper.setAmount(PRE_FILLED_AMOUNT.min(balance.available))
    }

    private fun onAmountChanged() {
        updateAmountError()
        updateAmountActionsAvailability()
    }

    private fun updateAmountInputLimitations() {
        amount_view.maxAmount = balance.available
    }

    private fun updateAmountError() {
        if (amount_view.amountWrapper.scaledAmount > balance.available) {
            amount_view.editText.textColor = errorColor
        } else {
            amount_view.editText.textColor = defaultAmountInputColor
        }
    }
    // endregion

    private fun updateAmountActionsAvailability() {
        val amount = amount_view.amountWrapper.scaledAmount
        canSend = amount.signum() > 0 && amount <= balance.available
        canRedeem = canSend
    }

    private fun subscribeToBalances() {
        balancesRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { onBalancesUpdated() }
                .addTo(compositeDisposable)

        balancesRepository
                .loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loadingIndicator.setLoading(it, "balances") }
                .addTo(compositeDisposable)

        balancesRepository
                .errorsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { errorHandlerFactory.getDefault().handle(it) }
                .addTo(compositeDisposable)
    }

    private fun update(force: Boolean = false) {
        if (!force) {
            balancesRepository.updateIfNotFresh()
        } else {
            balancesRepository.update()
        }
    }

    private fun onBalancesUpdated() {
        displayBalance()
        updateAmountInputLimitations()
        updateAmountError()
        updateAmountActionsAvailability()
    }

    private fun displayBalance() {
        available_text_view.text = amountFormatter.formatAssetAmount(
                balance.available,
                balance.asset,
                withAssetCode = false, withAssetName = false
        )
        asset_name_text_view.text = balance.asset.run { name ?: code }

        toolbar.title = available_text_view.text
        toolbar.subtitle = asset_name_text_view.text
    }

    private fun openSend() {
        Navigator.from(this).openSend(
                asset = balance.assetCode,
                amount = amount_view.amountWrapper.scaledAmount,
                requestCode = SEND_REQUEST
        )
    }

    private fun openRedemption() {
        Navigator.from(this).openSimpleRedemptionCreation(
                balanceId = balanceId,
                amount = amount_view.amountWrapper.scaledAmount,
                requestCode = REDEMPTION_REQUEST
        )
    }

    private fun openAssetDetails() {
        Navigator.from(this).openAssetDetails(balance.asset)
    }

    private fun openCompanyDetails() {
        balance.company?.also(Navigator.from(this)::openCompanyDetails)
    }

    private fun openHistory() {
        Navigator.from(this).openAssetMovements(balanceId)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                SEND_REQUEST,
                REDEMPTION_REQUEST -> preFillAmount()
            }
        }
    }

    companion object {
        private val PRE_FILLED_AMOUNT = BigDecimal.ONE
        private const val BALANCE_ID_EXTRA = "balance_id"
        private val SEND_REQUEST = "send".hashCode() and 0xffff
        private val REDEMPTION_REQUEST = "redemption".hashCode() and 0xffff

        fun getBundle(balanceId: String) = Bundle().apply {
            putString(BALANCE_ID_EXTRA, balanceId)
        }
    }
}