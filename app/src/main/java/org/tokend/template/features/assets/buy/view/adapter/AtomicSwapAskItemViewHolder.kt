package org.tokend.template.features.assets.buy.view.adapter

import android.annotation.SuppressLint
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.apmem.tools.layouts.FlowLayout
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.adapter.base.SimpleItemClickListener
import org.tokend.template.view.util.formatter.AmountFormatter

class AtomicSwapAskItemViewHolder(view: View,
                                  private val amountFormatter: AmountFormatter
) : BaseViewHolder<AtomicSwapAskListItem>(view) {
    private val availableTextView: TextView = view.findViewById(R.id.available_text_view)
    private val pricesHintTextView: TextView = view.findViewById(R.id.prices_hint_text_view)
    private val pricesLayout: ViewGroup = view.findViewById(R.id.prices_layout)
    private val buyButton: Button = view.findViewById(R.id.buy_btn)
    private val priceThemedContext = ContextThemeWrapper(view.context, R.style.BadgeText)

    private val priceTextViewMargin =
            view.context.resources.getDimensionPixelSize(R.dimen.quarter_standard_margin)

    override fun bind(item: AtomicSwapAskListItem, clickListener: SimpleItemClickListener<AtomicSwapAskListItem>?) {
        bind(item)
        buyButton.setOnClickListener { clickListener?.invoke(view, item) }
    }

    @SuppressLint("SetTextI18n")
    override fun bind(item: AtomicSwapAskListItem) {
        availableTextView.text = amountFormatter.formatAssetAmount(
                item.available,
                item.asset,
                withAssetCode = false,
                abbreviation = true
        )

        pricesHintTextView.text = view.context.getString(R.string.with_price_of)

        pricesLayout.removeAllViews()
        item.quoteAssets.forEach { quoteAsset ->
            val textView = TextView(priceThemedContext, null, R.style.BadgeText)
            textView.text = amountFormatter.formatAssetAmount(
                    quoteAsset.price,
                    quoteAsset,
                    withAssetName = true
            )
            pricesLayout.addView(textView)
            textView.layoutParams = (textView.layoutParams as FlowLayout.LayoutParams).apply {
                setMargins(priceTextViewMargin, priceTextViewMargin, priceTextViewMargin, priceTextViewMargin)
            }
        }
    }
}