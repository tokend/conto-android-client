package org.tokend.template.features.assets.buy.view

import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.android.synthetic.main.fragment_amount_input.*
import kotlinx.android.synthetic.main.include_text_view_spinner_for_centering.view.*
import kotlinx.android.synthetic.main.layout_atomic_swap_quote_asset_selection.view.*
import org.jetbrains.anko.dip
import org.jetbrains.anko.layoutInflater
import org.tokend.sdk.api.integrations.marketplace.model.MarketplacePaymentMethodType
import org.tokend.sdk.utils.BigDecimalUtil
import org.tokend.template.R
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.amountscreen.model.AmountInputResult
import org.tokend.template.features.amountscreen.view.AmountInputFragment
import org.tokend.template.features.assets.buy.marketplace.model.MarketplaceOfferRecord
import org.tokend.template.features.assets.model.Asset
import org.tokend.template.features.balances.model.BalanceRecord
import org.tokend.template.view.balancepicker.BalancePickerBottomDialog
import java.math.BigDecimal

class MarketplaceBuyAmountFragment : AmountInputFragment() {
    private lateinit var offer: MarketplaceOfferRecord

    private val needQuoteAssetSelection: Boolean
        get() = offer.paymentMethods.size > 1

    private var quoteAssetTextView: TextView? = null
    private var quoteAssetPicker: BalancePickerBottomDialog? = null
    private var quoteAssetAvailableTextView: TextView? = null
    private var quoteAsset: Asset? = null
        set(value) {
            field = value
            onQuoteAssetChanged()
        }

    private val requiredAmount: BigDecimal by lazy {
        BigDecimalUtil.valueOf(arguments?.getString(AMOUNT_EXTRA), DEFAULT_AMOUNT)
    }

    override fun onInitAllowed() {
        this.offer = arguments?.getSerializable(OFFER_EXTRA) as? MarketplaceOfferRecord
                ?: throw IllegalArgumentException("No $OFFER_EXTRA specified")

        super.onInitAllowed()

        amountWrapper.setAmount(requiredAmount)
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
                requiredAssets = offer.paymentMethods,
                balancesFilter = { false }
        ) {
            override fun getAvailableAmount(assetCode: String,
                                            balance: BalanceRecord?): BigDecimal? = null
        }
    }

    override fun getTitleText(): String? = getString(
            R.string.template_asset_name_dash_price,
            offer.asset.name ?: offer.asset.code,
            amountFormatter.formatAssetAmount(
                    offer.price,
                    offer.priceAsset,
                    withAssetCode = true
            )
    )

    override fun displayBalance() {
        val paymentMethod = offer.paymentMethods
                .find { it.code == quoteAsset?.code }
        val balance = balancesRepository.itemsList
                .find { it.assetCode == paymentMethod?.asset?.code }

        if (paymentMethod?.type == MarketplacePaymentMethodType.INTERNAL
                && balance != null) {
            quoteAssetAvailableTextView?.visibility = View.VISIBLE
            quoteAssetAvailableTextView?.text = getString(
                    R.string.template_available,
                    amountFormatter.formatAssetAmount(
                            balance.available, balance.asset,
                            withAssetCode = false
                    )
            )
        } else {
            quoteAssetAvailableTextView?.visibility = View.GONE
        }
    }

    override fun checkAmount() {
        val availableExceeded = amountWrapper.scaledAmount > offer.amount

        when {
            availableExceeded ->
                setError(getString(
                        R.string.template_amount_available_for_buy,
                        amountFormatter.formatAssetAmount(offer.amount, offer.asset,
                                withAssetCode = false)
                ))
            else ->
                setError(null)
        }
    }

    override fun getExtraTopView(parent: ViewGroup): View? {
        val view = requireContext().layoutInflater.inflate(
                R.layout.layout_atomic_swap_quote_asset_selection, parent, false)

        quoteAssetTextView = view.spinner_text_view
        quoteAssetAvailableTextView = view.available_text_view
        quoteAssetTextView?.setOnClickListener {
            openQuoteAssetPicker()
        }
        quoteAsset = quoteAsset
                ?: (quoteAssetPicker
                        ?: throw IllegalStateException("Quote asset picker must be initialized at this point"))
                        .getItemsToDisplay().first().asset

        if (!needQuoteAssetSelection) {
            view.visibility = View.GONE
        }

        return view
    }

    private fun openQuoteAssetPicker() {
        quoteAssetPicker?.show {
            quoteAsset = it.asset
        }
    }

    private fun onQuoteAssetChanged() {
        quoteAssetTextView?.text = quoteAsset?.name ?: quoteAsset?.code
        displayBalance()
    }

    override fun postResult() {
        val amount = amountWrapper.scaledAmount
        val asset = quoteAsset
                ?: throw IllegalStateException("Quote asset must be selected at this moment")

        resultSubject.onNext(AmountInputResult(amount, asset))
    }

    override fun getSmallSizingHeightThreshold(): Int {
        return if (needQuoteAssetSelection)
            requireContext().dip(270)
        else
            super.getSmallSizingHeightThreshold()
    }

    override fun updateSizing(useSmallSize: Boolean) {
        super.updateSizing(useSmallSize)

        if (needQuoteAssetSelection) {
            val resources = requireContext().resources

            val dividerVerticalSpacing = if (useSmallSize)
                resources.getDimensionPixelSize(R.dimen.half_standard_padding)
            else
                resources.getDimensionPixelSize(R.dimen.standard_padding)

            root_layout.findViewById<View>(R.id.divider_view).run {
                layoutParams = (layoutParams as ViewGroup.MarginLayoutParams).apply {
                    setMargins(leftMargin, dividerVerticalSpacing, rightMargin, dividerVerticalSpacing)
                }
            }
        }
    }

    companion object {
        private val DEFAULT_AMOUNT = BigDecimal.ONE
        private const val OFFER_EXTRA = "offer"
        private const val AMOUNT_EXTRA = "amount"

        fun getBundle(offer: MarketplaceOfferRecord,
                      amount: BigDecimal? = null) = Bundle().apply {
            putSerializable(OFFER_EXTRA, offer)
            putString(AMOUNT_EXTRA, amount?.let(BigDecimalUtil::toPlainString))
        }

        fun newInstance(bundle: Bundle): MarketplaceBuyAmountFragment =
                MarketplaceBuyAmountFragment().withArguments(bundle)
    }
}