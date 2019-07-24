package org.tokend.template.util

import android.app.Activity
import android.content.Intent
import android.support.v4.app.Fragment
import com.google.zxing.integration.android.IntentIntegrator
import org.tokend.template.features.qr.CaptureActivityPortrait

/**
 * Contains utilities to work with QR scanner
 */
object QrScannerUtil {
    /**
     * Opens QR scanner with the default setup:
     * no beep, no orientation lock, no labels
     */
    fun openScanner(activity: Activity,
                    prompt: String = "") {
        IntentIntegrator(activity)
                .defaultSetup(prompt)
                .initiateScan()
    }

    /**
     * Opens QR scanner with the default setup:
     * no beep, no orientation lock, no labels
     */
    fun openScanner(fragment: Fragment,
                    prompt: String = "") {
        IntentIntegrator.forSupportFragment(fragment)
                .defaultSetup(prompt)
                .initiateScan()
    }

    private fun IntentIntegrator.defaultSetup(prompt: String): IntentIntegrator {
        return this
                .setBeepEnabled(false)
                .setOrientationLocked(true)
                .setPrompt(prompt + "\n")
                .setCaptureActivity(CaptureActivityPortrait::class.java)
    }

    /**
     * @return QR code content if [result] is QR scanner result, null otherwise
     */
    fun getStringFromResult(requestCode: Int, resultCode: Int, result: Intent?): String? {
        return IntentIntegrator.parseActivityResult(requestCode, resultCode, result)?.contents
    }
}