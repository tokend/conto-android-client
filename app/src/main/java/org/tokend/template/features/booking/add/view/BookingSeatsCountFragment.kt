package org.tokend.template.features.booking.add.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.fragment_booking_seats_count.*
import org.jetbrains.anko.textColor
import org.tokend.template.R
import org.tokend.template.data.model.Asset
import org.tokend.template.features.booking.add.model.BookingInfoHolder
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.view.util.formatter.DateFormatter
import java.math.BigDecimal
import java.math.MathContext

class BookingSeatsCountFragment : BaseFragment() {
    private val resultSubject = PublishSubject.create<Int>()
    val resultObservable: Observable<Int> = resultSubject

    private lateinit var bookingInfoHolder: BookingInfoHolder

    private val availableSeatsCount: Int
        get() = bookingInfoHolder.selectedRoom.second
    private val seatPrice: BigDecimal
        get() = bookingInfoHolder.selectedRoom.first.price
    private val seatPriceAsset: Asset
        get() = bookingInfoHolder.selectedRoom.first.priceAsset

    private var canContinue: Boolean = false
        set(value) {
            field = value
            continue_button.isEnabled = value
        }

    private var defaultAvailableLabelColor: Int = 0
    private val errorColor: Int by lazy {
        ContextCompat.getColor(requireContext(), R.color.error)
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

        room_name_text_view.text = bookingInfoHolder.selectedRoom.first.name
    }

    private fun initCountView() {
        val availableSeatsString = getString(
                R.string.template_available,
                availableSeatsCount.toString()
        )
        available_text_view.text = availableSeatsString
        defaultAvailableLabelColor = available_text_view.currentTextColor

        seats_count_view.amountWrapper.apply {
            maxPlacesAfterComa = 0
            onAmountChanged { scaled, _ ->
                seatsCount = scaled.intValueExact()
            }
        }

        seats_count_view.apply {
            minAmount = BigDecimal.ONE
            maxAmount = availableSeatsCount.toBigDecimal()
            editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
    }

    private fun initButtons() {
        continue_button.setOnClickListener { postResult() }
    }

    private fun onSeatsCountChanged() {
        updateError()
        updateContinueAvailability()
        updateToPayAmount()
    }

    private fun updateError() {
        if (seatsCount > availableSeatsCount) {
            available_text_view.textColor = errorColor
        } else {
            available_text_view.textColor = defaultAvailableLabelColor
        }
    }

    private fun updateContinueAvailability() {
        canContinue = seatsCount in 1..availableSeatsCount
    }

    private fun updateToPayAmount() {
        if (seatsCount <= 0 || seatsCount > availableSeatsCount) {
            return
        }

        val toPay = seatsCount.toBigDecimal().multiply(seatPrice, MathContext.DECIMAL32)
        amount_text_view.text = amountFormatter.formatAssetAmount(
                toPay,
                seatPriceAsset,
                withAssetCode = true
        )
    }

    private fun postResult() {
        resultSubject.onNext(seatsCount)
    }
}