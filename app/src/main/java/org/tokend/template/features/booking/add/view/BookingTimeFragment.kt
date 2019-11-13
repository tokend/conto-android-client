package org.tokend.template.features.booking.add.view

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.Single
import io.reactivex.subjects.SingleSubject
import kotlinx.android.synthetic.main.fragment_booking_time.*
import org.tokend.template.R
import org.tokend.template.features.booking.add.model.BookingTime
import org.tokend.template.fragments.BaseFragment
import java.text.SimpleDateFormat
import java.util.*

class BookingTimeFragment : BaseFragment() {
    private val resultSubject = SingleSubject.create<BookingTime>()
    val resultSingle: Single<BookingTime> = resultSubject

    private val calendarFrom = Calendar.getInstance()
    private val calendarTo = Calendar.getInstance()
    private var millisDelta = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_booking_time, container, false)
    }

    override fun onInitAllowed() {
        initFields()
        preFillDates()
    }

    private fun initFields() {
        booking_from_day_text_view.setOnClickListener {
            openDatePicker(calendarFrom, this::adjustToDateOnFromDateChange)
        }
        booking_from_hours_text_view.setOnClickListener {
            openTimePicker(calendarFrom, this::adjustToDateOnFromDateChange)
        }

        booking_to_day_text_view.setOnClickListener {
            openDatePicker(calendarTo)
        }
        booking_to_hours_text_view.setOnClickListener {
            openTimePicker(calendarTo)
        }
    }

    private fun preFillDates() {
        val now = Date().time / 1000
        val period = 30 * 60 // 30 minutes
        val preferredBookingTime = 8 * 60 * 60 // 8 working hours, why not
        val nextPeriodStart = now - now % period + period

        calendarFrom.time = Date(nextPeriodStart * 1000L)
        calendarTo.time = Date((nextPeriodStart + preferredBookingTime) * 1000L)

        onDatesUpdated()
    }

    private fun adjustToDateOnFromDateChange() {
        if (millisDelta >= 0) {
            calendarTo.timeInMillis = calendarFrom.timeInMillis + millisDelta
        }
    }

    private fun onDatesUpdated() {
        millisDelta = calendarTo.timeInMillis - calendarFrom.timeInMillis
        displayDates()
    }

    private fun displayDates() {
        val dayFormat = SimpleDateFormat("EEE, dd MMMM", Locale.getDefault())
        val hourFormat = DateFormat.getTimeFormat(context)

        val dateFrom = calendarFrom.time
        booking_from_day_text_view.text = dayFormat.format(dateFrom).capitalize()
        booking_from_hours_text_view.text = hourFormat.format(dateFrom)

        val dateTo = calendarTo.time
        booking_to_day_text_view.text = dayFormat.format(dateTo).capitalize()
        booking_to_hours_text_view.text = hourFormat.format(dateTo)
    }

    private fun openDatePicker(calendarToUpdate: Calendar,
                               callback: () -> Unit = {}) {
        val datePickerListener = DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
            calendarToUpdate[Calendar.YEAR] = year
            calendarToUpdate[Calendar.MONTH] = monthOfYear
            calendarToUpdate[Calendar.DAY_OF_MONTH] = dayOfMonth
            callback()
            onDatesUpdated()
        }

        DatePickerDialog(
                requireContext(),
                R.style.AlertDialogStyle,
                datePickerListener,
                calendarToUpdate[Calendar.YEAR],
                calendarToUpdate[Calendar.MONTH],
                calendarToUpdate[Calendar.DAY_OF_MONTH]
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
            show()
        }
    }

    private fun openTimePicker(calendarToUpdate: Calendar,
                               callback: () -> Unit = {}) {
        val timePickerListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            calendarToUpdate.set(Calendar.HOUR_OF_DAY, hour)
            calendarToUpdate.set(Calendar.MINUTE, minute)
            callback()
            onDatesUpdated()
        }

        TimePickerDialog(
                requireContext(),
                R.style.AlertDialogStyle,
                timePickerListener,
                calendarToUpdate[Calendar.HOUR_OF_DAY],
                calendarToUpdate[Calendar.MINUTE],
                DateFormat.is24HourFormat(requireContext())
        ).apply {
            show()
        }
    }
}