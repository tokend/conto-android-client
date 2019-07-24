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
) : PaginationRecyclerAdapter<CompanyClientListItem,
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
        val clientHolder = holder as? CompanyClientItemViewHolder

        clientHolder?.bind(items[position], onItemClickListener, selectionListener)
                ?: super.bindItemViewHolder(holder, position)

        val isLastInSection =
                position == itemCount - (if (needLoadingFooter) 2 else 1)
        clientHolder?.dividerIsVisible = drawDividers && !isLastInSection
    }

    override fun createFooterViewHolder(parent: ViewGroup): BaseViewHolder<CompanyClientListItem> {
        val view = parent.context
                .layoutInflater.inflate(R.layout.list_item_loading_footer, parent, false)
        return FooterViewHolder(view)
    }

    override fun bindFooterViewHolder(holder: BaseViewHolder<CompanyClientListItem>) {}

    override fun setData(data: Collection<CompanyClientListItem>?) {
        val updatedData = data?.toMutableList()
        updatedData?.let {
            val itemsToRemove = mutableListOf<String>()
            selectedItems.forEach { id ->
                updatedData.find { it.id == id }?.let {
                    it.isChecked = true
                } ?: itemsToRemove.add(id)
            }
            selectedItems.removeAll(itemsToRemove)
        }
        super.setData(updatedData)
    }

    private val selectedItems = mutableListOf<String>()

    private var onSelect: ((Int) -> Unit)? = null
    fun onSelect(onSelect: (Int) -> Unit) {
        this.onSelect = onSelect
    }

    private var selectionListener: (CompanyClientListItem, Int) -> Unit = { item, index ->
        item.isChecked = !item.isChecked
        if (item.isChecked) {
            selectedItems.add(item.id)
        } else {
            selectedItems.remove(item.id)
        }
        notifyItemChanged(index)
        onSelect?.invoke(selectedItems.size)
    }

    fun getSelected(): List<CompanyClientListItem> {
        val result = mutableListOf<CompanyClientListItem>()
        selectedItems.forEach { id ->
            items.find { it.id == id }?.let {
                result.add(it)
            }
        }
        return result
    }

    fun clearSelection() {
        if (hasSelection) {
            selectedItems.forEach { id ->
                val index = items.indexOfFirst { it.id == id }
                if(index >= 0) {
                    items[index].isChecked = false
                    notifyItemChanged(index)
                }
            }
            selectedItems.clear()
            onSelect?.invoke(0)
        }
    }

    val hasSelection: Boolean
        get() = selectedItems.size > 0
}