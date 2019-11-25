package org.tokend.template.features.booking.add.view

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.fragment_booking_seats_count.*
import org.tokend.template.R
import org.tokend.template.features.booking.add.model.BookingInfoHolder
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.view.util.formatter.DateFormatter
import java.math.BigDecimal

class BookingSeatsCountFragment : BaseFragment() {
    private val resultSubject = PublishSubject.create<Int>()
    val resultObservable: Observable<Int> = resultSubject

    private lateinit var bookingInfoHolder: BookingInfoHolder

    private var canContinue: Boolean = false
        set(value) {
            field = value
            continue_button.isEnabled = value
        }

    private var seatsCount: Int = 0
        set(value) {
            val isTheSame = field == value
            field = value
            if (!isTheSame) {
                onSeatsCountChanged()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_booking_seats_count, container, false)
    }

    override fun onInitAllowed() {
        bookingInfoHolder = requireActivity() as? BookingInfoHolder
                ?: throw IllegalStateException("Parent activity must hold booking info")

        initTopInfo()
        initCountView()
        initButtons()
    }

    private fun initTopInfo() {
        val dateFormatter = DateFormatter(requireContext())
        val time = bookingInfoHolder.bookingTime
        val timeRange = "${dateFormatter.formatCompact(time.from, false)} – " +
                dateFormatter.formatCompact(time.to, false)

        booking_time_range_text_view.text = timeRange
    }

    private fun initCountView() {
        seats_count_view.amountWrapper.apply {
            maxPlacesAfterComa = 0
            setAmount(bookingInfoHolder.seatsCount.toBigDecimal())
            onAmountChanged { scaled, _ ->
                seatsCount = scaled.intValueExact()
            }
        }

        seats_count_view.apply {
            minAmount = BigDecimal.ONE
            editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
    }

    private fun initButtons() {
        continue_button.setOnClickListener { postResult() }
    }

    private fun onSeatsCountChanged() {
        updateContinueAvailability()
    }

    private fun updateContinueAvailability() {
        canContinue = seatsCount > 0
    }

    private fun postResult() {
        resultSubject.onNext(seatsCount)
    }
}