package org.tokend.template.features.booking.add.rooms.view

import android.content.res.Configuration
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.fragment_booking_rooms.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import org.tokend.template.R
import org.tokend.template.features.booking.add.model.BookingInfoHolder
import org.tokend.template.features.booking.add.rooms.view.adapter.BookingRoomListItem
import org.tokend.template.features.booking.add.rooms.view.adapter.BookingRoomsAdapter
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.view.util.ColumnCalculator
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.formatter.DateFormatter

class BookingRoomsFragment: BaseFragment() {
    private lateinit var bookingInfoHolder: BookingInfoHolder

    private val adapter = BookingRoomsAdapter()
    private lateinit var layoutManager: GridLayoutManager

    private val resultSubject = PublishSubject.create<Any>()
    val resultObservable: Observable<Any> = resultSubject

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_booking_rooms, container, false)
    }

    override fun onInitAllowed() {
        bookingInfoHolder = requireActivity() as? BookingInfoHolder
                ?: throw IllegalStateException("Parent activity must hold booking info")

        initTopInfo()
        initList()

        displayRooms()
    }

    private fun initTopInfo() {
        val dateFormatter = DateFormatter(requireContext())
        val time = bookingInfoHolder.bookingTime
        val timeRange = "${dateFormatter.formatCompact(time.from, false)} – " +
                dateFormatter.formatCompact(time.to, false)

        booking_time_range_text_view.text = timeRange

        val availableSeats = bookingInfoHolder.availableSeats
        val availableSeatsString = getString(
                R.string.template_available,
                "$availableSeats ${requireContext().resources.getQuantityString(
                        R.plurals.seat,
                        availableSeats
                )}"
        )
        available_seats_text_view.text = availableSeatsString
    }

    private fun initList() {
        layoutManager = GridLayoutManager(requireContext(), 1)
        updateListColumnsCount()

        rooms_list.adapter = adapter
        rooms_list.layoutManager = layoutManager

        adapter.onItemClick { _, item ->
            // TODO
        }

        ElevationUtil.initScrollElevation(rooms_list, appbar_elevation_view)
    }

    private fun displayRooms() {
        val items = listOf(
                BookingRoomListItem(
                        "Big room #1",
                        25,
                        "https://conference-service.com.ua/_img/files/2012_05_25_19_10_35.jpg"
                ),
                BookingRoomListItem(
                        "Hall #1",
                        60,
                        "https://conference-service.com.ua/_img/files/2014_08_01_16_16_17.jpg"
                ),
                BookingRoomListItem(
                        "Small room",
                        25,
                        "https://conference-service.com.ua/_img/files/2019_08_22_11_28_23.jpg"
                )
        )
        adapter.setData(items)
    }

    private fun updateListColumnsCount() {
        layoutManager.spanCount = ColumnCalculator.getColumnCount(requireActivity())
        adapter.drawDividers = layoutManager.spanCount == 1
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateListColumnsCount()
    }
}