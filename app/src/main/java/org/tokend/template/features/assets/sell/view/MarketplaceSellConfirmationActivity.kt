package org.tokend.template.features.assets.sell.view

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.widget.LinearLayout
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_balance_change_confirmation.*
import kotlinx.android.synthetic.main.appbar_with_balance_change_main_data.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.layout_balance_change_main_data.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.assets.sell.logic.ConfirmMarketplaceSellRequestUseCase
import org.tokend.template.features.assets.sell.model.MarketplaceSellPaymentMethodListItem
import org.tokend.template.features.assets.sell.model.MarketplaceSellRequest
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.balancechange.BalanceChangeMainDataView
import org.tokend.template.view.details.DetailsItem
import org.tokend.template.view.details.adapter.DetailsItemsAdapter
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.ProgressDialogFactory
import java.math.BigDecimal

class MarketplaceSellConfirmationActivity : BaseActivity() {
    private lateinit var request: MarketplaceSellRequest
    private val adapter = DetailsItemsAdapter()
    private lateinit var mainDataView: BalanceChangeMainDataView

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_balance_change_confirmation)

        initToolbar()

        request =
                (intent.getSerializableExtra(SELL_REQUEST_EXTRA) as? MarketplaceSellRequest)
                        ?: return

        mainDataView = BalanceChangeMainDataView(appbar, amountFormatter)

        details_list.layoutManager = LinearLayoutManager(this)
        details_list.adapter = adapter

        displayDetails()
        initConfirmButton()
        ElevationUtil.initScrollElevation(details_list, appbar_elevation_view)
    }

    private fun initToolbar() {
        toolbar.background = ColorDrawable(Color.WHITE)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun initConfirmButton() {
        confirm_button.apply {
            setOnClickListener { confirm() }

            // Prevent accidental click.
            isEnabled = false
            postDelayed({
                if (!isFinishing) {
                    isEnabled = true
                }
            }, 1500)
        }
    }

    // region Details
    private fun displayDetails() {
        (top_info_text_view.layoutParams as? LinearLayout.LayoutParams)?.also {
            it.topMargin = 0
            top_info_text_view.layoutParams = it
        }

        mainDataView.displayOperationName(getString(R.string.sell))
        mainDataView.displayAmount(request.amount, request.asset, isReceived = false)
        mainDataView.displayNonZeroFee(BigDecimal.ZERO, request.asset)

        adapter.addData(
                DetailsItem(
                        hint = getString(R.string.price),
                        icon = ContextCompat.getDrawable(this, R.drawable.ic_price),
                        text = getString(
                                R.string.template_price_one_equals,
                                request.asset.run { name ?: code },
                                amountFormatter.formatAssetAmount(
                                        request.price,
                                        request.priceAsset,
                                        withAssetCode = true
                                )
                        )
                )
        )

        displayPaymentMethods()
    }

    private fun displayPaymentMethods() {
        val coinsIcon = ContextCompat.getDrawable(this, R.drawable.ic_coins)
        adapter.addData(
                request.paymentMethods
                        .map { MarketplaceSellPaymentMethodListItem(it, this) }
                        .mapIndexed { i, paymentMethod ->
                            DetailsItem(
                                    header =
                                    if (i == 0)
                                        getString(R.string.payment_methods)
                                    else
                                        null,
                                    text = paymentMethod.destination,
                                    hint = paymentMethod.name,
                                    icon = coinsIcon
                            )
                        }
        )
    }
    // endregion

    private fun confirm() {
        val progress = ProgressDialogFactory.getDialog(this)

        ConfirmMarketplaceSellRequestUseCase(
                request = request,
                repositoryProvider = repositoryProvider,
                apiProvider = apiProvider,
                accountProvider = accountProvider
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .doOnSubscribe {
                    progress.show()
                }
                .doOnTerminate {
                    progress.dismiss()
                }
                .subscribeBy(
                        onComplete = {
                            progress.dismiss()
                            toastManager.long(R.string.offer_created)
                            finishWithSuccess()
                        },
                        onError = {
                            errorHandlerFactory.getDefault().handle(it)
                        }
                )
    }

    private fun finishWithSuccess() {
        setResult(Activity.RESULT_OK)
        finish()
    }

    companion object {
        private const val SELL_REQUEST_EXTRA = "sell_request"

        fun getBundle(request: MarketplaceSellRequest) = Bundle().apply {
            putSerializable(SELL_REQUEST_EXTRA, request)
        }
    }
}