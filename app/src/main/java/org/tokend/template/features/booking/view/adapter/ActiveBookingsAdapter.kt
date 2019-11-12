package org.tokend.template.features.booking.view.adapter

import android.view.ViewGroup
import org.jetbrains.anko.layoutInflater
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseRecyclerAdapter
import org.tokend.template.view.util.formatter.DateFormatter

class ActiveBookingsAdapter(
        private val dateFormatter: DateFormatter
): BaseRecyclerAdapter<ActiveBookingListItem, ActiveBookingItemViewHolder>() {
    override fun createItemViewHolder(parent: ViewGroup): ActiveBookingItemViewHolder {
        val view = parent.context.layoutInflater.inflate(R.layout.list_item_active_booking,
                parent, false)
        return ActiveBookingItemViewHolder(view, dateFormatter)
    }
}