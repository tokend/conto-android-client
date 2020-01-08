package org.tokend.template.features.nfcpayment.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.Single
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.SingleSubject
import org.tokend.template.R
import org.tokend.template.extensions.withArguments
import org.tokend.template.features.nfcpayment.logic.PosPaymentRequestLoader
import org.tokend.template.features.nfcpayment.model.PosPaymentRequest
import org.tokend.template.features.nfcpayment.model.RawPosPaymentRequest
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.util.ObservableTransformers
import java.io.IOException
import java.util.concurrent.TimeUnit

class LoadPosPaymentRequestFragment : BaseFragment() {
    private val resultSubject = SingleSubject.create<PosPaymentRequest>()
    val resultSingle: Single<PosPaymentRequest> = resultSubject

    private lateinit var rawRequest: RawPosPaymentRequest

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_loading_data, container, false)
    }

    override fun onInitAllowed() {
        rawRequest = arguments?.getSerializable(RAW_REQUEST_EXTRA) as? RawPosPaymentRequest
                ?: throw IllegalArgumentException("No $RAW_REQUEST_EXTRA specified")

        loadRequest()
    }

    private fun loadRequest() {
        val loader = PosPaymentRequestLoader(repositoryProvider)

        loader
                .load(rawRequest)
                .retryWhen { errors ->
                    errors.filter { it is IOException }.delay(2, TimeUnit.SECONDS)
                }
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .subscribeBy(
                        onSuccess = resultSubject::onSuccess,
                        onError = resultSubject::onError
                )
                .addTo(compositeDisposable)
    }

    companion object {
        private const val RAW_REQUEST_EXTRA = "raw_request"

        fun getBundle(rawRequest: RawPosPaymentRequest) = Bundle().apply {
            putSerializable(RAW_REQUEST_EXTRA, rawRequest)
        }

        fun newInstance(bundle: Bundle): LoadPosPaymentRequestFragment =
                LoadPosPaymentRequestFragment().withArguments(bundle)
    }
}