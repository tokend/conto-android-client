package org.tokend.template.features.clients.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.fragment_company_clients.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider

class CompanyClientsFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject = BehaviorSubject.create<Toolbar>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_company_clients, container, false)
    }

    override fun onInitAllowed() {
        initToolbar()
        initSwipeRefresh()

        error_empty_view.showEmpty(R.string.no_clients)
        update()
    }

    // region Init
    private fun initToolbar() {
        toolbar.title = getString(R.string.clients_title)
        toolbarSubject.onNext(toolbar)
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(context!!, R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
    }
    // endregion

    private fun update(force: Boolean = false) {

    }

    companion object {
        val ID = "company_clients".hashCode().toLong()
    }
}