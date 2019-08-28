package org.tokend.template.features.dashboard.balances.view.adapter

import android.view.View
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.balances.BalanceItemView
import org.tokend.template.view.balances.BalanceItemViewImpl
import org.tokend.template.view.util.formatter.AmountFormatter

class BalanceItemViewHolder(
        view: View,
        private val amountFormatter: AmountFormatter
) : BaseViewHolder<BalanceListItem>(view), BalanceItemView by BalanceItemViewImpl(view) {

    override fun bind(item: BalanceListItem) {
        displayLogo(item.logoUrl, item.asset.code)

        nameTextView.text = item.displayedName

        if (item.ownerName != null) {
            bottomTextView.text = item.ownerName
            altAmountLayout.visibility = View.VISIBLE
            altAmountTextView.text =
                    amountFormatter.formatAssetAmount(
                            item.available,
                            item.asset,
                            withAssetCode = false,
                            abbreviation = true
                    )
        } else {
            bottomTextView.text = view.context.getString(
                    R.string.template_available,
                    amountFormatter.formatAssetAmount(
                            item.available,
                            item.asset,
                            withAssetCode = false,
                            abbreviation = true
                    )
            )
            altAmountLayout.visibility = View.GONE
        }
    }
}