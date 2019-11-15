package org.tokend.template.features.booking.add.view

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.fragment_booking_seats_selection.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.tokend.template.R
import org.tokend.template.features.booking.add.model.BookingInfoHolder
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.view.util.formatter.DateFormatter

class BookingSeatsSelectionFragment : BaseFragment() {
    private lateinit var bookingInfoHolder: BookingInfoHolder

    private val resultSubject = PublishSubject.create<Any>()
    val resultObservable: Observable<Any> = resultSubject

    private var webView: WebView? = null

    private val mapName = "rooms/example.svg"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_booking_seats_selection, container, false)
    }

    override fun onInitAllowed() {
        bookingInfoHolder = requireActivity() as? BookingInfoHolder
                ?: throw IllegalStateException("Parent activity must hold booking info")

        initTopInfo()
        initButtons()

        Handler().postDelayed({ loadAndDisplayMap() }, 200)
    }

    private fun initTopInfo() {
        val dateFormatter = DateFormatter(requireContext())
        val time = bookingInfoHolder.bookingTime
        val timeRange = "${dateFormatter.formatCompact(time.from, false)} – " +
                dateFormatter.formatCompact(time.to, false)

        booking_time_range_text_view.text = timeRange

        room_name_text_view.text = "Big mocked room #1"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
//        web_view.addJavascriptInterface(this, "Android")
//        web_view.settings.apply {
//            javaScriptEnabled = true
//            setSupportZoom(true)
//            builtInZoomControls = true
//            displayZoomControls = false
//        }
    }

    private fun initButtons() {
        zoom_in_button.setOnClickListener {
            webView?.zoomIn()
        }
        zoom_out_button.setOnClickListener {
            webView?.zoomOut()
        }
    }

    @JavascriptInterface
    fun onSeatPicked(seatId: String) {

    }

    @JavascriptInterface
    fun onSeatUnpicked(seatId: String) {

    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadAndDisplayMap() {
        if (isDetached) {
            return
        }

        webView = WebView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )

            addJavascriptInterface(this@BookingSeatsSelectionFragment, "Android")

            settings.apply {
                javaScriptEnabled = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
        }

        doAsync {
            val map = resources.assets
                    .open(mapName)
                    .use { it.reader(Charsets.UTF_8).readText() }

            val viewer = resources.assets
                    .open(MAP_VIEWER_HTML)
                    .use { it.reader(Charsets.UTF_8).readText() }
                    .replace(VIEWER_MAP_PLACEHOLDER, map)

            uiThread {
                if (isDetached) {
                    return@uiThread
                }

                web_view_container.addView(webView)
                webView?.loadData(viewer, "text/html", Charsets.UTF_8.name())
            }
        }
    }

    private fun tryToReadAssets() {
        var html = resources.assets
                .open(MAP_VIEWER_HTML)
                .use { it.reader(Charsets.UTF_8).readText() }

        val map = resources.assets
                .open("rooms/example.svg")
                .use { it.reader(Charsets.UTF_8).readText() }

        html = html.replace("<% svg %>", map)

        val seats = "[\n" +
                "                { id: 'e11', booked: true } ,\n" +
                "                { id: 'e12', booked: false },\n" +
                "                { id: 'e13', booked: true },\n" +
                "                { id: 'c21', booked: false },\n" +
                "                { id: 'c22', booked: false },\n" +
                "                { id: 'c23', booked: false },\n" +
                "                { id: 'c31', booked: true },\n" +
                "                { id: 'c32', booked: false },\n" +
                "                { id: 'c33', booked: true }\n" +
                "            ]"

        html = html.replace("<% seats_array %>", seats)
    }

    companion object {
        private const val MAP_VIEWER_HTML = "booking.html"
        private const val VIEWER_MAP_PLACEHOLDER = "<% svg %>"
        private const val VIEWER_SEATS_PLACEHOLDER = "<% seats_array %>"
    }
}