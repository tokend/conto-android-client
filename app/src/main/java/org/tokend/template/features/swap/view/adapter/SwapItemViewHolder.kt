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

    override fun bind(item: SwapListItem) {
        baseAmountTextView.text = amountFormatter.formatAssetAmount(
                item.baseAmount,
                item.baseAsset,
                withAssetCode = false
        )
        baseAssetTextView.text = item.baseAsset.name ?: item.baseAsset.code

        quoteAmountTextView.text = amountFormatter.formatAssetAmount(
                item.quoteAmount,
                item.quoteAsset,
                withAssetCode = false
        )
        quoteAssetTextView.text = item.quoteAsset.name ?: item.quoteAsset.code

        counterpartyTextView.text = item.counterparty

        stateTextView.text = localizedName.forSwapState(item.state, item.isIncoming)
        stateTextView.textColor = when (item.state) {
            SwapState.CREATED -> secondaryColor
            else -> okColor
        }
    }
}