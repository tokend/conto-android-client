package org.tokend.template.features.movements.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.fragment_movements.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.data.model.BalanceRecord
import org.tokend.template.data.repository.BalancesRepository
import org.tokend.template.data.repository.balancechanges.BalanceChangesRepository
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.wallet.adapter.BalanceChangeListItem
import org.tokend.template.features.wallet.adapter.BalanceChangesAdapter
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.balancepicker.BalancePickerBottomDialog
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.LocalizedName
import java.math.BigDecimal

open class AssetMovementsFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject = BehaviorSubject.create<Toolbar>()

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    protected var currentBalance: BalanceRecord? = null
        set(value) {
            field = value
            onBalanceChanged()
        }

    private val balanceChangesRepository: BalanceChangesRepository
        get() = repositoryProvider.balanceChanges(currentBalance?.id)

    private lateinit var adapter: BalanceChangesAdapter

    private lateinit var balancePicker: BalancePickerBottomDialog

    private val requiredBalanceId: String? by lazy {
        arguments?.getString(BALANCE_ID_EXTRA)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_movements, container, false)
    }

    override fun onInitAllowed() {
        initToolbar()
        initSwipeRefresh()
        initBalanceSelection()
        initList()
    }

    // region Init
    protected open fun initToolbar() {
        val dropDownButton = ImageButton(requireContext()).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_arrow_drop_down))
            background = null
        }

        toolbar.addView(dropDownButton)

        toolbar.subtitle = "*"
        toolbar.title = "*"
        val toolbarTextViews = ArrayList<View>().apply {
            toolbar.findViewsWithText(this, "*", ViewGroup.FIND_VIEWS_WITH_TEXT)
        }

        listOf(*toolbarTextViews.toTypedArray(), dropDownButton).forEach {
            it.setOnClickListener { openBalancePicker() }
        }

        toolbar.title = getString(R.string.operations_history)

        ElevationUtil.initScrollElevation(history_list, appbar_elevation_view)

        toolbarSubject.onNext(toolbar)
    }

    protected open fun initBalanceSelection() {
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
        val filter: ((BalanceRecord) -> Boolean)? =
                if (!repositoryProvider.activeKyc().isActualOrForcedGeneral)
                    { it: BalanceRecord -> it.asset.isOwnedBy(accountId) }
                else
                    null

        balancePicker = object : BalancePickerBottomDialog(
                requireContext(),
                amountFormatter,
                balanceComparator,
                balancesRepository,
                balancesFilter = filter
        ) {
            // Available amounts are useless on this screen.
            override fun getAvailableAmount(assetCode: String,
                                            balance: BalanceRecord?): BigDecimal? = null
        }

        val items = balancePicker.getItemsToDisplay()

        if (items.isEmpty()) {
            error_empty_view.showEmpty(R.string.no_transaction_history)
            return
        }

        val balanceToSet = items
                .find { it.source?.id == requiredBalanceId }
                ?.source
                ?: items.firstOrNull()?.source

        if (balanceToSet != null) {
            currentBalance = balanceToSet
        }
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
    }

    protected open fun initList() {
        adapter = BalanceChangesAdapter(amountFormatter, false)
        adapter.onItemClick { _, item ->
            item.source?.let { Navigator.from(this).openBalanceChangeDetails(it) }
        }

        error_empty_view.setEmptyDrawable(R.drawable.ic_balance)
        error_empty_view.setPadding(0, 0, 0,
                resources.getDimensionPixelSize(R.dimen.quadra_margin))
        error_empty_view.observeAdapter(adapter, R.string.no_transaction_history)
        error_empty_view.setEmptyViewDenial { balanceChangesRepository.isNeverUpdated }

        history_list.adapter = adapter
        history_list.layoutManager = LinearLayoutManager(requireContext())

        history_list.listenBottomReach({ adapter.getDataItemCount() }) {
            balanceChangesRepository.loadMore() || balanceChangesRepository.noMoreItems
        }

        date_text_switcher.init(history_list, adapter)
    }
    // endregion

    private var historyDisposable: Disposable? = null

    private fun subscribeToHistory() {
        val disposable = CompositeDisposable()
        this.historyDisposable?.dispose()
        this.historyDisposable = disposable

        if (currentBalance == null) {
            return
        }

        balanceChangesRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { displayHistory() }
                .addTo(disposable)

        balanceChangesRepository
                .loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loading ->
                    if (loading) {
                        if (balanceChangesRepository.isOnFirstPage) {
                            loadingIndicator.show("transactions")
                        } else {
                            adapter.showLoadingFooter()
                        }
                    } else {
                        loadingIndicator.hide("transactions")
                        adapter.hideLoadingFooter()
                    }
                }
                .addTo(disposable)

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
                .addTo(disposable)

        disposable.addTo(compositeDisposable)
    }

    private fun displayHistory() {
        val localizedName = LocalizedName(requireContext())
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return

        adapter.setData(balanceChangesRepository.itemsList.map { balanceChange ->
            BalanceChangeListItem(balanceChange, accountId, localizedName)
        })
    }

    protected fun openBalancePicker() {
        balancePicker.show {
            currentBalance = it.source
        }
    }

    protected open fun onBalanceChanged() {
        toolbar.subtitle = currentBalance?.asset?.name ?: currentBalance?.assetCode
        history_list.resetBottomReachHandled()
        subscribeToHistory()
        update()
    }

    private fun update(force: Boolean = false) {
        if (!force) {
            balancesRepository.updateIfNotFresh()
            balanceChangesRepository.updateIfNotFresh()
        } else {
            balancesRepository.update()
            balanceChangesRepository.update()
        }
    }

    companion object {
        val ID = "asset_movements".hashCode().toLong()
        private const val BALANCE_ID_EXTRA = "balance_id"

        fun getBundle(balanceId: String? = null) = Bundle().apply {
            putString(BALANCE_ID_EXTRA, balanceId)
        }

        fun newInstance(bundle: Bundle): AssetMovementsFragment =
                AssetMovementsFragment().withArguments(bundle)
    }
}