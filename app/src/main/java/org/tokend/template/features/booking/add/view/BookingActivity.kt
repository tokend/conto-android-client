package org.tokend.template.features.booking.add.view

import android.os.Bundle
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.fragment_user_flow.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.booking.add.logic.AvailableRoomsLoader
import org.tokend.template.features.booking.add.model.BookingInfoHolder
import org.tokend.template.features.booking.add.rooms.view.BookingRoomsFragment
import org.tokend.template.features.booking.model.BookingBusinessRecord
import org.tokend.template.features.booking.model.BookingRoom
import org.tokend.template.features.booking.model.BookingTime
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ProgressDialogFactory
import org.tokend.template.view.util.UserFlowFragmentDisplayer
import java.util.concurrent.TimeUnit

class BookingActivity : BaseActivity(), BookingInfoHolder {
    override lateinit var business: BookingBusinessRecord
    override lateinit var bookingTime: BookingTime
    override var seatsCount: Int = 0
    override var availableRooms: Collection<BookingRoom> = emptyList()
    override lateinit var selectedRoom: BookingRoom

    private val availableRoomsLoader: AvailableRoomsLoader by lazy {
        AvailableRoomsLoader(apiProvider, repositoryProvider.bookingBusiness())
    }

    private val fragmentDisplayer =
            UserFlowFragmentDisplayer(this, R.id.fragment_container_layout)

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.fragment_user_flow)

        initToolbar()
        initSwipeRefresh()

        ensureBusinessAndStart()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        title = getString(R.string.book_a_seat)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initSwipeRefresh() {
        swipe_refresh.isEnabled = false
    }

    private fun ensureBusinessAndStart() {
        var disposable: Disposable? = null

        val progress = ProgressDialogFactory.getDialog(this, R.string.loading_data) {
            disposable?.dispose()
            finish()
        }

        disposable = repositoryProvider
                .bookingBusiness()
                .ensureItem()
                .retryWhen { errors ->
                    errors.delay(2, TimeUnit.SECONDS)
                }
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .doOnSubscribe { progress.show() }
                .doOnEvent { _, _ -> progress.dismiss() }
                .subscribeBy(
                        onSuccess = this::onBusinessObtained,
                        onError = { it.printStackTrace() }
                )
    }

    private fun onBusinessObtained(business: BookingBusinessRecord) {
        this.business = business
        toTimeScreen()
    }

    private fun toTimeScreen() {
        val fragment = BookingTimeFragment()

        fragment.resultObservable
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe(this::onTimeSelected)
                .addTo(compositeDisposable)

        fragmentDisplayer.display(fragment, "time", null)
    }

    private fun onTimeSelected(time: BookingTime) {
        this.bookingTime = time
        toSeatCountInput()
    }

    private fun toSeatCountInput() {
        val fragment = BookingSeatsCountFragment()

        fragment.resultObservable
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe(this::onSeatCountEntered)
                .addTo(compositeDisposable)

        fragmentDisplayer.display(fragment, "seats", true)
    }

    private fun onSeatCountEntered(seatsCount: Int) {
        this.seatsCount = seatsCount
        loadAndShowAvailableRooms()
    }

    private fun loadAndShowAvailableRooms() {
        var disposable: Disposable? = null

        val dialog = ProgressDialogFactory.getDialog(this, R.string.loading_data) {
            disposable?.dispose()
        }

        disposable = availableRoomsLoader
                .getAvailableRooms(bookingTime, seatsCount)
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .doOnSubscribe { dialog.show() }
                .doOnEvent { _, _ -> dialog.dismiss() }
                .subscribeBy(
                        onSuccess = this::onAvailableRoomsLoaded,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
    }

    private fun onAvailableRoomsLoaded(rooms: List<BookingRoom>) {
        if (rooms.isEmpty()) {
            toastManager.short(R.string.error_no_booking_rooms_available)
            return
        }

        this.availableRooms = rooms
        toRoomSelection()
    }

    private fun toRoomSelection() {
        val fragment = BookingRoomsFragment()

        fragment.resultObservable
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe(this::onRoomSelected)
                .addTo(compositeDisposable)

        fragmentDisplayer.display(fragment, "rooms", true)
    }

    private fun onRoomSelected(room: BookingRoom) {
        this.selectedRoom = room
        toSummary()
    }

    private fun toSummary() {
        val fragment = BookingSummaryFragment()

        fragment.resultObservable
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { this.onSummaryConfirmed() }
                .addTo(compositeDisposable)

        fragmentDisplayer.display(fragment, "summary", true)
    }

    private fun onSummaryConfirmed() {
        createBooking()
    }

    private fun createBooking() {}


    override fun onBackPressed() {
        if (!fragmentDisplayer.tryPopBackStack()) {
            finish()
        }
    }
}
