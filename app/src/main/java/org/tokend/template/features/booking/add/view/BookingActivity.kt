package org.tokend.template.features.booking.add.view

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.toSingle
import kotlinx.android.synthetic.main.fragment_user_flow.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.booking.add.model.BookingInfoHolder
import org.tokend.template.features.booking.add.model.BookingTime
import org.tokend.template.features.booking.add.rooms.view.BookingRoomsFragment
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ProgressDialogFactory

class BookingActivity : BaseActivity(), BookingInfoHolder {
    override lateinit var bookingTime: BookingTime
    override var availableSeats: Int = 25

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
        loadFreeSeatsAndShowRooms()
    }

    private fun loadFreeSeatsAndShowRooms() {
        var disposable: Disposable? = null

        val dialog = ProgressDialogFactory.getDialog(this, R.string.loading_data) {
            disposable?.dispose()
        }

        disposable = ({
            Thread.sleep(1000)
            true
        }).toSingle()
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .doOnSubscribe { dialog.show() }
                .doOnEvent { _, _ -> dialog.dismiss() }
                .subscribeBy(
                        onSuccess = { onFreeSeatsLoaded()},
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
    }

    private fun onFreeSeatsLoaded() {
        toRoomSelection()
    }

    private fun toRoomSelection() {
        val fragment = BookingRoomsFragment()

        displayFragment(fragment, "rooms", true)
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
