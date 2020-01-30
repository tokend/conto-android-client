package org.tokend.template.features.assets.sell.view

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v4.content.ContextCompat
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.fragment_user_flow.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.sdk.utils.BigDecimalUtil
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.Asset
import org.tokend.template.data.model.BalanceRecord
import org.tokend.template.data.repository.BalancesRepository
import org.tokend.template.extensions.getBigDecimalExtra
import org.tokend.template.features.amountscreen.model.AmountInputResult
import org.tokend.template.features.assets.sell.logic.CreateMarketplaceSellRequestUseCase
import org.tokend.template.features.assets.sell.model.MarketplaceSellInfoHolder
import org.tokend.template.features.assets.sell.model.MarketplaceSellRequest
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.LoadingIndicatorManager
import org.tokend.template.view.util.ProgressDialogFactory
import org.tokend.template.view.util.UserFlowFragmentDisplayer
import java.math.BigDecimal

class SellAssetOnMarketplaceActivity : BaseActivity(), MarketplaceSellInfoHolder {
    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val fragmentDisplayer =
            UserFlowFragmentDisplayer(this, R.id.fragment_container_layout)

    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    private val balanceId: String by lazy {
        intent.getStringExtra(BALANCE_ID_EXTRA)
    }

    override lateinit var balance: BalanceRecord
    override lateinit var amount: BigDecimal
    override var price: BigDecimal = BigDecimal.ZERO
    override lateinit var priceAsset: Asset
    override lateinit var cardNumber: String

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.fragment_user_flow)

        updateBalance()

        initAmount()
        initToolbar()
        initSwipeRefresh()

        subscribeToBalances()

        toAmountInput()
    }

    private fun initAmount() {
        val requiredAmount = intent.getBigDecimalExtra(AMOUNT_EXTRA)
        amount =
                if (requiredAmount.signum() != 0)
                    requiredAmount
                else
                    requiredAmount.max(balance.available)
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        setTitle(R.string.sell)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent))
        swipe_refresh.setOnRefreshListener { balancesRepository.update() }
    }

    private fun subscribeToBalances() {
        balancesRepository.itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { updateBalance() }
                .addTo(compositeDisposable)

        balancesRepository.loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { loadingIndicator.setLoading(it, "balances") }
                .addTo(compositeDisposable)

        balancesRepository.errorsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    errorHandlerFactory.getDefault().handle(it)
                }
                .addTo(compositeDisposable)
    }

    private fun updateBalance() {
        val balance = balancesRepository.itemsList
                .find { it.id == balanceId }

        if (balance != null) {
            this.balance = balance
        } else {
            errorHandlerFactory.getDefault().handle(IllegalStateException("No balance found"))
            finish()
        }
    }

    private fun toAmountInput() {
        val fragment = MarketplaceSellAmountFragment.newInstance()

        fragment.resultObservable
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribeBy(
                        onNext = this::onAmountEntered,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)

        fragmentDisplayer.display(fragment, "amount", true)
    }

    private fun onAmountEntered(amount: BigDecimal) {
        this.amount = amount
        toPriceInput()
    }

    private fun toPriceInput() {
        val fragment = MarketplaceSellPriceFragment.newInstance()

        fragment.resultObservable
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribeBy(
                        onNext = this::onPriceEntered,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)

        fragmentDisplayer.display(fragment, "price", true)
    }

    private fun onPriceEntered(price: AmountInputResult) {
        this.price = price.amount
        this.priceAsset = price.asset
        toCardNumberInput()
    }

    private fun toCardNumberInput() {
        val fragment = MarketplaceSellCardNumberFragment.newInstance()

        fragment.resultObservable
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribeBy(
                        onNext = this::onCardNumberEntered,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)

        fragmentDisplayer.display(fragment, "card", true)
    }

    private fun onCardNumberEntered(cardNumber: String) {
        this.cardNumber = cardNumber
        createAndConfirmSellRequest()
    }

    private fun createAndConfirmSellRequest() {
        var disposable: Disposable? = null

        val progress = ProgressDialogFactory.getDialog(this, R.string.loading_data) {
            disposable?.dispose()
        }

        disposable = CreateMarketplaceSellRequestUseCase(
                amount = amount,
                balance = balance,
                priceAsset = priceAsset,
                price = price,
                cardNumber = cardNumber,
                apiProvider = apiProvider,
                walletInfoProvider = walletInfoProvider
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .doOnSubscribe { progress.show() }
                .doOnEvent { _, _ -> progress.dismiss() }
                .subscribeBy(
                        onSuccess = this::onSellRequestCreated,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
    }

    private fun onSellRequestCreated(request: MarketplaceSellRequest) {
        Navigator.from(this)
                .openMarketplaceSellConfirmation(request, SELL_CONFIRMATION_REQUEST)
    }

    override fun onBackPressed() {
        if (!fragmentDisplayer.tryPopBackStack()) {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK && requestCode == SELL_CONFIRMATION_REQUEST) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    companion object {
        private const val BALANCE_ID_EXTRA = "balance_id"
        private const val AMOUNT_EXTRA = "amount"
        private val SELL_CONFIRMATION_REQUEST = "sell".hashCode() and 0xffff

        fun getBundle(balanceId: String,
                      amount: BigDecimal) = Bundle().apply {
            putString(BALANCE_ID_EXTRA, balanceId)
            putString(AMOUNT_EXTRA, BigDecimalUtil.toPlainString(amount))
        }
    }
}