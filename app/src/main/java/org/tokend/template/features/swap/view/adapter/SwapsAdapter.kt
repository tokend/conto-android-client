package org.tokend.template.features.swap.view.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseRecyclerAdapter
import org.tokend.template.view.util.formatter.AmountFormatter

class SwapsAdapter(private val amountFormatter: AmountFormatter)
    : BaseRecyclerAdapter<SwapListItem, SwapItemViewHolder>() {
    override fun createItemViewHolder(parent: ViewGroup): SwapItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_swap,
                parent, false)
        return SwapItemViewHolder(view, amountFormatter)
    }
}