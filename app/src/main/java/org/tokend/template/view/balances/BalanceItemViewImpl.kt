package org.tokend.template.view.balances

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.list_item_balance.view.*
import org.tokend.template.view.util.CircleLogoUtil

class BalanceItemViewImpl(view: View) : BalanceItemView {
    override val logoImageView: ImageView = view.asset_logo_image_view
    override val nameTextView: TextView = view.asset_code_text_view
    override val bottomTextView: TextView = view.balance_bottom_info_text_view
    override val dividerView: View = view.divider_view
    override val altAmountLayout: ViewGroup = view.balance_alt_available_layout
    override val altAmountTextView: TextView = view.balance_alt_available_text_view

    override fun displayLogo(logoUrl: String?, assetCode: String) {
        CircleLogoUtil.setLogo(logoImageView, assetCode, logoUrl)
    }
}