package org.tokend.template.features.companies.add.view

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.data.model.CompanyRecord
import org.tokend.template.features.companies.add.logic.CompanyLoader
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.PermissionManager
import org.tokend.template.util.QrScannerUtil
import org.tokend.wallet.Base32Check

class AddCompanyActivity : BaseActivity() {
    private class CompanyAlreadyAddedException(val company: CompanyRecord) : Exception()

    private val cameraPermission = PermissionManager(Manifest.permission.CAMERA, 404)

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_loading_data)

        tryOpenQrScanner()
    }

    // region Qr
    private fun tryOpenQrScanner() {
        cameraPermission.check(
                this,
                action = {
                    QrScannerUtil.openScanner(
                            this,
                            getString(R.string.add_company_scanner_prompt)
                    )
                },
                deniedAction = this::onNoCameraPermission
        )
    }

    private fun onNoCameraPermission() {
        toastManager.long(R.string.error_camera_permission_is_required)
        finish()
    }

    private fun onQrScanResult(result: String) {
        try {
            val accountId = Base32Check.encodeAccountId(Base32Check.decodeAccountId(result))
            loadCompany(accountId)
        } catch (e: Exception) {
            toastManager.short(R.string.error_invalid_company_qr_code)
        }
    }
    // endregion

    private fun loadCompany(companyAccountId: String) {
        CompanyLoader(repositoryProvider.clientCompanies())
                .load(companyAccountId)
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .subscribeBy(
                        onSuccess = this::tryToAddCompany,
                        onError = this::onCompanyAddError
                )
                .addTo(compositeDisposable)
    }

    private fun tryToAddCompany(company: CompanyRecord) {
        if (!repositoryProvider.clientCompanies().itemsList.contains(company)) {
            openAddConfirmation(company)
        } else {
            onCompanyAddError(CompanyAlreadyAddedException(company))
        }
    }

    private fun onCompanyAddError(error: Throwable) {
        when (error) {
            is CompanyLoader.NoCompanyFoundException ->
                toastManager.short(R.string.error_company_not_found)
            is CompanyAlreadyAddedException ->
                toastManager.short(
                        getString(
                                R.string.template_error_company_name_already_added,
                                error.company.name
                        )
                )
            else ->
                errorHandlerFactory.getDefault().handle(error)
        }

        tryOpenQrScanner()
    }

    private fun openAddConfirmation(company: CompanyRecord) {
        Navigator.from(this)
                .openCompanyAddConfirmation(company, CONFIRM_ADD_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        cameraPermission.handlePermissionResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CONFIRM_ADD_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                finish()
            } else {
                tryOpenQrScanner()
            }
        } else {
            val qrResult = QrScannerUtil.getStringFromResult(requestCode, resultCode, data)

            if (qrResult != null) {
                onQrScanResult(qrResult)
            } else {
                finish()
            }
        }
    }

    companion object {
        private val CONFIRM_ADD_REQUEST = "confirm_add_company".hashCode() and 0xffff
    }
}
