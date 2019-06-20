package org.tokend.template.features.companies.view.adapter

import android.support.v7.util.DiffUtil
import android.view.LayoutInflater
import android.view.ViewGroup
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseRecyclerAdapter

class CompanyItemsAdapter : BaseRecyclerAdapter<CompanyListItem, CompanyItemViewHolder>() {

    private var dividersWasDrawn = true

    var drawDividers: Boolean = true
        set(value) {
            field = value
            notifyDataSetChanged()
            dividersWasDrawn = value
        }

    override fun createItemViewHolder(parent: ViewGroup): CompanyItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_company, parent, false)
        return CompanyItemViewHolder(view)
    }

    override fun bindItemViewHolder(holder: CompanyItemViewHolder, position: Int) {
        super.bindItemViewHolder(holder, position)
        holder.dividerIsVisible = drawDividers && position < itemCount - 1
    }

    override fun getDiffCallback(
            newItems: List<CompanyListItem>
    ): DiffUtil.Callback? = object : DiffUtil.Callback() {
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return items[oldItemPosition].id == newItems[newItemPosition].id
        }

        override fun getOldListSize(): Int {
            return items.size
        }

        override fun getNewListSize(): Int {
            return newItems.size
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            // Divider.
            if (dividersWasDrawn != drawDividers
                    || oldItemPosition == items.size - 1 && newItemPosition != newItems.size - 1
                    || oldItemPosition != items.size - 1 && newItemPosition == newItems.size - 1) {
                return false
            }

            val first = items[oldItemPosition]
            val second = newItems[newItemPosition]

            return first.name == second.name
        }
    }
}