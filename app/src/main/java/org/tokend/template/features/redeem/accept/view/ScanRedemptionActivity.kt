package org.tokend.template.features.redeem.accept.view

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.google.zxing.integration.android.IntentIntegrator
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import org.spongycastle.util.encoders.DecoderException
import org.tokend.sdk.utils.extentions.decodeBase64
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.balances.storage.BalancesRepository
import org.tokend.template.features.redeem.model.RedemptionRequest
import org.tokend.template.features.redeem.model.RedemptionRequestFormatException
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.PermissionManager
import org.tokend.template.util.QrScannerUtil
import org.tokend.template.util.errorhandler.CompositeErrorHandler
import org.tokend.template.util.errorhandler.ErrorHandler
import org.tokend.template.util.errorhandler.SimpleErrorHandler
import org.tokend.template.util.navigation.Navigator

class ScanRedemptionActivity : BaseActivity() {

    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    private val cameraPermission = PermissionManager(Manifest.permission.CAMERA, 404)

    private var redemptionRequest: RedemptionRequest? = null

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_loading_data)

        tryOpenQrScanner()
    }

    private fun tryOpenQrScanner() {
        cameraPermission.check(this) {
            QrScannerUtil.openScanner(this)
                    .addTo(activityRequestsBag)
                    .doOnSuccess(this::onScannerResult)
        }
    }

    private fun onScannerResult(result: String) {
        repositoryProvider
                .systemInfo()
                .getNetworkParams()
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .map {
                    RedemptionRequest.fromSerialized(it, result.decodeBase64())
                }
                .doOnSuccess { redemptionRequest = it }
                .map { request ->
                    val accountId = walletInfoProvider.getWalletInfo()?.accountId
                    val balance = balancesRepository
                            .itemsList
                            .find { it.assetCode == request.assetCode }

                    if (balance == null
                            || balance.asset.ownerAccountId != accountId) {
                        throw WrongAssetException()
                    } else {
                        balance
                    }
                }
                .subscribeBy(
                        onSuccess = { balance ->
                            Navigator.from(this)
                                    .openRedemptionRequestConfirmation(
                                            balance.id,
                                            result
                                    )
                                    .addTo(activityRequestsBag)
                                    .doOnSuccess { finish() }
                                    .doOnCancel { tryOpenQrScanner() }
                        },
                        onError = requestParsingErrorHandler::handleIfPossible
                )
                .addTo(compositeDisposable)
    }

    private val requestParsingErrorHandler: ErrorHandler
        get() = CompositeErrorHandler(
                SimpleErrorHandler { error ->
                    when (error) {
                        is WrongAssetException -> {
                            toastManager.long(R.string.error_redemption_not_owned_asset)
                            true
                        }
                        is RedemptionRequestFormatException,
                        is DecoderException -> {
                            toastManager.short(R.string.error_invalid_redemption_request)
                            error.cause?.printStackTrace()
                            true
                        }
                        else -> false
                    }
                },
                errorHandlerFactory.getDefault()
        )
                .doOnSuccessfulHandle { tryOpenQrScanner() }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK) {
            when (requestCode) {
                IntentIntegrator.REQUEST_CODE -> finish()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        cameraPermission.handlePermissionResult(requestCode, permissions, grantResults)
    }

    class WrongAssetException : Exception()
}