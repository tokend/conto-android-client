package org.tokend.template.features.booking.add.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.fragment_booking_summary.*
import org.tokend.template.R
import org.tokend.template.features.booking.add.model.BookingInfoHolder
import org.tokend.template.features.booking.add.rooms.view.adapter.BookingRoomItemViewHolder
import org.tokend.template.features.booking.add.rooms.view.adapter.BookingRoomListItem
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.view.details.DetailsItem
import org.tokend.template.view.details.adapter.DetailsItemsAdapter
import org.tokend.template.view.util.formatter.DateFormatter
import java.math.MathContext

class BookingSummaryFragment : BaseFragment() {
    private lateinit var bookingInfoHolder: BookingInfoHolder

    private lateinit var adapter: DetailsItemsAdapter

    private val resultSubject = PublishSubject.create<Any>()
    val resultObservable: Observable<Any> = resultSubject

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_booking_summary, container, false)
    }

    override fun onInitAllowed() {
        bookingInfoHolder = requireActivity() as? BookingInfoHolder
                ?: throw IllegalStateException("Parent activity must hold booking info")

        initDetailsList()
        initConfirmationButton()

        displayRoom()
        displaySeatsCount()
        displayTime()
        displayTotal()
    }

    private fun initDetailsList() {
        adapter = DetailsItemsAdapter()
        details_list.adapter = adapter
        details_list.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun initConfirmationButton() {
        confirm_button.setOnClickListener {
            confirm()
        }
    }

    private fun displayTime() {
        val dateFormatter = DateFormatter(requireContext())
        val time = bookingInfoHolder.bookingTime

        adapter.addData(
                DetailsItem(
                        text = getString(R.string.time_from) + " "
                                + dateFormatter.formatCompact(time.from, false),
                        icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_calendar)
                ),
                DetailsItem(
                        text = getString(R.string.time_until) + " "
                                + dateFormatter.formatCompact(time.to, false)
                )
        )
    }

    private fun displaySeatsCount() {
        adapter.addData(
                DetailsItem(
                        text = bookingInfoHolder.seatsCount.toString() + " " +
                                requireContext().resources.getQuantityString(
                                        R.plurals.seat,
                                        bookingInfoHolder.seatsCount
                                ),
                        icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_seat_desk)
                )
        )
    }

    private fun displayRoom() {
        val roomItem = BookingRoomListItem(bookingInfoHolder.selectedRoom)
        BookingRoomItemViewHolder(booking_room_layout, amountFormatter).apply {
            bind(roomItem)
            dividerIsVisible = false
        }
    }

    private fun displayTotal() {
        val totalHours = bookingInfoHolder.bookingTime.run {
            ((to.time - from.time) / 1000).toFloat() / 3600
        }.toBigDecimal(MathContext.DECIMAL64)

        val totalAmount = totalHours
                .multiply(
                        bookingInfoHolder.selectedRoom.price,
                        MathContext.DECIMAL64
                )
                .multiply(
                        bookingInfoHolder.seatsCount.toBigDecimal(),
                        MathContext.DECIMAL64
                )

        amount_text_view.text = amountFormatter.formatAssetAmount(
                totalAmount,
                bookingInfoHolder.selectedRoom.priceAsset,
                withAssetCode = true
        )
    }

    private fun confirm() {
        resultSubject.onNext(Any())
    }
}