package org.tokend.template.features.dashboard.balances.view.adapter

import android.view.View
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.balances.BalanceItemView
import org.tokend.template.view.balances.BalanceItemViewImpl
import org.tokend.template.view.util.CircleLogoUtil
import org.tokend.template.view.util.formatter.AmountFormatter

class BalanceItemViewHolder(
        view: View,
        private val amountFormatter: AmountFormatter
) : BaseViewHolder<BalanceListItem>(view), BalanceItemView by BalanceItemViewImpl(view) {

    override fun bind(item: BalanceListItem) {
        CircleLogoUtil.setAssetLogo(logoImageView, item.asset)

        nameTextView.text = item.displayedName

        altAmountLayout.visibility = View.VISIBLE
        altAmountTextView.text =
                amountFormatter.formatAssetAmount(
                        item.available,
                        item.asset,
                        withAssetCode = false,
                        abbreviation = true
                )

        if (item.ownerName != null) {
            bottomTextView.visibility = View.VISIBLE
            bottomTextView.text = item.ownerName
        } else {
            bottomTextView.visibility = View.GONE
        }
    }
}