package org.tokend.template.features.redeem.create.view

import android.os.Bundle
import org.tokend.template.features.amountscreen.view.AmountInputFragment

class RedemptionAmountInputFragment: AmountInputFragment() {
    override fun getTitleText(): String? = null

    companion object {
        fun newInstance(requiredAsset: String? = null): RedemptionAmountInputFragment {
            val fragment = RedemptionAmountInputFragment()
            fragment.arguments = Bundle().apply {
                putString(ASSET_EXTRA, requiredAsset)
            }
            return fragment
        }
    }
}