package org.tokend.template.features.assets.sell.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.Observable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.fragment_marketplace_sell_amount.*
import org.tokend.template.R
import org.tokend.template.extensions.onEditorAction
import org.tokend.template.features.assets.sell.model.MarketplaceSellInfoHolder
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.input.SoftInputUtil
import java.math.BigDecimal
import kotlin.math.roundToInt

class MarketplaceSellAmountFragment: BaseFragment() {
    private lateinit var sellInfoHolder: MarketplaceSellInfoHolder

    private val resultSubject = PublishSubject.create<BigDecimal>()
    val resultObservable: Observable<BigDecimal> = resultSubject

    private val hintColor: Int by lazy {
        ContextCompat.getColor(requireContext(), R.color.secondary_text)
    }

    private val errorColor: Int by lazy {
        ContextCompat.getColor(requireContext(), R.color.error)
    }

    private var canContinue: Boolean = false
        set(value) {
            field = value
            continue_button.isEnabled = value
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_marketplace_sell_amount, container, false)
    }

    override fun onInitAllowed() {
        sellInfoHolder = requireActivity() as? MarketplaceSellInfoHolder
                ?: throw IllegalArgumentException("Activity must hold sell info")

        initLabels()
        initFields()
        initButtons()

        subscribeToBalances()
    }

    private fun initLabels() {
        top_info_text_view.text = sellInfoHolder.balance.asset.run { name ?: code }
    }

    private fun initFields() {
        amount_view.editText.onEditorAction {
            tryToPostResult()
        }

        amount_view.amountWrapper.apply {
            onAmountChanged { _, _ ->
                updateAmountError()
                updateContinueAvailability()
            }

            maxPlacesAfterComa = sellInfoHolder.balance.asset.trailingDigits
            setAmount(sellInfoHolder.amount)
        }

        root_layout.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            val width = right - left
            val oldWidth = oldRight - oldLeft
            if (width != oldWidth) {
                // Max amount input field width is 45% of the container width.
                amount_view.editText.maxWidth = (width * 0.45).roundToInt()
            }
        }

        amount_view.editText.requestFocus()
        SoftInputUtil.showSoftInputOnView(amount_view.editText)
    }

    private fun initButtons() {
        continue_button.setOnClickListener {
            tryToPostResult()
        }
    }

    private fun subscribeToBalances() {
        repositoryProvider.balances()
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { onBalancesUpdated()  }
                .addTo(compositeDisposable)
    }

    private fun onBalancesUpdated() {
        updateMaxAmount()
        updateContinueAvailability()
        displayBalance()
    }

    private fun updateMaxAmount() {
        amount_view.maxAmount = sellInfoHolder.balance.available
    }

    private fun updateAmountError() {
        if (amount_view.amountWrapper.scaledAmount > sellInfoHolder.balance.available) {
            balance_text_view.setTextColor(errorColor)
        } else {
            balance_text_view.setTextColor(hintColor)
        }
    }

    private fun updateContinueAvailability() {
        val amount = amount_view.amountWrapper.scaledAmount
        canContinue = amount.signum() > 0 && amount <= sellInfoHolder.balance.available
    }

    private fun displayBalance() {
        balance_text_view.text = getString(
                R.string.template_available,
                amountFormatter.formatAssetAmount(
                        sellInfoHolder.balance.available,
                        sellInfoHolder.balance.asset,
                        withAssetCode = false,
                        withAssetName = false
                )
        )
    }

    private fun tryToPostResult() {
        if (canContinue) {
            postResult()
        }
    }

    private fun postResult() {
        resultSubject.onNext(amount_view.amountWrapper.scaledAmount)
    }

    companion object {
        fun newInstance() = MarketplaceSellAmountFragment()
    }
}