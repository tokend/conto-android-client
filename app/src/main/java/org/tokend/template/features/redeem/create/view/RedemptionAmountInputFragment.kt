package org.tokend.template.features.redeem.create.view

import kotlinx.android.synthetic.main.fragment_amount_input.*
import org.tokend.template.features.amountscreen.view.AmountInputFragment

class RedemptionAmountInputFragment: AmountInputFragment() {
    override fun getTitleText(): String? = null

    override fun onInitAllowed() {
        super.onInitAllowed()
        amount_edit_text.setText(PRE_FILLED_AMOUNT)
    }

    companion object {
        private const val PRE_FILLED_AMOUNT = "1"

        fun newInstance(requiredAsset: String? = null): RedemptionAmountInputFragment {
            val fragment = RedemptionAmountInputFragment()
            fragment.arguments = getBundle(requiredAssetCode = requiredAsset)
            return fragment
        }
    }
}