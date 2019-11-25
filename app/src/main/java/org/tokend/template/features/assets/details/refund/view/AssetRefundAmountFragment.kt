package org.tokend.template.features.assets.details.refund.view

import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.view.View
import kotlinx.android.synthetic.main.fragment_amount_input.*
import org.tokend.template.R
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.amountscreen.view.AmountInputFragment
import org.tokend.template.features.trade.orderbook.model.OrderBookEntryRecord

class AssetRefundAmountFragment : AmountInputFragment() {
    private lateinit var offer: OrderBookEntryRecord

    override fun onInitAllowed() {
        this.offer = arguments?.getSerializable(OFFER_EXTRA) as? OrderBookEntryRecord
                ?: throw IllegalArgumentException("No $OFFER_EXTRA specified")

        super.onInitAllowed()
    }

    override fun initAssetSelection() {
        asset_code_text_view.visibility = View.GONE
    }

    override fun initFields() {
        super.initFields()
        amount_edit_text.layoutParams =
                (amount_edit_text.layoutParams as ConstraintLayout.LayoutParams).apply {
                    horizontalBias = 0.5F
                }
    }

    override fun focusAmountField() {
        amount_edit_text.requestFocus()
    }

    override fun getTitleText(): String? {
        return getString(
                R.string.template_price_one_equals,
                offer.baseAsset.name ?: offer.baseAsset.code,
                amountFormatter.formatAssetAmount(
                        offer.price,
                        offer.quoteAsset,
                        withAssetCode = true
                )
        )
    }

    override fun checkAmount() {
        super.checkAmount()
        if (amountWrapper.scaledAmount > offer.volume) {
            setError(getString(
                    R.string.template_max_refund_amount,
                    amountFormatter.formatAssetAmount(
                            offer.volume,
                            offer.baseAsset,
                            withAssetCode = false,
                            withAssetName = false
                    )
            ))
        }
    }

    companion object {
        private const val OFFER_EXTRA = "offer"

        fun getBundle(offer: OrderBookEntryRecord) = Bundle().apply {
            putString(ASSET_EXTRA, offer.baseAsset.code)
            putSerializable(OFFER_EXTRA, offer)
        }

        fun newInstance(bundle: Bundle): AssetRefundAmountFragment =
                AssetRefundAmountFragment().withArguments(bundle)
    }
}