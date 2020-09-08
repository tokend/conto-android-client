package org.tokend.template.features.shaketopay.view.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseRecyclerAdapter

class NearbyUsersAdapter : BaseRecyclerAdapter<NearbyUserListItem, NearbyUserItemViewHolder>() {
    override fun createItemViewHolder(parent: ViewGroup): NearbyUserItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
                R.layout.list_item_user_nearby, parent, false
        )
        return NearbyUserItemViewHolder(view)
    }

    override fun getDiffCallback(newItems: List<NearbyUserListItem>): DiffUtil.Callback? {
        return object : DiffUtil.Callback() {
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
                val first = items[oldItemPosition]
                val second = newItems[newItemPosition]

                return first.name == second.name && first.avatarUrl == second.avatarUrl
            }

        }
    }
}