package org.tokend.template.features.nfcpayment.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.reactivex.Completable
import io.reactivex.subjects.CompletableSubject
import kotlinx.android.synthetic.main.appbar_with_balance_change_main_data.*
import kotlinx.android.synthetic.main.fragment_pos_payment_request_confirmation.*
import kotlinx.android.synthetic.main.layout_balance_change_main_data.*
import org.tokend.template.R
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.nfcpayment.model.PosPaymentRequest
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.view.balancechange.BalanceChangeMainDataView
import java.math.BigDecimal
import java.util.concurrent.CancellationException

class PosPaymentRequestConfirmationFragment: BaseFragment() {
    private lateinit var request: PosPaymentRequest
    private lateinit var mainDataView: BalanceChangeMainDataView

    private val resultSubject = CompletableSubject.create()
    val resultCompletable: Completable = resultSubject

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pos_payment_request_confirmation, container, false)
    }

    override fun onInitAllowed() {
        request = arguments?.getSerializable(PAYMENT_REQUEST_EXTRA) as? PosPaymentRequest
                ?: throw IllegalArgumentException("No $PAYMENT_REQUEST_EXTRA specified")

        initMainDataView()
        initButtons()

        displayMainData()
    }

    private fun initMainDataView() {
        mainDataView = BalanceChangeMainDataView(appbar, amountFormatter)
        (top_info_text_view.layoutParams as? LinearLayout.LayoutParams)?.also {
            it.topMargin = 0
            top_info_text_view.layoutParams = it
        }
        toolbar_container.visibility = View.GONE
    }

    private fun initButtons() {
        confirm_button.setOnClickListener {
            resultSubject.onComplete()
        }

        cancel_button.setOnClickListener {
            resultSubject.onError(CancellationException())
        }
    }

    private fun displayMainData() {
        mainDataView.displayOperationName(getString(R.string.balance_change_cause_payment))
        mainDataView.displayAmount(request.amount, request.asset, isReceived = false)
        mainDataView.displayNonZeroFee(BigDecimal.ZERO, request.asset)
    }

    companion object {
        private const val PAYMENT_REQUEST_EXTRA = "payment_request"

        fun getBundle(request: PosPaymentRequest) = Bundle().apply {
            putSerializable(PAYMENT_REQUEST_EXTRA, request)
        }

        fun newInstance(bundle: Bundle): PosPaymentRequestConfirmationFragment =
                PosPaymentRequestConfirmationFragment().withArguments(bundle)
    }
}