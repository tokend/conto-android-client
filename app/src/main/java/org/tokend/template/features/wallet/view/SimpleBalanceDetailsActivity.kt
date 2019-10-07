package org.tokend.template.features.wallet.view

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.v4.content.ContextCompat
import android.view.Menu
import android.view.MenuItem
import android.view.View
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.include_rounded_elevated_bottom_sheet_header.*
import kotlinx.android.synthetic.main.layout_simple_balance_details_content.*
import kotlinx.android.synthetic.main.layout_simple_balance_details_redemption.*
import kotlinx.android.synthetic.main.toolbar.*
import org.jetbrains.anko.textColor
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.BalanceRecord
import org.tokend.template.data.repository.BalancesRepository
import org.tokend.template.extensions.onEditorAction
import org.tokend.template.features.redeem.create.view.SimpleRedemptionFragment
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.LoadingIndicatorManager
import java.math.BigDecimal

class SimpleBalanceDetailsActivity : BaseActivity() {
    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private var defaultAvailableLabelColor: Int = 0
    private val errorColor: Int by lazy {
        ContextCompat.getColor(this, R.color.error)
    }

    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    private lateinit var balanceId: String

    private val balance: BalanceRecord
        get() = balancesRepository.itemsList.first { it.id == balanceId }

    private var canSend: Boolean = false
        set(value) {
            field = value
            send_button.isEnabled = value
        }

    private lateinit var bottomSheet: BottomSheetBehavior<View>

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
        initBottomSheet()

        subscribeToBalances()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = balance.asset.name ?: balance.asset.code
        toolbar.subtitle = balance.company?.name
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent))
        swipe_refresh.setOnRefreshListener {
            update(force = true)
        }
    }

    private fun initFields() {
        defaultAvailableLabelColor = available_text_view.currentTextColor

        payment_amount_view.amountWrapper.onAmountChanged { _, _ -> onAmountChanged() }
        payment_amount_view.amountWrapper.maxPlacesAfterComa = balance.asset.trailingDigits
        payment_amount_view.editText.onEditorAction(this::tryToSend)

        preFillAmount()
    }

    private fun initButtons() {
        send_button.setOnClickListener { tryToSend() }
    }

    private fun initBottomSheet() {
        bottomSheet = BottomSheetBehavior.from(redemption_bottom_sheet_layout)

        // Fade header.
        bottomSheet.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(p0: View, p1: Int) {}

            override fun onSlide(p0: View, offset: Float) {
                bottom_sheet_header_fade.alpha = offset
            }
        })

        supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container_layout, SimpleRedemptionFragment.newInstance(
                        SimpleRedemptionFragment.getBundle(balanceId)
                ))
                .commit()

        redemption_bottom_sheet_text_view.setOnClickListener {
            if (bottomSheet.state != BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
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
        updateAmountMaximum()
        updateAmountError()
        updateSendAvailability()
    }

    private fun displayBalance() {
        available_text_view.text = getString(
                R.string.template_available,
                amountFormatter.formatAssetAmount(
                        balance.available,
                        balance.asset,
                        withAssetCode = false
                )
        )
    }

    // region Amount
    private fun preFillAmount() {
        payment_amount_view.amountWrapper.setAmount(
                if (PRE_FILLED_AMOUNT > balance.available)
                    BigDecimal.ZERO
                else
                    PRE_FILLED_AMOUNT
        )
    }

    private fun onAmountChanged() {
        updateAmountError()
        updateSendAvailability()
    }

    private fun updateAmountMaximum() {
        payment_amount_view.maxAmount = balance.available
    }

    private fun updateAmountError() {
        if (payment_amount_view.amountWrapper.scaledAmount > balance.available) {
            available_text_view.textColor = errorColor
        } else {
            available_text_view.textColor = defaultAvailableLabelColor
        }
    }
    // endregion

    // region Send
    private fun updateSendAvailability() {
        val amount = payment_amount_view.amountWrapper.scaledAmount
        canSend = amount.signum() > 0
                && amount <= balance.available
    }

    private fun tryToSend() {
        if (canSend) {
            openSend()
        }
    }

    private fun openSend() {
        Navigator.from(this).openSend(
                asset = balance.assetCode,
                amount = payment_amount_view.amountWrapper.scaledAmount,
                requestCode = SEND_REQUEST
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SEND_REQUEST && resultCode == Activity.RESULT_OK) {
            preFillAmount()
        }
    }
    // endregion

    override fun onBackPressed() {
        if (bottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.balance_details, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.asset_details -> openAssetDetails()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openAssetDetails() {
        Navigator.from(this).openAssetDetails(balance.asset)
    }

    companion object {
        private val PRE_FILLED_AMOUNT = BigDecimal.ONE
        private const val BALANCE_ID_EXTRA = "balance_id"
        private val SEND_REQUEST = "send".hashCode() and 0xffff

        fun getBundle(balanceId: String) = Bundle().apply {
            putString(BALANCE_ID_EXTRA, balanceId)
        }
    }
}
