package org.tokend.template.features.redeem.accept.view

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

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_scan_redemption)

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
                            Navigator.from(this).toAcceptRedemption(balance.id, result)
                        },
                        onError = this::onRequestParsingError
                )
                .addTo(compositeDisposable)
    }

    private fun onRequestParsingError(error: Throwable) {
        when (error) {
            is WrongAssetException ->
                toastManager.long(R.string.error_redemption_not_owned_asset)
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
}