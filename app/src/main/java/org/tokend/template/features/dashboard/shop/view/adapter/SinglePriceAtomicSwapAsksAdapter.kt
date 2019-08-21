package org.tokend.template.features.dashboard.shop.view.adapter

import android.view.View
import android.view.ViewGroup
import org.jetbrains.anko.layoutInflater
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.adapter.base.PaginationRecyclerAdapter
import org.tokend.template.view.util.formatter.AmountFormatter

class SinglePriceAtomicSwapAsksAdapter(
        private val amountFormatter: AmountFormatter
) : PaginationRecyclerAdapter<SinglePriceAtomicSwapAskListItem,
        BaseViewHolder<SinglePriceAtomicSwapAskListItem>>() {
    class FooterViewHolder(v: View) : BaseViewHolder<SinglePriceAtomicSwapAskListItem>(v) {
        override fun bind(item: SinglePriceAtomicSwapAskListItem) {}
    }

    override fun createFooterViewHolder(parent: ViewGroup): BaseViewHolder<SinglePriceAtomicSwapAskListItem> {
        val view = parent.context
                .layoutInflater.inflate(R.layout.list_item_loading_footer, parent, false)
        return FooterViewHolder(view)
    }

    override fun bindFooterViewHolder(holder: BaseViewHolder<SinglePriceAtomicSwapAskListItem>) {}

    override fun createItemViewHolder(parent: ViewGroup): BaseViewHolder<SinglePriceAtomicSwapAskListItem> {
        val view = parent.context.layoutInflater.inflate(R.layout.list_item_single_price_atomic_swap_ask,
                parent, false)
        return SinglePriceAtomicSwapAskItemViewHolder(view, amountFormatter)
    }
}