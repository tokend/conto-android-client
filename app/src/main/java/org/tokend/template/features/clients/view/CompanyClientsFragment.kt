package org.tokend.template.features.clients.view

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.fragment_company_clients.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.data.repository.BalancesRepository
import org.tokend.template.features.clients.repository.CompanyClientsRepository
import org.tokend.template.features.clients.view.adapter.CompanyClientItemsAdapter
import org.tokend.template.features.clients.view.adapter.CompanyClientListItem
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ColumnCalculator
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.HideFabScrollListener
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.fab.FloatingActionMenuAction
import org.tokend.template.view.util.fab.addActions

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

    private var selectMode = false

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

        val actionToolbar = action_toolbar as Toolbar
        actionToolbar.inflateMenu(R.menu.clients_action_mode)
        actionToolbar.setOnMenuItemClickListener {
            val emails = adapter.getSelected().joinToString(",\n") { it.email }
            Navigator.from(this@CompanyClientsFragment).openMassIssuance(
                    emails = emails,
                    requestCode = MASS_ISSUANCE_REQUEST
            )
            true
        }
        actionToolbar.navigationIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_close)
        actionToolbar.setNavigationOnClickListener {
            adapter.clearSelection()
        }
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(context!!, R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
    }

    private fun initList() {
        adapter = CompanyClientItemsAdapter(amountFormatter)
        layoutManager = GridLayoutManager(requireContext(), 1)
        updateListColumnsCount()

        clients_list.adapter = adapter
        clients_list.layoutManager = layoutManager
        clients_list.addOnScrollListener(HideFabScrollListener(menu_fab))
        clients_list.listenBottomReach({ adapter.getDataItemCount() }) {
            clientsRepository.loadMore() || clientsRepository.noMoreItems
        }

        adapter.onItemClick { _, item ->
            item.source?.also { Navigator.from(this).openCompanyClientDetails(it) }
        }

        adapter.onSelect { count ->
            if (count == 0) {
                selectMode = false
                updateActionToolbarVisibility()
                return@onSelect
            }
            activateSelectModeIfNeeded()
            (action_toolbar as Toolbar).title = requireContext().getString(R.string.template_selected, count)
        }

        error_empty_view.observeAdapter(adapter, R.string.no_clients)
        error_empty_view.setEmptyViewDenial { clientsRepository.isNeverUpdated }
        error_empty_view.setEmptyDrawable(R.drawable.ic_accounts)

        ElevationUtil.initScrollElevation(clients_list, appbar_elevation_view)
    }

    private fun initFab() {
        menu_fab.addActions(getFabActions())
        menu_fab.setClosedOnTouchOutside(true)
    }

    private fun getFabActions(): Collection<FloatingActionMenuAction> {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
        val navigator = Navigator.from(this)

        val actions = mutableListOf<FloatingActionMenuAction>()

        // Accept redemption.
        val balances = balancesRepository.itemsList
        actions.add(FloatingActionMenuAction(
                requireContext(),
                R.string.accept_redemption,
                R.drawable.ic_qr_code_scan_fab,
                {
                    navigator.openScanRedemption()
                },
                isEnabled = balances.any { it.asset.isOwnedBy(accountId) }
        ))

        // Invite.
        actions.add(FloatingActionMenuAction(
                requireContext(),
                R.string.invite_action,
                R.drawable.ic_accounts_add_fab,
                {
                    navigator.openInvitation()
                }
        ))

        // Issuance.
        actions.add(FloatingActionMenuAction(
                requireContext(),
                R.string.issuance_title,
                R.drawable.ic_issuance_white,
                {
                    navigator.openMassIssuance(requestCode = MASS_ISSUANCE_REQUEST)
                },
                isEnabled = balances.any { it.asset.isOwnedBy(accountId) }
        ))

        return actions
    }
    // endregion

    private fun updateActionToolbarVisibility() {
        action_toolbar.visibility = if (selectMode) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun activateSelectModeIfNeeded() {
        if (!selectMode) {
            selectMode = true
            updateActionToolbarVisibility()
        }
    }

    private fun subscribeToClients() {
        clientsRepository
                .loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loading ->
                    if (loading) {
                        if (clientsRepository.isOnFirstPage) {
                            loadingIndicator.show()
                        } else {
                            adapter.showLoadingFooter()
                        }
                    } else {
                        loadingIndicator.hide()
                        adapter.hideLoadingFooter()
                    }
                }
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

    override fun onBackPressed(): Boolean {
        if (menu_fab.isOpened) {
            menu_fab.close(true)
            return false
        } else if (adapter.hasSelection) {
            adapter.clearSelection()
            return false
        }
        return super.onBackPressed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                MASS_ISSUANCE_REQUEST -> adapter.clearSelection()
            }
        }
    }

    companion object {
        private val MASS_ISSUANCE_REQUEST = "mass_issuance".hashCode() and 0xffff
        val ID = "company_clients".hashCode().toLong()

        fun newInstance() = CompanyClientsFragment()
    }
}