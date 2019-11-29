package org.tokend.template.features.booking.add.rooms.view.adapter

import android.view.ViewGroup
import org.jetbrains.anko.layoutInflater
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseRecyclerAdapter
import org.tokend.template.view.util.formatter.AmountFormatter

class BookingRoomsAdapter(
        private val amountFormatter: AmountFormatter
): BaseRecyclerAdapter<BookingRoomListItem, BookingRoomItemViewHolder>() {
    var drawDividers: Boolean = true
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun createItemViewHolder(parent: ViewGroup): BookingRoomItemViewHolder {
        val view = parent.context.layoutInflater.inflate(R.layout.list_item_booking_room,
                parent, false)
        return BookingRoomItemViewHolder(view, amountFormatter)
    }

    override fun bindItemViewHolder(holder: BookingRoomItemViewHolder, position: Int) {
        super.bindItemViewHolder(holder, position)
        holder.dividerIsVisible = drawDividers && position < itemCount - 1
    }
}