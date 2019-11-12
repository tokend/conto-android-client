package org.tokend.template.features.booking.view.adapter

import android.view.View
import android.widget.TextView
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.util.formatter.DateFormatter
import java.util.*

class ActiveBookingItemViewHolder(
        view: View,
        private val dateFormatter: DateFormatter
) : BaseViewHolder<ActiveBookingListItem>(view) {
    private val seatsCountTextView: TextView = view.findViewById(R.id.seats_count_text_view)
    private val seatsHintTextView: TextView = view.findViewById(R.id.seats_hint_text_view)
    private val dateFromTextView: TextView = view.findViewById(R.id.booking_from_text_view)
    private val dateToTextView: TextView = view.findViewById(R.id.booking_until_text_view)

    override fun bind(item: ActiveBookingListItem) {
        seatsCountTextView.text = item.seatsCount.toString()

        seatsHintTextView.text = view.context.resources.getQuantityString(
                R.plurals.seat,
                item.seatsCount
        )

        dateFromTextView.text = getDateString(item.dateFrom)
        dateToTextView.text = getDateString(item.dateTo)
    }

    private fun getDateString(date: Date): String {
        return "${dateFormatter.formatCompactDateOnly(date, false)} " +
                dateFormatter.formatTimeOnly(date)
    }
}