package org.tokend.template.features.swap.create.view

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.activity_balance_change_confirmation.*
import kotlinx.android.synthetic.main.appbar_with_balance_change_main_data.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.layout_balance_change_main_data.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.swap.create.model.SwapRequest
import org.tokend.template.view.balancechange.BalanceChangeMainDataView
import org.tokend.template.view.details.DetailsItem
import org.tokend.template.view.details.adapter.DetailsItemsAdapter
import org.tokend.template.view.util.ElevationUtil
import java.math.BigDecimal

class SwapConfirmationActivity : BaseActivity() {
    private lateinit var request: SwapRequest
    private val adapter = DetailsItemsAdapter()
    private lateinit var mainDataView: BalanceChangeMainDataView

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_balance_change_confirmation)

        initToolbar()

        val request = intent.getSerializableExtra(SWAP_REQUEST_EXTRA) as? SwapRequest
        if (request == null) {
            finishWithMissingArgError(SWAP_REQUEST_EXTRA)
            return
        }
        this.request = request

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

    private fun displayDetails() {
        (top_info_text_view.layoutParams as? LinearLayout.LayoutParams)?.also {
            it.topMargin = 0
            top_info_text_view.layoutParams = it
        }

        mainDataView.displayOperationName(getString(R.string.balance_change_cause_swap))
        displayAmounts()
        displayCounterparty()
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

    private fun displayAmounts() {
        mainDataView.displayAmount(request.baseAmount, request.baseAsset, false)
        mainDataView.displayNonZeroFee(BigDecimal.ZERO, request.baseAsset)

        adapter.addData(
                DetailsItem(
                        text = amountFormatter.formatAssetAmount(
                                request.quoteAmount,
                                request.quoteAsset,
                                withAssetName = true
                        ),
                        hint = getString(R.string.to_receive),
                        icon = ContextCompat.getDrawable(this, R.drawable.ic_coins)
                )
        )
    }

    private fun displayCounterparty() {
        adapter.addData(
                DetailsItem(
                        text = request.destEmail,
                        hint = getString(R.string.swap_counterparty),
                        icon = ContextCompat.getDrawable(this, R.drawable.ic_account)
                )
        )
    }

    private fun confirm() {
        setResult(Activity.RESULT_OK)
    }

    companion object {
        private const val SWAP_REQUEST_EXTRA = "swap_request"

        fun getBundle(request: SwapRequest) = Bundle().apply {
            putSerializable(SWAP_REQUEST_EXTRA, request)
        }
    }
}
