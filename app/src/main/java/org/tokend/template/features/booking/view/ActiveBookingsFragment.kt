package org.tokend.template.features.booking.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.fragment_active_bookings.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.view.util.ElevationUtil

class ActiveBookingsFragment: BaseFragment(), ToolbarProvider {
    override val toolbarSubject: BehaviorSubject<Toolbar> = BehaviorSubject.create()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_active_bookings, container, false)
    }

    override fun onInitAllowed() {
        initToolbar()
        initSwipeRefresh()
        initList()

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
        ElevationUtil.initScrollElevation(bookings_list, appbar_elevation_view)
        bookings_list.addOnScrollListener(hideFabScrollListener)

        error_empty_view.showEmpty(R.string.no_active_bookings)
    }

    private fun update(force: Boolean = false) {

    }

    companion object {
        val ID = "active_bookings".hashCode().toLong()
    }
}