package org.tokend.template.features.swap.view.adapter

import android.support.v4.content.ContextCompat
import android.view.View
import android.widget.TextView
import org.jetbrains.anko.textColor
import org.tokend.template.R
import org.tokend.template.features.swap.model.SwapState
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.util.LocalizedName
import org.tokend.template.view.util.formatter.AmountFormatter

class SwapItemViewHolder(
        view: View,
        private val amountFormatter: AmountFormatter
) : BaseViewHolder<SwapListItem>(view) {
    private val baseAmountTextView: TextView = view.findViewById(R.id.base_amount_text_view)
    private val baseAssetTextView: TextView = view.findViewById(R.id.base_asset_text_view)
    private val quoteAmountTextView: TextView = view.findViewById(R.id.quote_amount_text_view)
    private val quoteAssetTextView: TextView = view.findViewById(R.id.quote_asset_text_view)
    private val counterpartyTextView: TextView = view.findViewById(R.id.swap_counterparty_text_view)
    private val stateTextView: TextView = view.findViewById(R.id.swap_state_text_view)

    private val localizedName = LocalizedName(view.context)

    private val secondaryColor = ContextCompat.getColor(view.context, R.color.secondary_text)
    private val okColor = ContextCompat.getColor(view.context, R.color.ok)
    private val errorColor = ContextCompat.getColor(view.context, R.color.error)

    override fun bind(item: SwapListItem) {
        baseAmountTextView.text = amountFormatter.formatAssetAmount(
                item.payAmount,
                item.payAsset,
                withAssetCode = false
        )
        baseAssetTextView.text = item.payAsset.name ?: item.payAsset.code

        quoteAmountTextView.text = amountFormatter.formatAssetAmount(
                item.receiveAmount,
                item.receiveAsset,
                withAssetCode = false
        )
        quoteAssetTextView.text = item.receiveAsset.name ?: item.receiveAsset.code

        if (item.counterparty != null) {
            counterpartyTextView.visibility = View.VISIBLE
            counterpartyTextView.text = item.counterparty
        } else {
            counterpartyTextView.visibility = View.GONE
        }

        stateTextView.text = localizedName.forSwapState(item.state, item.isIncoming)
        stateTextView.textColor = when (item.state) {
            SwapState.CREATED -> secondaryColor
            SwapState.CANCELED,
            SwapState.CANCELED_BY_COUNTERPARTY -> errorColor
            else -> {
                if (item.isIncoming && item.state == SwapState.WAITING_FOR_CLOSE_BY_SOURCE)
                    secondaryColor
                else
                    okColor
            }
        }
    }
}