package org.tokend.template.features.clients.view

import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.clans.fab.FloatingActionButton
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.appbar.*
import kotlinx.android.synthetic.main.fragment_company_clients.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.data.repository.balances.BalancesRepository
import org.tokend.template.features.clients.repository.CompanyClientsRepository
import org.tokend.template.features.clients.view.adapter.CompanyClientItemsAdapter
import org.tokend.template.features.clients.view.adapter.CompanyClientListItem
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ColumnCalculator
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.LoadingIndicatorManager

class CompanyClientsFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject = BehaviorSubject.create<Toolbar>()

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val clientsRepository: CompanyClientsRepository
        get() = repositoryProvider.companyClients()

    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    private lateinit var adapter: CompanyClientItemsAdapter
    private lateinit var layoutManager: GridLayoutManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_company_clients, container, false)
    }

    override fun onInitAllowed() {
        initToolbar()
        initSwipeRefresh()
        initList()
        initFab()

        subscribeToClients()

        update()
    }

    // region Init
    private fun initToolbar() {
        toolbar.title = getString(R.string.clients_title)
        toolbarSubject.onNext(toolbar)
        ElevationUtil.initScrollElevation(appbar, appbar_elevation_view)
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(context!!, R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
    }

    private val hideFabScrollListener =
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 2) {
                        menu_fab.hideMenuButton(true)
                    } else if (dy < -2 && menu_fab.isEnabled) {
                        menu_fab.showMenuButton(true)
                    }
                }
            }

    private fun initList() {
        adapter = CompanyClientItemsAdapter(amountFormatter)
        layoutManager = GridLayoutManager(requireContext(), 1)
        updateListColumnsCount()
        clients_list.adapter = adapter
        clients_list.layoutManager = layoutManager
        clients_list.addOnScrollListener(hideFabScrollListener)
        adapter.onItemClick { _, item ->
            item.source?.also { Navigator.from(this).openCompanyClientDetails(it) }
        }

        error_empty_view.observeAdapter(adapter, R.string.no_clients)
        error_empty_view.setEmptyViewDenial { clientsRepository.isNeverUpdated }
        error_empty_view.setEmptyDrawable(R.drawable.ic_accounts)
    }

    private fun initFab() {
        val themedContext = ContextThemeWrapper(requireContext(), R.style.FloatingButtonMenuItem)
        val actions = mutableListOf<FloatingActionButton>()

        // Accept redemption.
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
        val balances = balancesRepository.itemsList
        if (balances.any { it.asset.ownerAccountId == accountId }) {
            actions.add(
                    FloatingActionButton(themedContext, null, R.style.FloatingButtonMenuItem)
                            .apply {
                                labelText = getString(R.string.accept_redemption)
                                setImageDrawable(ContextCompat.getDrawable(
                                        requireContext(),
                                        R.drawable.ic_qr_code_scan_fab)
                                )
                                setOnClickListener {
                                    Navigator.from(this@CompanyClientsFragment)
                                            .openScanRedemption()
                                    menu_fab.close(false)
                                }
                            }
            )
        }

        // Invite
        actions.add(
                FloatingActionButton(themedContext, null, R.style.FloatingButtonMenuItem)
                        .apply {
                            labelText = getString(R.string.invite_action)
                            setImageDrawable(ContextCompat.getDrawable(
                                    requireContext(),
                                    R.drawable.ic_accounts_add_fab)
                            )
                            setOnClickListener {
                                // TODO
                                menu_fab.close(false)
                            }
                        }
        )

        if (actions.isEmpty()) {
            menu_fab.visibility = View.GONE
            menu_fab.isEnabled = false
        } else {
            menu_fab.visibility = View.VISIBLE
            menu_fab.isEnabled = true
            actions.forEach(menu_fab::addMenuButton)
        }
    }
    // endregion

    private fun subscribeToClients() {
        clientsRepository
                .loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loadingIndicator.setLoading(it) }
                .addTo(compositeDisposable)

        clientsRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { displayClients() }
                .addTo(compositeDisposable)

        clientsRepository
                .errorsSubject
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

    private fun displayClients() {
        val clients = clientsRepository.itemsList
        adapter.setData(clients.map(::CompanyClientListItem))
    }

    private fun update(force: Boolean = false) {
        if (!force) {
            clientsRepository.updateIfNotFresh()
        } else {
            clientsRepository.update()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateListColumnsCount()
    }

    private fun updateListColumnsCount() {
        layoutManager.spanCount = ColumnCalculator.getColumnCount(requireActivity())
        adapter.drawDividers = layoutManager.spanCount == 1
    }

    companion object {
        val ID = "company_clients".hashCode().toLong()
    }
}