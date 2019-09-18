package org.tokend.template.features.swap.create.view

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.Asset
import org.tokend.template.data.repository.assets.AssetsRepository
import org.tokend.template.features.amountscreen.model.AmountInputResult
import org.tokend.template.features.send.model.PaymentRecipient
import org.tokend.template.features.swap.create.logic.CreateSwapRequestUseCase
import org.tokend.template.features.swap.create.model.SwapQuoteAmountAndCounterparty
import org.tokend.template.features.swap.create.model.SwapRequest
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.ProgressDialogFactory
import org.tokend.template.view.util.input.SoftInputUtil
import java.math.BigDecimal

class CreateSwapActivity : BaseActivity() {
    private val assetsRepository: AssetsRepository
        get() = repositoryProvider.assets()

    private lateinit var baseAmount: BigDecimal
    private lateinit var baseAsset: Asset
    private lateinit var quoteAmount: BigDecimal
    private lateinit var quoteAsset: Asset
    private lateinit var counterparty: PaymentRecipient

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.fragment_user_flow)

        initToolbar()

        toAmountScreen()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.create_swap)
    }

    private fun toAmountScreen() {
        val fragment = SwapAmountFragment()

        fragment
                .resultObservable
                .map { it as AmountInputResult }
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribeBy(
                        onNext = this::onAmountEntered,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)

        displayFragment(fragment, "amount", false)
    }

    private fun onAmountEntered(result: AmountInputResult) {
        this.baseAmount = result.amount
        this.baseAsset = result.asset

        ensureAssetsAndGoToQuoteAmountScreen()
    }

    private fun ensureAssetsAndGoToQuoteAmountScreen() {
        if (assetsRepository.isFresh) {
            toQuoteAmountAndCounterpartyScreen()
        } else {
            var disposable: Disposable? = null

            val progress = ProgressDialogFactory.getDialog(this, R.string.loading_data) {
                disposable?.dispose()
            }

            disposable = assetsRepository
                    .updateDeferred()
                    .compose(ObservableTransformers.defaultSchedulersCompletable())
                    .doOnSubscribe { progress.show() }
                    .doOnEvent { progress.dismiss() }
                    .subscribeBy(
                            onComplete = this::toQuoteAmountAndCounterpartyScreen,
                            onError = { errorHandlerFactory.getDefault().handle(it) }
                    )
                    .addTo(compositeDisposable)
        }
    }

    private fun toQuoteAmountAndCounterpartyScreen() {
        val fragment = SwapQuoteAmountAndCounterpartyFragment.newInstance(
                SwapQuoteAmountAndCounterpartyFragment.getBundle(baseAsset.code)
        )

        fragment
                .resultObservable
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribeBy(
                        onNext = this::onQuoteAmountAndCounterpartySelected,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)

        displayFragment(fragment, "quote_asset", true)
    }

    private fun onQuoteAmountAndCounterpartySelected(result: SwapQuoteAmountAndCounterparty) {
        this.quoteAmount = result.amount
        this.quoteAsset = result.asset
        this.counterparty = result.counterparty

        createRequestAndGoToConfirmation()
    }

    private fun createRequestAndGoToConfirmation() {
        SoftInputUtil.hideSoftInput(this)

        CreateSwapRequestUseCase(
                baseAmount,
                baseAsset,
                quoteAmount,
                quoteAsset,
                counterparty,
                repositoryProvider.balances(),
                walletInfoProvider
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .subscribeBy(
                        onSuccess = this::onSwapRequestCreated,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
    }

    private fun onSwapRequestCreated(request: SwapRequest) {
        Navigator.from(this).openSwapConfirmation(request, SWAP_CONFIRMATION_REQUEST)
    }

    private fun displayFragment(
            fragment: Fragment,
            tag: String,
            forward: Boolean?
    ) {
        supportFragmentManager.beginTransaction()
                .setTransition(
                        when (forward) {
                            true -> FragmentTransaction.TRANSIT_FRAGMENT_OPEN
                            false -> FragmentTransaction.TRANSIT_FRAGMENT_CLOSE
                            null -> FragmentTransaction.TRANSIT_NONE
                        }
                )
                .replace(R.id.fragment_container_layout, fragment)
                .addToBackStack(tag)
                .commit()
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount <= 1) {
            super.onBackPressed()
        } else {
            supportFragmentManager.popBackStackImmediate()
        }
    }

    companion object {
        private val SWAP_CONFIRMATION_REQUEST = "swap_confirmation".hashCode() and 0xffff
    }
}
