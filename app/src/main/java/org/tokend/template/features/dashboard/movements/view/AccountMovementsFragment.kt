package org.tokend.template.features.dashboard.movements.view

import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.appbar.*
import kotlinx.android.synthetic.main.fragment_movements.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.features.history.storage.BalanceChangesRepository
import org.tokend.template.features.history.view.adapter.BalanceChangeListItem
import org.tokend.template.features.history.view.adapter.BalanceChangesAdapter
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.navigation.Navigator
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.LocalizedName

class AccountMovementsFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject = BehaviorSubject.create<Toolbar>()

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val balanceChangesRepository: BalanceChangesRepository
        get() = repositoryProvider.balanceChanges(null)

    private lateinit var adapter: BalanceChangesAdapter

    private val allowToolbar: Boolean by lazy {
        arguments?.getBoolean(ALLOW_TOOLBAR_EXTRA, true) ?: true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_movements, container, false)
    }

    override fun onInitAllowed() {
        initToolbar()
        initSwipeRefresh()
        initHistory()
        ElevationUtil.initScrollElevation(history_list, appbar_elevation_view)

        subscribeToHistory()

        update()
    }

    // region Init
    private fun initToolbar() {
        if (allowToolbar) {
            toolbar.title = getString(R.string.operations_history)

            ElevationUtil.initScrollElevation(history_list, appbar_elevation_view)
        } else {
            appbar_elevation_view.visibility = View.GONE
            appbar.visibility = View.GONE
        }
        toolbarSubject.onNext(toolbar)
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(context!!, R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
    }

    private fun initHistory() {
        adapter = BalanceChangesAdapter(amountFormatter, true)
        adapter.onItemClick { _, item ->
            item.source?.let { Navigator.from(this).openBalanceChangeDetails(it) }
        }

        error_empty_view.setEmptyDrawable(R.drawable.ic_balance)
        error_empty_view.setPadding(0, 0, 0,
                resources.getDimensionPixelSize(R.dimen.quadra_margin))
        error_empty_view.observeAdapter(adapter, R.string.no_transaction_history)
        error_empty_view.setEmptyViewDenial { balanceChangesRepository.isNeverUpdated }

        history_list.adapter = adapter
        history_list.layoutManager = LinearLayoutManager(context!!)
        history_list.setHasFixedSize(true)

        history_list.listenBottomReach({ adapter.getDataItemCount() }) {
            balanceChangesRepository.loadMore() || balanceChangesRepository.noMoreItems
        }

        date_text_switcher.init(history_list, adapter)
    }
    // endregion

    private fun subscribeToHistory() {
        balanceChangesRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { displayHistory() }
                .addTo(compositeDisposable)

        balanceChangesRepository
                .loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loading ->
                    if (loading) {
                        if (!balanceChangesRepository.isOnFirstPage) {
                            adapter.showLoadingFooter()
                        }

                        if (balanceChangesRepository.isOnFirstPage
                                || balanceChangesRepository.isLoadingTopPages) {
                            loadingIndicator.show("transactions")
                        }
                    } else {
                        loadingIndicator.hide("transactions")
                        adapter.hideLoadingFooter()
                    }
                }
                .addTo(compositeDisposable)

        balanceChangesRepository
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

    private fun displayHistory() {
        val localizedName = LocalizedName(requireContext())
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return

        adapter.setData(balanceChangesRepository.itemsList.map { balanceChange ->
            BalanceChangeListItem(balanceChange, accountId, localizedName)
        })

        history_list.resetBottomReachHandled()
    }

    private fun update(force: Boolean = false) {
        if (!force) {
            balanceChangesRepository.updateIfNotFresh()
        } else {
            balanceChangesRepository.update()
        }
    }

    companion object {
        val ID = "account_movements".hashCode().toLong()
        private const val ALLOW_TOOLBAR_EXTRA = "allow_toolbar"

        fun newInstance(bundle: Bundle): AccountMovementsFragment {
            val fragment = AccountMovementsFragment()
            fragment.arguments = bundle
            return fragment
        }

        fun getBundle(allowToolbar: Boolean) = Bundle().apply {
            putBoolean(ALLOW_TOOLBAR_EXTRA, allowToolbar)
        }
    }
}