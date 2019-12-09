package org.tokend.template.features.booking.view

import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.fragment_active_bookings.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.features.booking.model.ActiveBookingRecord
import org.tokend.template.features.booking.repository.ActiveBookingsRepository
import org.tokend.template.features.booking.view.adapter.ActiveBookingListItem
import org.tokend.template.features.booking.view.adapter.ActiveBookingsAdapter
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ColumnCalculator
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.formatter.DateFormatter

class ActiveBookingsFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject: BehaviorSubject<Toolbar> = BehaviorSubject.create()

    private val bookingsRepository: ActiveBookingsRepository
        get() = repositoryProvider.activeBookings()

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private lateinit var adapter: ActiveBookingsAdapter
    private lateinit var layoutManager: GridLayoutManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_active_bookings, container, false)
    }

    override fun onInitAllowed() {
        initToolbar()
        initSwipeRefresh()
        initList()
        initButtons()

        subscribeToBookings()

        update()
    }

    private fun initToolbar() {
        toolbar.title = getString(R.string.booking_title)
        toolbarSubject.onNext(toolbar)
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(context!!, R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
    }

    private val hideFabScrollListener =
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 2) {
                        add_fab.hide()
                    } else if (dy < -2 && add_fab.isEnabled) {
                        add_fab.show()
                    }
                }
            }

    private fun initList() {
        adapter = ActiveBookingsAdapter(DateFormatter(requireContext()))
        adapter.onItemClick { _, item ->
            item.source?.also(this::openBookingQr)
        }

        layoutManager = GridLayoutManager(requireContext(), 1)
        updateListColumnsCount()

        bookings_list.layoutManager = layoutManager
        bookings_list.adapter = adapter

        ElevationUtil.initScrollElevation(bookings_list, appbar_elevation_view)
        bookings_list.addOnScrollListener(hideFabScrollListener)

        error_empty_view.observeAdapter(adapter, R.string.no_active_bookings)
        error_empty_view.setEmptyViewDenial { bookingsRepository.isNeverUpdated }
    }

    private fun initButtons() {
        add_fab.setOnClickListener {
            Navigator.from(this).openBooking()
        }
    }

    private fun subscribeToBookings() {
        bookingsRepository.itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { displayActiveBookings() }
                .addTo(compositeDisposable)

        bookingsRepository.loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loadingIndicator.setLoading(it) }
                .addTo(compositeDisposable)

        bookingsRepository.errorsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { error ->
                    if (!adapter.hasData) {
                        error_empty_view.showError(error, errorHandlerFactory.getDefault()) {
                            update(true)
                        }
                    } else {
                        errorHandlerFactory.getDefault().handle(error)
                    }
                }
                .addTo(compositeDisposable)
    }

    private fun update(force: Boolean = false) {
        if (!force) {
            bookingsRepository.updateIfNotFresh()
        } else {
            bookingsRepository.update()
        }
    }

    private fun displayActiveBookings() {
        val items = bookingsRepository
                .itemsList
                .map(::ActiveBookingListItem)

        adapter.setData(items)
    }

    private fun openBookingQr(booking: ActiveBookingRecord) {
        Navigator.from(this).openQrShare(
                data = booking.reference,
                title = getString(R.string.booking_title),
                topText = booking.seatsCount.toString() + " " + resources.getQuantityString(
                        R.plurals.seat,
                        booking.seatsCount
                ),
                shareLabel = getString(R.string.share)
        )
    }

    private fun updateListColumnsCount() {
        layoutManager.spanCount = ColumnCalculator.getColumnCount(requireActivity())
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateListColumnsCount()
    }

    companion object {
        val ID = "active_bookings".hashCode().toLong()

        fun newInstance() = ActiveBookingsFragment()
    }
}