package org.tokend.template.features.clients.view.adapter

import android.view.View
import android.view.ViewGroup
import org.jetbrains.anko.layoutInflater
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.adapter.base.PaginationRecyclerAdapter
import org.tokend.template.view.util.formatter.AmountFormatter

class CompanyClientItemsAdapter(
        private val amountFormatter: AmountFormatter
): PaginationRecyclerAdapter<CompanyClientListItem,
        BaseViewHolder<CompanyClientListItem>>() {

    class FooterViewHolder(v: View) : BaseViewHolder<CompanyClientListItem>(v) {
        override fun bind(item: CompanyClientListItem) {}
    }

    var drawDividers: Boolean = true
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun createItemViewHolder(parent: ViewGroup): BaseViewHolder<CompanyClientListItem> {
        val view = parent.context.layoutInflater
                .inflate(R.layout.list_item_company_client, parent, false)
        return CompanyClientItemViewHolder(view, amountFormatter)
    }

    override fun bindItemViewHolder(holder: BaseViewHolder<CompanyClientListItem>, position: Int) {
        super.bindItemViewHolder(holder, position)
        val isLastInSection =
                position == itemCount - (if (needLoadingFooter) 2 else 1)
        (holder as? CompanyClientItemViewHolder)?.dividerIsVisible = drawDividers && !isLastInSection
    }

    override fun createFooterViewHolder(parent: ViewGroup): BaseViewHolder<CompanyClientListItem> {
        val view = parent.context
                .layoutInflater.inflate(R.layout.list_item_loading_footer, parent, false)
        return FooterViewHolder(view)
    }

    override fun bindFooterViewHolder(holder: BaseViewHolder<CompanyClientListItem>) {}
}