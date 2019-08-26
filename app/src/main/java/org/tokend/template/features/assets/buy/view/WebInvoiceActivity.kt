package org.tokend.template.features.assets.buy.view

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.android.synthetic.main.activity_web_invoice.*
import kotlinx.android.synthetic.main.toolbar.*
import okhttp3.HttpUrl
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity

class WebInvoiceActivity : BaseActivity() {
    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_web_invoice)

        val invoiceUrl = intent.getStringExtra(INVOICE_URL_EXTRA)
        if (invoiceUrl == null) {
            errorHandlerFactory.getDefault().handle(
                    IllegalArgumentException("No $INVOICE_URL_EXTRA specified")
            )
            finish()
            return
        }

        initToolbar()
        initWebView()

        navigate(invoiceUrl)
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        setTitle(R.string.web_invoice_screen_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        web_view.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                finishIfNeeded(url)
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progress.visibility = View.GONE
            }
        }

        web_view.settings.apply {
            loadWithOverviewMode = true
            useWideViewPort = true
            javaScriptEnabled = true
        }
    }

    private fun navigate(url: String) {
        web_view.loadUrl(url)
    }

    private fun finishIfNeeded(url: String) {
        val successRedirectUrl = HttpUrl.parse(urlConfigProvider.getConfig().client)
                ?: return
        val currentUrl = HttpUrl.parse(url)
                ?: return

        // Successful payment causes redirect to web client.
        if (currentUrl.host() == successRedirectUrl.host()) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    companion object {
        private const val INVOICE_URL_EXTRA = "invoice_url"

        fun getBundle(invoiceUrl: String) = Bundle().apply {
            putString(INVOICE_URL_EXTRA, invoiceUrl)
        }
    }
}
