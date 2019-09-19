package org.tokend.template.features.swap.details.view

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_balance_change_details.*
import kotlinx.android.synthetic.main.activity_balance_details.*
import kotlinx.android.synthetic.main.activity_swap_details.*
import kotlinx.android.synthetic.main.activity_swap_details.swipe_refresh
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.sdk.factory.JsonApiToolsProvider
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.swap.details.logic.CloseIncomingSwapUseCase
import org.tokend.template.features.swap.details.logic.CloseOutgoingSwapUseCase
import org.tokend.template.features.swap.details.logic.ConfirmIncomingSwapUseCase
import org.tokend.template.features.swap.model.SwapRecord
import org.tokend.template.features.swap.model.SwapState
import org.tokend.template.features.swap.repository.SwapsRepository
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.balancechange.BalanceChangeMainDataView
import org.tokend.template.view.details.DetailsItem
import org.tokend.template.view.details.adapter.DetailsItemsAdapter
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.LocalizedName
import org.tokend.template.view.util.ProgressDialogFactory
import org.tokend.template.view.util.SwipeRefreshDependencyUtil
import java.math.BigDecimal

class SwapDetailsActivity : BaseActivity() {
    private val adapter = DetailsItemsAdapter()
    private lateinit var mainDataView: BalanceChangeMainDataView

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private lateinit var swapHash: String

    private val swapsRepository: SwapsRepository
        get() = repositoryProvider.swaps()

    private lateinit var swap: SwapRecord

