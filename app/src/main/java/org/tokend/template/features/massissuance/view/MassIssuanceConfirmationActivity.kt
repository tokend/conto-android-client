package org.tokend.template.features.massissuance.view

import android.app.Activity
import android.content.Intent
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
import org.jetbrains.anko.enabled
import org.jetbrains.anko.onClick
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.massissuance.logic.ConfirmMassIssuanceRequestUseCase
import org.tokend.template.features.massissuance.model.MassIssuanceRequest
import org.tokend.template.logic.transactions.TxManager
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.balancechange.BalanceChangeMainDataView
import org.tokend.template.view.details.DetailsItem
import org.tokend.template.view.details.adapter.DetailsItemsAdapter
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.ProgressDialogFactory
import java.math.BigDecimal

class MassIssuanceConfirmationActivity : BaseActivity() {

    private lateinit var request: MassIssuanceRequest
    private val adapter = DetailsItemsAdapter()
    private lateinit var mainDataView: BalanceChangeMainDataView

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_balance_change_confirmation)

        initToolbar()

        request = (intent.getSerializableExtra(MASS_ISSUANCE_REQUEST_EXTRA) as? MassIssuanceRequest)
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

    private fun displayDetails() {
        (top_info_text_view.layoutParams as? LinearLayout.LayoutParams)?.also {
            it.topMargin = 0
            top_info_text_view.layoutParams = it
        }

        mainDataView.displayOperationName("Mass issuance")

        displayAmount()
        displayRecipients()
    }

    private fun displayAmount() {
        val amount = request.amount * BigDecimal(request.recipients.size)
        mainDataView.displayAmount(amount, request.asset, false)
    }

    private fun displayRecipients() {
        val icon = ContextCompat.getDrawable(this, R.drawable.ic_email)
        adapter.setData(
                request.recipients.map {
                    DetailsItem(it.email, icon = icon, singleLineText = true)
                }
        )
    }

    private fun initConfirmButton() {
        confirm_button.apply {
            onClick { confirm() }

            enabled = false
            postDelayed({
                if (!isFinishing) {
                    enabled = true
                }
            }, 1500)
        }
    }

    private fun confirm() {
        val progress = ProgressDialogFactory.getDialog(this)

        ConfirmMassIssuanceRequestUseCase(
                request,
                accountProvider,
                TxManager(apiProvider),
                repositoryProvider
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
                            toastManager.long(R.string.successfully_issued)
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
        const val MASS_ISSUANCE_REQUEST_EXTRA = "mass_issuance_request"

        fun getBundle(request: MassIssuanceRequest) = Bundle().apply {
            putSerializable(MASS_ISSUANCE_REQUEST_EXTRA, request)
        }
    }
}