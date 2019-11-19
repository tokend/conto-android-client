package org.tokend.template.features.wallet.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import org.jetbrains.anko.dip
import org.jetbrains.anko.layoutInflater
import org.tokend.template.R
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.input.AmountEditTextWrapper
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class PlusMinusAmountInputView
@JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private var compositeDisposable = CompositeDisposable()

    private val incButton: View
    private val decButton: View

    lateinit var amountWrapper: AmountEditTextWrapper
    val editText: EditText

    var maxAmount: BigDecimal? = null
        set(value) {
            field = value
            onAmountLimitsChanged()
        }

    var minAmount: BigDecimal = BigDecimal.ZERO
        set(value) {
            field = value
            onAmountLimitsChanged()
        }

    init {
        context.layoutInflater.inflate(R.layout.layout_plus_minus_amount_input_view,
                this, true)

        gravity = Gravity.CENTER_VERTICAL
        setPadding(dip(2), 0, dip(2), 0)
        clipToPadding = false
        clipChildren = false

        editText = findViewById(R.id.amount_edit_text)
        incButton = findViewById(R.id.inc_amount_button)
        decButton = findViewById(R.id.dec_amount_button)

        initField()
        initButtons()
    }

    private fun initField() {
        amountWrapper = AmountEditTextWrapper(editText)
    }

    private fun initButtons() {
        val stoppingTouchListener: (View, MotionEvent) -> Boolean = { _, event ->
            if (event.action == MotionEvent.ACTION_UP
                    || event.action == MotionEvent.ACTION_CANCEL) {
                stopAmountChange()
            }
            false
        }

        decButton.apply {
            setOnClickListener {
                changeAmount(inc = false)
            }

            setOnLongClickListener {
                startAmountChange(inc = false)
                true
            }

            setOnTouchListener(stoppingTouchListener)
        }

        incButton.apply {
            setOnClickListener {
                changeAmount(inc = true)
            }

            setOnLongClickListener {
                startAmountChange(inc = true)
                true
            }

            setOnTouchListener(stoppingTouchListener)
        }
    }

    private fun onAmountLimitsChanged() {
        val maxAmount = this.maxAmount

        if (maxAmount != null && amountWrapper.scaledAmount > maxAmount) {
            amountWrapper.setAmount(maxAmount)
        }

        if (amountWrapper.scaledAmount < minAmount) {
            amountWrapper.setAmount(minAmount)
        }
    }

    /**
     * @return false if this change can't be applied
     */
    private fun changeAmount(inc: Boolean): Boolean {
        val newAmount = amountWrapper.scaledAmount + if (inc) BigDecimal.ONE else MINUS_ONE
        val maxAmount = this.maxAmount

        return if (newAmount < minAmount) {
            amountWrapper.setAmount(minAmount)
            return false
        } else if (maxAmount != null && newAmount > maxAmount) {
            amountWrapper.setAmount(maxAmount)
            false
        } else {
            amountWrapper.setAmount(newAmount)
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        compositeDisposable.dispose()
    }

    private companion object {
        private val MINUS_ONE = BigDecimal.ZERO.dec()
        private const val INITIAL_AMOUNT_CHANGE_INTERVAL_MS = 110L
        private const val MS_TO_AMOUNT_CHANGE_INTERVAL_DECREASE = 2000L
        private const val AMOUNT_CHANGE_INTERVAL_DECREASE_STEP = 50L
    }
}