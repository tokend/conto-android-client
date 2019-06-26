package org.tokend.template.features.redeem.view

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import org.spongycastle.util.encoders.DecoderException
import org.tokend.sdk.utils.extentions.decodeBase64
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.AssetRecord
import org.tokend.template.data.repository.balances.BalancesRepository
import org.tokend.template.features.redeem.model.RedemptionRequest
import org.tokend.template.features.redeem.model.RedemptionRequestFormatException
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.PermissionManager
import org.tokend.template.util.QrScannerUtil

class ScanRedemptionActivity : BaseActivity() {

    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    private val cameraPermission = PermissionManager(Manifest.permission.CAMERA, 404)

    private var redemptionRequest: RedemptionRequest? = null

    private val asset: AssetRecord?
        get() = balancesRepository.itemsList
                .find { it.id == balanceId }
                ?.asset

    private lateinit var balanceId: String

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_scan_redemption)

        val balanceId = intent.getStringExtra(EXTRA_BALANCE_ID)
        if (balanceId == null) {
            errorHandlerFactory.getDefault().handle(
                    IllegalArgumentException("No $EXTRA_BALANCE_ID specified")
            )
            finish()
            return
        }
        this.balanceId = balanceId

        tryOpenQrScanner()
    }

    private fun tryOpenQrScanner() {
        cameraPermission.check(this) {
            QrScannerUtil.openScanner(this)
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
                .map {
                    if (asset?.code != it.assetCode) {
                        throw WrongAssetException()
                    }
                }
                .ignoreElement()
                .subscribeBy(
                        onComplete = {
                            Navigator.from(this).toAcceptRedemption(balanceId, result)
                        },
                        onError = this::onRequestParsingError
                )
                .addTo(compositeDisposable)
    }

    private fun onRequestParsingError(error: Throwable) {
        when (error) {
            is WrongAssetException ->
                toastManager.short(
                        getString(
                                R.string.template_error_redemption_request_asset_mismatch,
                                asset?.code
                        )
                )
            is RedemptionRequestFormatException,
            is DecoderException -> {
                toastManager.short(R.string.error_invalid_redemption_request)
                error.cause?.printStackTrace()
            }
            else ->
                errorHandlerFactory.getDefault().handle(error)
        }
        tryOpenQrScanner()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_CANCELED) {
            finish()
        }

        QrScannerUtil.getStringFromResult(requestCode, resultCode, data)
                ?.also(this::onScannerResult)
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        cameraPermission.handlePermissionResult(requestCode, permissions, grantResults)
    }

    class WrongAssetException : Exception()

    companion object {
        const val EXTRA_BALANCE_ID = "balance_id"
    }
}