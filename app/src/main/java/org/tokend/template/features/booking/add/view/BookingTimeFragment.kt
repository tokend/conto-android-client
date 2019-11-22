package org.tokend.template.features.booking.add.view

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.fragment_booking_time.*
import org.tokend.template.R
import org.tokend.template.features.booking.model.BookingTime
import org.tokend.template.fragments.BaseFragment
import java.text.SimpleDateFormat
import java.util.*

class BookingTimeFragment : BaseFragment() {
    private val resultSubject = PublishSubject.create<BookingTime>()
    val resultObservable: Observable<BookingTime> = resultSubject

    private val calendarFrom = Calendar.getInstance()
    private val calendarTo = Calendar.getInstance()
    private var millisDelta = 0L

    private val errorColor: Int by lazy {
        ContextCompat.getColor(requireContext(), R.color.error)
    }
    private val defaultTextColor: Int by lazy {
        ContextCompat.getColor(requireContext(), R.color.primary_text)
    }

    private var hasDateError: Boolean = false
        set(value) {
            field = value
            updateContinueAvailability()
        }

    private var canContinue: Boolean = false
        set(value) {
            field = value
            continue_button.isEnabled = value
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_booking_time, container, false)
    }

    override fun onInitAllowed() {
        initFields()
        initButtons()

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

    private fun initButtons() {
        continue_button.setOnClickListener {
            postResult()
        }
    }

    private fun preFillDates() {
        val now = Date().time / 1000
        val period = 30 * 60 // 30 minutes

        // So it sets start time to the nearest period start,
        // i.e to 12:30 if it's 12:10 now or to 13:00 if's 12:30 now, for example
        val nextPeriodStart = now - now % period + period

        val preferredBookingTime = 8 * 60 * 60 // Let it be 8 working hours

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
        updateError()
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

    private fun updateError() {
        hasDateError = millisDelta < 0
        if (hasDateError) {
            booking_from_day_text_view.setTextColor(errorColor)
            booking_from_hours_text_view.setTextColor(errorColor)
        } else {
            booking_from_day_text_view.setTextColor(defaultTextColor)
            booking_from_hours_text_view.setTextColor(defaultTextColor)
        }
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

    private fun updateContinueAvailability() {
        canContinue = !hasDateError
    }

    private fun postResult() {
        resultSubject.onNext(BookingTime(calendarFrom.time, calendarTo.time))
    }
}