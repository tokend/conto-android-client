package org.tokend.template.features.redeem.accept.view

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.widget.LinearLayout
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_balance_change_confirmation.*
import kotlinx.android.synthetic.main.appbar_with_balance_change_main_data.*
import kotlinx.android.synthetic.main.include_appbar_elevation.*
import kotlinx.android.synthetic.main.layout_balance_change_main_data.*
import kotlinx.android.synthetic.main.toolbar.*
import org.jetbrains.anko.onClick
import org.tokend.sdk.utils.extentions.decodeBase64
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.assets.model.AssetRecord
import org.tokend.template.features.balances.storage.BalancesRepository
import org.tokend.template.features.redeem.accept.logic.ConfirmRedemptionRequestUseCase
import org.tokend.template.features.redeem.model.RedemptionRequest
import org.tokend.template.logic.TxManager
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.errorhandler.CompositeErrorHandler
import org.tokend.template.util.errorhandler.ErrorHandler
import org.tokend.template.util.errorhandler.SimpleErrorHandler
import org.tokend.template.view.balancechange.BalanceChangeMainDataView
import org.tokend.template.view.details.DetailsItem
import org.tokend.template.view.details.adapter.DetailsItemsAdapter
import org.tokend.template.view.dialog.CopyDataDialogFactory
import org.tokend.template.view.util.ElevationUtil
import org.tokend.template.view.util.ProgressDialogFactory
import java.math.BigDecimal

class ConfirmRedemptionActivity : BaseActivity() {
    private var emailLoadingFinished: Boolean = false

    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    private val asset: AssetRecord?
        get() = balancesRepository.itemsList
                .find { it.id == balanceId }
                ?.asset

    private val formattedId: String by lazy {
        val id = request.sourceAccountId
        "${id.substring(0..3)}…${id.substring(id.length - 4 until id.length)}"
    }

    private lateinit var balanceId: String
    private lateinit var request: RedemptionRequest

    private val adapter = DetailsItemsAdapter()
    private lateinit var mainDataView: BalanceChangeMainDataView

    private var senderEmail: String? = null

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_balance_change_confirmation)

        val balanceId = intent.getStringExtra(EXTRA_BALANCE_ID)
        if (balanceId == null) {
            finishWithMissingArgError(EXTRA_BALANCE_ID)
            return
        }
        this.balanceId = balanceId

        val requestString = intent.getStringExtra(EXTRA_REDEMPTION)
        if (requestString == null) {
            finishWithMissingArgError(EXTRA_REDEMPTION)
            return
        }

        try {
            val networkParams = repositoryProvider
                    .systemInfo()
                    .item
                    ?.toNetworkParams()
                    ?: throw IllegalArgumentException("No loaded network params found")

            request = RedemptionRequest.fromSerialized(networkParams, requestString.decodeBase64())
        } catch (e: Exception) {
            finishWithError(e)
            return
        }

        initViews()
        displayDetails()
    }

    private fun initViews() {
        initToolbar()
        initDataView()
        initDetailsList()
        initConfirmationButton()
    }

    private fun initToolbar() {
        toolbar.background = ColorDrawable(Color.WHITE)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationOnClickListener { finish() }
        ElevationUtil.initScrollElevation(details_list, appbar_elevation_view)
    }

    private fun initDataView() {
        (top_info_text_view.layoutParams as? LinearLayout.LayoutParams)?.also {
            it.topMargin = 0
            top_info_text_view.layoutParams = it
        }
        mainDataView = BalanceChangeMainDataView(appbar, amountFormatter)
    }

    private fun initDetailsList() {
        details_list.layoutManager = LinearLayoutManager(this)
        details_list.adapter = adapter
    }

    private fun initConfirmationButton() {
        confirm_button.onClick {
            acceptRedemption()
        }
    }

    private fun acceptRedemption() {
        val dialog = ProgressDialogFactory.getDialog(this)

        ConfirmRedemptionRequestUseCase(
                request,
                repositoryProvider,
                walletInfoProvider,
                apiProvider,
                TxManager(apiProvider)
        )
                .perform()
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .doOnSubscribe { dialog.show() }
                .doOnTerminate { dialog.dismiss() }
                .subscribeBy(
                        onComplete = this::onRedemptionConfirmed,
                        onError = redemptionConfirmationErrorHandler::handleIfPossible
                )
                .addTo(compositeDisposable)
    }

    private fun onRedemptionConfirmed() {
        toastManager.short(R.string.successfully_accepted_redemption)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private val redemptionConfirmationErrorHandler: ErrorHandler
        get() = CompositeErrorHandler(
                SimpleErrorHandler { error ->
                    when (error) {
                        is ConfirmRedemptionRequestUseCase.RedemptionAlreadyProcessedException -> {
                            toastManager.long(R.string.error_redemption_request_no_more_valid)
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                            true
                        }
                        else -> false
                    }
                },
                errorHandlerFactory.getDefault()
        )

    private fun displayDetails() {
        val asset = this.asset ?: return
        mainDataView.displayOperationName(getString(R.string.redemption_title))
        mainDataView.displayAmount(request.amount, asset, true)
        mainDataView.displayNonZeroFee(BigDecimal.ZERO, asset)

        initRequestorClick()
        displayRequestor()
        loadAndDisplayRequestorEmail()
    }

    private fun initRequestorClick() {
        adapter.onItemClick { _, item ->
            if (item.id == REQUESTOR_ITEM_ID) {

                val content =
                        if (emailLoadingFinished && senderEmail != null)
                            senderEmail + "\n\n" + request.sourceAccountId
                        else
                            request.sourceAccountId

                CopyDataDialogFactory.getDialog(
                        this,
                        content,
                        item.hint,
                        toastManager,
                        getString(R.string.data_has_been_copied)
                )
            }
        }
    }

    private fun displayRequestor() {
        adapter.addOrUpdateItem(
                DetailsItem(
                        id = REQUESTOR_ITEM_ID,
                        text = if (!emailLoadingFinished)
                            getString(R.string.loading_data)
                        else
                            senderEmail ?: formattedId,
                        hint = getString(R.string.tx_requestor),
                        icon = ContextCompat.getDrawable(this, R.drawable.ic_account),
                        singleLineText = true
                )
        )
    }

    private fun loadAndDisplayRequestorEmail() {
        repositoryProvider
                .accountDetails()
                .getEmailByAccountId(request.sourceAccountId)
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .doOnEvent { _, _ ->
                    emailLoadingFinished = true
                }
                .doOnSuccess { email ->
                    senderEmail = email
                }
                .subscribeBy(
                        onSuccess = {
                            displayRequestor()
                        },
                        onError = {
                            displayRequestor()
                        }
                )
                .addTo(compositeDisposable)
    }

    companion object {
        private const val EXTRA_BALANCE_ID = "balance_id"
        private const val EXTRA_REDEMPTION = "extra_redemption"

        private const val REQUESTOR_ITEM_ID = 1L

        fun getBundle(balanceId: String,
                      redemptionRequest: String) = Bundle().apply {
            putString(EXTRA_BALANCE_ID, balanceId)
            putString(EXTRA_REDEMPTION, redemptionRequest)
        }
    }
}