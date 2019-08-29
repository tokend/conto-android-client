package org.tokend.template.features.assets.buy.view

import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.v7.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.android.synthetic.main.fragment_amount_input.*
import kotlinx.android.synthetic.main.layout_atomic_swap_quote_asset_selection.view.*
import org.jetbrains.anko.layoutInflater
import org.tokend.template.R
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.AtomicSwapAskRecord
import org.tokend.template.data.model.BalanceRecord
import org.tokend.template.features.amountscreen.model.AmountInputResult
import org.tokend.template.features.amountscreen.view.AmountInputFragment
import org.tokend.template.view.balancepicker.BalancePickerBottomDialog
import java.math.BigDecimal

class AtomicSwapAmountFragment : AmountInputFragment() {
    private lateinit var ask: AtomicSwapAskRecord

    private val needQuoteAssetSelection: Boolean
        get() = ask.quoteAssets.size > 1

    private lateinit var availableTextView: TextView

    private lateinit var quoteAssetTextView: TextView
    private lateinit var quoteAssetPicker: BalancePickerBottomDialog
    private var quoteAsset: Asset? = null
        set(value) {
            field = value
            quoteAssetTextView.text = value?.name ?: value?.code
        }

    override fun onInitAllowed() {
        val askId = arguments?.getString(ASK_ID_EXTRA)
        TODO("Serialize ask instead of searching it")
        this.ask = listOf(
                repositoryProvider.atomicSwapAsks(requestedAsset!!).itemsList
        )
                .flatten()
                .find { it.id == askId }
                ?: throw IllegalArgumentException("No ask found for ID $askId from $ASK_ID_EXTRA")

        super.onInitAllowed()
        onAssetChanged()

        amount_edit_text.setText(PRE_FILLED_AMOUNT)
    }

    override fun initFields() {
        super.initFields()

        amount_edit_text.layoutParams =
                (amount_edit_text.layoutParams as ConstraintLayout.LayoutParams).apply {
                    horizontalBias = 0.5F
                }

        if (needQuoteAssetSelection) {
            amount_edit_text_container.layoutParams =
                    (amount_edit_text_container.layoutParams as ConstraintLayout.LayoutParams).apply {
                        verticalBias = 0.65F
                    }
        }

        balance_text_view.text = getString(R.string.quantity)
    }

    override fun initAssetSelection() {
        asset_code_text_view.visibility = View.GONE

        quoteAssetPicker = object : BalancePickerBottomDialog(
                requireContext(),
                amountFormatter,
                balanceComparator,
                repositoryProvider.balances(),
                requiredAssets = ask.quoteAssets,
                balancesFilter = { false }
        ) {
            override fun getAvailableAmount(assetCode: String,
                                            balance: BalanceRecord?): BigDecimal? = null
        }
    }

    override fun getTitleText(): String? = null

    override fun displayBalance() {}

    override fun checkAmount() {
        val availableExceeded = amountWrapper.scaledAmount > ask.amount

        when {
            availableExceeded ->
                setError(getString(
                        R.string.template_amount_available_for_buy,
                        amountFormatter.formatAssetAmount(ask.amount, ask.asset,
                                withAssetCode = false)
                ))
            else ->
                setError(null)
        }
    }

    override fun getExtraTopView(parent: ViewGroup): View? {
        val view = requireContext().layoutInflater.inflate(
                R.layout.layout_atomic_swap_quote_asset_selection, parent, false)

        quoteAssetTextView = view.quote_asset_code_text_view
        quoteAssetTextView.setOnClickListener {
            openQuoteAssetPicker()
        }
        quoteAsset = quoteAssetPicker.getItemsToDisplay().first().asset

        if (!needQuoteAssetSelection) {
            view.visibility = View.GONE
        }

        return view
    }

    override fun getExtraAmountView(parent: ViewGroup): View? {
        val context = ContextThemeWrapper(requireContext(), R.style.HintText)
        return TextView(context, null, R.style.HintText)
                .apply {
                    text = getString(
                            R.string.template_amount_available_for_buy,
                            amountFormatter.formatAssetAmount(ask.amount, ask.asset,
                                    withAssetCode = false)
                    )
                    gravity = Gravity.CENTER
                }
                .also { availableTextView = it }
    }

    override fun setError(message: String?) {
        super.setError(message)
        availableTextView.visibility =
                if (message == null)
                    View.VISIBLE
                else
                    View.GONE
    }

    private fun openQuoteAssetPicker() {
        quoteAssetPicker.show {
            quoteAsset = it.asset
        }
    }

    override fun postResult() {
        val amount = amountWrapper.scaledAmount
        val asset = quoteAsset
                ?: throw IllegalStateException("Quote asset must be selected at this moment")

        resultSubject.onNext(AmountInputResult(amount, asset))
    }

    companion object {
        private const val PRE_FILLED_AMOUNT = "1"
        private const val ASK_ID_EXTRA = "ask_id"

        fun newInstance(assetCode: String,
                        askId: String): AtomicSwapAmountFragment {
            val fragment = AtomicSwapAmountFragment()
            fragment.arguments = Bundle().apply {
                putString(ASK_ID_EXTRA, askId)
                putString(ASSET_EXTRA, assetCode)
            }
            return fragment
        }
    }
}