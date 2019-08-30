package org.tokend.template.features.wallet.view

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.MotionEvent
import android.view.View
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_simple_balance_details.*
import kotlinx.android.synthetic.main.toolbar.*
import org.jetbrains.anko.textColor
import org.tokend.sdk.utils.BigDecimalUtil
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.BalanceRecord
import org.tokend.template.data.repository.BalancesRepository
import org.tokend.template.extensions.onEditorAction
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.input.AmountEditTextWrapper
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class SimpleBalanceDetailsActivity : BaseActivity() {
    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private lateinit var amountWrapper: AmountEditTextWrapper

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

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_simple_balance_details)

        val balanceId = intent.getStringExtra(BALANCE_ID_EXTRA)
        if (balanceId == null) {
            errorHandlerFactory.getDefault().handle(IllegalArgumentException(
                    "No $BALANCE_ID_EXTRA specified"))
            finish()
            return
        }
        this.balanceId = balanceId

        initToolbar()
        initSwipeRefresh()
        initFields()
        initButtons()

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

        amountWrapper = AmountEditTextWrapper(amount_edit_text)
        amountWrapper.onAmountChanged { _, _ -> onAmountChanged() }

        amount_edit_text.onEditorAction(this::tryToSend)

        preFillAmount()
    }

    private fun initButtons() {
        val stoppingTouchListener: (View, MotionEvent) -> Boolean = { _, event ->
            if (event.action == MotionEvent.ACTION_UP
                    || event.action == MotionEvent.ACTION_CANCEL) {
                stopAmountChange()
            }
            false
        }

        dec_amount_button.setOnClickListener {
            changeAmount(inc = false)
        }
        dec_amount_button.setOnLongClickListener {
            startAmountChange(inc = false)
            true
        }
        dec_amount_button.setOnTouchListener(stoppingTouchListener)

        inc_amount_button.setOnClickListener {
            changeAmount(inc = true)
        }
        inc_amount_button.setOnLongClickListener {
            startAmountChange(inc = true)
            true
        }
        inc_amount_button.setOnTouchListener(stoppingTouchListener)

        send_button.setOnClickListener { tryToSend() }
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
        setAmount(
                if (PRE_FILLED_AMOUNT > balance.available)
                    BigDecimal.ZERO
                else
                    PRE_FILLED_AMOUNT
        )
    }

    private fun setAmount(amount: BigDecimal) {
        amount_edit_text.setText(BigDecimalUtil.toPlainString(amount))
        amount_edit_text.setSelection(amount_edit_text.text?.length ?: 0)
    }

    /**
     * @return false if this change can't be applied
     */
    private fun changeAmount(inc: Boolean): Boolean {
        val newAmount = amountWrapper.scaledAmount + if (inc) BigDecimal.ONE else MINUS_ONE

        return if (newAmount.signum() < 0) {
            setAmount(BigDecimal.ZERO)
            return false
        } else if (newAmount > balance.available) {
            setAmount(balance.available)
            false
        } else {
            setAmount(newAmount)
            true
        }
    }

    private var amountChangeDisposable: Disposable? = null
    private fun startAmountChange(inc: Boolean, interval: Long = INITIAL_AMOUNT_CHANGE_INTERVAL_MS) {
        stopAmountChange()
        amountChangeDisposable = Observable
                .interval(interval, TimeUnit.MILLISECONDS)
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    val elapsed = it * interval
                    if (interval < INITIAL_AMOUNT_CHANGE_INTERVAL_MS
                            || elapsed <= MS_TO_AMOUNT_CHANGE_INTERVAL_DECREASE) {
                        if (!changeAmount(inc)) {
                            stopAmountChange()
                        }
                    } else {
                        startAmountChange(
                                inc,
                                interval - AMOUNT_CHANGE_INTERVAL_DECREASE_STEP
                        )
                    }
                }
                .addTo(compositeDisposable)
    }

    private fun stopAmountChange() {
        amountChangeDisposable?.dispose()
    }

    private fun onAmountChanged() {
        updateAmountError()
        updateSendAvailability()
    }

    private fun updateAmountError() {
        if (amountWrapper.scaledAmount > balance.available) {
            available_text_view.textColor = errorColor
        } else {
            available_text_view.textColor = defaultAvailableLabelColor
        }
    }
    // endregion

    // region Send
    private fun updateSendAvailability() {
        val amount = amountWrapper.scaledAmount
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
                amount = amountWrapper.scaledAmount,
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

    companion object {
        private val MINUS_ONE = BigDecimal.ZERO.dec()
        private val PRE_FILLED_AMOUNT = BigDecimal.ONE
        private const val BALANCE_ID_EXTRA = "balance_id"
        private const val INITIAL_AMOUNT_CHANGE_INTERVAL_MS = 110L
        private const val MS_TO_AMOUNT_CHANGE_INTERVAL_DECREASE = 2000L
        private const val AMOUNT_CHANGE_INTERVAL_DECREASE_STEP = 50L
        private val SEND_REQUEST = "send".hashCode() and 0xffff

        fun getBundle(balanceId: String) = Bundle().apply {
            putString(BALANCE_ID_EXTRA, balanceId)
        }
    }
}
