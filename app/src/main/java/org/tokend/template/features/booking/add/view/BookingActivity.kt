package org.tokend.template.features.booking.add.view

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.fragment_user_flow.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.booking.add.logic.AvailableSeatsLoader
import org.tokend.template.features.booking.add.logic.RoomAvailableSeats
import org.tokend.template.features.booking.add.model.BookingInfoHolder
import org.tokend.template.features.booking.model.BookingTime
import org.tokend.template.features.booking.add.rooms.view.BookingRoomsFragment
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ProgressDialogFactory

class BookingActivity : BaseActivity(), BookingInfoHolder {
    override lateinit var bookingTime: BookingTime
    override lateinit var roomAvailableSeats: List<RoomAvailableSeats>
    override lateinit var selectedRoom: RoomAvailableSeats

    private val availableSeatsLoader: AvailableSeatsLoader by lazy {
        AvailableSeatsLoader(apiProvider)
    }

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.fragment_user_flow)

        initToolbar()
        initSwipeRefresh()

        toTimeScreen()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        title = getString(R.string.book_a_seat)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initSwipeRefresh() {
        swipe_refresh.isEnabled = false
    }

    private fun toTimeScreen() {
        val fragment = BookingTimeFragment()

        fragment.resultObservable
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe(this::onTimeSelected)
                .addTo(compositeDisposable)

        displayFragment(fragment, "time", null)
    }

    private fun onTimeSelected(time: BookingTime) {
        this.bookingTime = time
        loadAvailableSeatsAndShowRooms()
    }

    private fun loadAvailableSeatsAndShowRooms() {
        var disposable: Disposable? = null

        val dialog = ProgressDialogFactory.getDialog(this, R.string.loading_data) {
            disposable?.dispose()
        }

        disposable = availableSeatsLoader
                .getAvailableSeats(bookingTime)
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .doOnSubscribe { dialog.show() }
                .doOnEvent { _, _ -> dialog.dismiss() }
                .subscribeBy(
                        onSuccess = this::onAvailableSeatsLoaded,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
    }

    private fun onAvailableSeatsLoaded(seats: List<RoomAvailableSeats>) {
        roomAvailableSeats = seats
        toRoomSelection()
    }

    private fun toRoomSelection() {
        val fragment = BookingRoomsFragment()

        fragment.resultObservable
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe(this::onRoomSelected)
                .addTo(compositeDisposable)

        displayFragment(fragment, "rooms", true)
    }

    private fun onRoomSelected(room: RoomAvailableSeats) {
        selectedRoom = room
        toSeatCountInput()
    }

    private fun toSeatCountInput() {
        val fragment = BookingSeatsCountFragment()

        fragment.resultObservable
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe(this::onSeatCountEntered)
                .addTo(compositeDisposable)

        displayFragment(fragment, "seats", true)
    }

    private fun onSeatCountEntered(seatsCount: Int) {

    }

    private fun displayFragment(
            fragment: Fragment,
            tag: String,
            forward: Boolean?
    ) {
        supportFragmentManager.beginTransaction()
                .setTransition(
                        when (forward) {
                            true -> FragmentTransaction.TRANSIT_FRAGMENT_OPEN
                            false -> FragmentTransaction.TRANSIT_FRAGMENT_CLOSE
                            null -> FragmentTransaction.TRANSIT_NONE
                        }
                )
                .replace(R.id.fragment_container_layout, fragment)
                .addToBackStack(tag)
                .commit()
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount <= 1) {
            finish()
        } else {
            supportFragmentManager.popBackStackImmediate()
        }
    }
}
