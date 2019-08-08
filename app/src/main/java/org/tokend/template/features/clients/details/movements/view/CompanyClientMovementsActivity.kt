package org.tokend.template.features.clients.details.movements.view

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_company_client_movements.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.repository.balancechanges.BalanceChangesRepository
import org.tokend.template.extensions.getNullableStringExtra
import org.tokend.template.features.clients.model.CompanyClientRecord
import org.tokend.template.features.wallet.adapter.BalanceChangeListItem
import org.tokend.template.features.wallet.adapter.BalanceChangesAdapter
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.LocalizedName

class CompanyClientMovementsActivity : BaseActivity() {
    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private lateinit var adapter: BalanceChangesAdapter

    private lateinit var client: CompanyClientRecord
    private lateinit var assetCode: String

    private val balanceChangesRepository: BalanceChangesRepository
        get() = repositoryProvider.companyClientBalanceChanges(client.accountId!!, assetCode)

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_company_client_movements)

        val client = (intent.getSerializableExtra(CLIENT_EXTRA) as? CompanyClientRecord)
        if (client == null) {
            errorHandlerFactory.getDefault().handle(IllegalArgumentException("Invalid $CLIENT_EXTRA"))
            finish()
            return
        }
        this.client = client

        val assetCode = intent.getNullableStringExtra(ASSET_CODE_EXTRA)
        if (assetCode == null) {
            errorHandlerFactory.getDefault().handle(IllegalArgumentException("Invalid $ASSET_CODE_EXTRA"))
            finish()
            return
        }
        this.assetCode = assetCode

        initToolbar()
        initSwipeRefresh()
        initList()

        subscribeToBalanceChanges()

        update()
    }

    // region Init
    private fun initToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val asset = client
                .balances
                .find { it.asset.code == assetCode }
                ?.asset

        title = getString(
                R.string.template_asset_movements,
                asset?.name ?: assetCode
        )

        toolbar.subtitle = client.email
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent))
        swipe_refresh.setOnRefreshListener {
            update(force = true)
        }
    }

    private fun initList() {
        adapter = BalanceChangesAdapter(amountFormatter, false)

        error_empty_view.apply {
            setEmptyDrawable(R.drawable.empty_view_wallet)
            observeAdapter(adapter, R.string.no_transaction_history)
            setEmptyViewDenial { balanceChangesRepository.isNeverUpdated }
        }

        history_list.adapter = adapter
        history_list.layoutManager = LinearLayoutManager(this)

        history_list.listenBottomReach({ adapter.getDataItemCount() }) {
            balanceChangesRepository.loadMore() || balanceChangesRepository.noMoreItems
        }

        date_text_switcher.init(history_list, adapter)

        ElevationUtil.initScrollElevation(history_list, appbar_elevation_view)
    }
    // endregion

    private fun subscribeToBalanceChanges() {
        balanceChangesRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { displayHistory() }
                .addTo(compositeDisposable)

        balanceChangesRepository.loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loading ->
                    if (loading) {
                        if (balanceChangesRepository.isOnFirstPage) {
                            loadingIndicator.show("history")
                        } else {
                            adapter.showLoadingFooter()
                        }
                    } else {
                        loadingIndicator.hide("history")
                        adapter.hideLoadingFooter()
                    }
                }
                .addTo(compositeDisposable)

        balanceChangesRepository.errorsSubject
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
        val localizedName = LocalizedName(this)

        adapter.setData(balanceChangesRepository.itemsList.map { balanceChange ->
            BalanceChangeListItem(balanceChange, client.accountId!!, localizedName)
        })
    }

    private fun update(force: Boolean = false) {
        if (!force) {
            balanceChangesRepository.updateIfNotFresh()
        } else {
            balanceChangesRepository.update()
        }
    }

    companion object {
        private const val CLIENT_EXTRA = "client"
        private const val ASSET_CODE_EXTRA = "asset_code"

        fun getBundle(client: CompanyClientRecord,
                      assetCode: String) = Bundle().apply {
            putSerializable(CLIENT_EXTRA, client)
            putString(ASSET_CODE_EXTRA, assetCode)
        }
    }
}