    private var actionDisposable: Disposable? = null

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_swap_details)

        val swapHash = intent.getStringExtra(SWAP_HASH_EXTRA)
        if (swapHash == null) {
            finishWithMissingArgError(SWAP_HASH_EXTRA)
            return
        }
        this.swapHash = swapHash

        initToolbar()
        initMainDataView()
        initSwipeRefresh()

        details_list.layoutManager = LinearLayoutManager(this)
        details_list.adapter = adapter

        subscribeToSwaps()
    }

    private fun initToolbar() {
        toolbar.background = ColorDrawable(Color.WHITE)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun initMainDataView() {
        mainDataView = BalanceChangeMainDataView(appbar, amountFormatter)
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
        SwipeRefreshDependencyUtil.addDependency(swipe_refresh, appbar)
    }

    private fun subscribeToSwaps() {
        swapsRepository
                .itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    swap = it.first { s -> s.hash == swapHash }
                    displayDetails()
                }
                .addTo(compositeDisposable)

        swapsRepository
                .loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loadingIndicator.setLoading(it) }
                .addTo(compositeDisposable)

        swapsRepository
                .errorsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { errorHandlerFactory.getDefault().handle(it) }
                .addTo(compositeDisposable)
    }

    private fun update(force: Boolean = false) {
        if (!force) {
            swapsRepository.updateIfNotFresh()
        } else {
            swapsRepository.update()
        }
    }

    private fun displayDetails() {
        displayAmounts()
        displayCounterparty()
        displayState()
        displayOperationName()
        displayDate()

        initAction()
    }

    private fun displayAmounts() {
        mainDataView.displayNonZeroFee(BigDecimal.ZERO, swap.quoteAsset)
        displayToReceive()
        displayToPay()
    }

    private fun displayToReceive() {
        val asset = if (swap.isIncoming) swap.baseAsset else swap.quoteAsset
        val amount = if (swap.isIncoming) swap.baseAmount else swap.quoteAmount

        adapter.addOrUpdateItem(
                DetailsItem(
                        id = TO_PAY_ITEM_ID,
                        text = amountFormatter.formatAssetAmount(amount, asset, withAssetName = true),
                        hint = getString(R.string.to_receive),
                        icon = ContextCompat.getDrawable(this, R.drawable.ic_coins)
                )
        )
    }

    private fun displayToPay() {
        val asset = if (swap.isIncoming) swap.quoteAmount else swap.baseAmount
        val amount = if (swap.isIncoming) swap.quoteAsset else swap.baseAsset

        mainDataView.displayAmount(asset, amount, false)
    }

    private fun displayState() {
        adapter.addOrUpdateItem(
                DetailsItem(
                        id = STATE_ITEM_ID,
                        text = LocalizedName(this).forSwapState(swap.state, swap.isIncoming),
                        isEnabled = false
                )
        )
    }

    private fun displayOperationName() {
        mainDataView.displayOperationName(
                if (swap.isIncoming)
                    getString(R.string.incoming_swap)
                else
                    getString(R.string.balance_change_cause_swap)
        )
    }

    private fun displayCounterparty() {
        val email = swap.counterpartyEmail

        adapter.addOrUpdateItem(
                DetailsItem(
                        id = COUNTERPARTY_ITEM_ID,
                        text = email,
                        hint = getString(R.string.swap_counterparty),
                        icon = ContextCompat.getDrawable(this, R.drawable.ic_account)
                )
        )
    }

    private fun displayDate() {
        mainDataView.displayDate(swap.createdAt)
    }

    private fun initAction() {
        when {
            swap.state == SwapState.CREATED && swap.isIncoming ->
                initConfirmIncomingSwapAction()
            swap.state == SwapState.WAITING_FOR_CLOSE_BY_SOURCE && !swap.isIncoming ->
                initCloseOutgoingSwapAction()
            swap.state == SwapState.CAN_BE_RECEIVED_BY_DEST && swap.isIncoming ->
                initCloseIncomingSwapAction()
            else ->
                action_button.visibility = View.GONE
        }
    }

    private fun initConfirmIncomingSwapAction() {
        action_button.visibility = View.VISIBLE
        action_button.setText(R.string.confirm_action)
        action_button.setOnClickListener {
            val progress = ProgressDialogFactory.getDialog(this, R.string.processing_progress) {
                actionDisposable?.dispose()
            }

            actionDisposable = ConfirmIncomingSwapUseCase(
                    swap,
                    apiProvider,
                    repositoryProvider,
                    accountProvider,
                    JsonApiToolsProvider.getObjectMapper()
            )
                    .perform()
                    .compose(ObservableTransformers.defaultSchedulersCompletable())
                    .doOnSubscribe { progress.show() }
                    .doOnEvent { progress.dismiss() }
                    .subscribeBy(
                            onComplete = { progress.dismiss() },
                            onError = { errorHandlerFactory.getDefault().handle(it) }
                    )
        }
    }

    private fun initCloseOutgoingSwapAction() {
        action_button.visibility = View.VISIBLE
        action_button.setText(R.string.receive_swap_funds)
        action_button.setOnClickListener {
            val progress = ProgressDialogFactory.getDialog(this, R.string.processing_progress) {
                actionDisposable?.dispose()
            }

            actionDisposable = CloseOutgoingSwapUseCase(
                    swap,
                    apiProvider,
                    urlConfigProvider,
                    repositoryProvider,
                    accountProvider
            )
                    .perform()
                    .compose(ObservableTransformers.defaultSchedulersCompletable())
                    .doOnSubscribe { progress.show() }
                    .doOnEvent { progress.dismiss() }
                    .subscribeBy(
                            onComplete = { progress.dismiss() },
                            onError = { errorHandlerFactory.getDefault().handle(it) }
                    )
        }
    }

    private fun initCloseIncomingSwapAction() {
        action_button.visibility = View.VISIBLE
        action_button.setText(R.string.receive_swap_funds)
        action_button.setOnClickListener {
            val progress = ProgressDialogFactory.getDialog(this, R.string.processing_progress) {
                actionDisposable?.dispose()
            }

            actionDisposable = CloseIncomingSwapUseCase(
                    swap,
                    apiProvider,
                    repositoryProvider,
                    accountProvider
            )
                    .perform()
                    .compose(ObservableTransformers.defaultSchedulersCompletable())
                    .doOnSubscribe { progress.show() }
                    .doOnEvent { progress.dismiss() }
                    .subscribeBy(
                            onComplete = { progress.dismiss() },
                            onError = { errorHandlerFactory.getDefault().handle(it) }
                    )
        }
    }

    companion object {
        private const val COUNTERPARTY_ITEM_ID = 1L
        private const val TO_PAY_ITEM_ID = 2L
        private const val STATE_ITEM_ID = 3L
        private const val SWAP_HASH_EXTRA = "swap_hash"

        fun getBundle(swapHash: String) = Bundle().apply {
            putString(SWAP_HASH_EXTRA, swapHash)
        }
    }
}
