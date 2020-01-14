package org.tokend.template.features.wallet.view

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.View
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_new_balance_details.*
import kotlinx.android.synthetic.main.toolbar.*
import org.jetbrains.anko.textColor
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.BalanceRecord
import org.tokend.template.data.repository.BalancesRepository
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.CircleLogoUtil
import org.tokend.template.view.util.LoadingIndicatorManager
import java.math.BigDecimal

class NewBalanceDetailsActivity : BaseActivity() {
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
        setContentView(R.layout.activity_new_balance_details)

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

        subscribeToBalances()
    }

    // region Init
    private fun initToolbar() {
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { onBackPressed() }
        toolbar.setSubtitleTextAppearance(this, R.style.ToolbarSubtitleAppearance)
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

        preFillAmount()
    }

    private fun initButtons() {
        send_button.setCompoundDrawablesRelativeWithIntrinsicBounds(
                ContextCompat.getDrawable(this, R.drawable.ic_send_button),
                null, null, null
        )
        send_button.setOnClickListener {
            if (canSend) {
                openSend()
            }
        }

        redeem_button.setCompoundDrawablesRelativeWithIntrinsicBounds(
                ContextCompat.getDrawable(this, R.drawable.ic_qr_code_button),
                null, null, null
        )
        redeem_button.setOnClickListener {
            if (canRedeem) {
                openRedemption()
            }
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
                requestCode = SEND_REQUEST
        )
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