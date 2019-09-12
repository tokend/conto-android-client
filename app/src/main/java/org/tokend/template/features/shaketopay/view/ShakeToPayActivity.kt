package org.tokend.template.features.shaketopay.view

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.android.gms.location.*
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_shake_to_pay.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.rx.extensions.toSingle
import org.tokend.sdk.api.integrations.locator.model.MinimalUserData
import org.tokend.sdk.api.integrations.locator.model.NearbyUser
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.util.IntervalPoller
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.PermissionManager
import org.tokend.template.util.ProfileUtil
import java.util.concurrent.TimeUnit

class ShakeToPayActivity : BaseActivity() {
    private val locationPermission =
            PermissionManager(Manifest.permission.ACCESS_FINE_LOCATION, 404)

    private lateinit var locationClient: FusedLocationProviderClient
    private var lastLocation: Location? = null

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_shake_to_pay)

        initToolbar()
        initLocationClient()
        tryToObserveLocation()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        title = ""
        toolbar.setNavigationIcon(R.drawable.ic_close)
    }

    private fun initLocationClient() {
        locationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun tryToObserveLocation() {
        locationPermission.check(this) {
            observeLocation()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            lastLocation = result.lastLocation
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            if (availability.isLocationAvailable) {
                onLocationAvailable()
            } else {
                onLocationNotAvailable()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun observeLocation() {
        locationClient
                .requestLocationUpdates(
                        LocationRequest.create().apply {
                            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                            interval = 5000
                            fastestInterval = 7000
                            numUpdates = 10
                        }
                                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY),
                        locationCallback,
                        Looper.getMainLooper()
                )
    }

    override fun onDestroy() {
        super.onDestroy()
        locationClient.removeLocationUpdates(locationCallback)
    }

    private fun onLocationAvailable() {
        location_av_text_view.text = "Location is available"

        startLocationBroadcast()
    }

    private fun onLocationNotAvailable() {
        location_av_text_view.text = "Location not available"

        stopLocationBroadcast()
    }

    private var locationBroadcastDisposable: Disposable? = null
    private fun startLocationBroadcast() {
        stopLocationBroadcast()

        val h = Handler(Looper.getMainLooper())

        locationBroadcastDisposable = IntervalPoller(
                minInterval = 3,
                timeUnit = TimeUnit.SECONDS,
                deferredDataSource = Single.defer<List<NearbyUser>> {
                    val lastLocation = this.lastLocation
                            ?: return@defer Single.error(IllegalStateException("No location yet"))
                    h.post {
                        location_text_view.text = "Broadcasted: ${lastLocation.latitude}, ${lastLocation.longitude}"
                    }

                    val email = walletInfoProvider.getWalletInfo()!!.email
                    val accountId = walletInfoProvider.getWalletInfo()!!.accountId
                    val avatar = ProfileUtil.getAvatarUrl(repositoryProvider.kycState().item,
                            urlConfigProvider, email)
                    val name = ProfileUtil.getDisplayedName(repositoryProvider.kycState().item, email)
                            ?: email

                    apiProvider
                            .getApi()
                            .integrations
                            .locator
                            .getUsersNearby(
                                    lastLocation.latitude,
                                    lastLocation.longitude,
                                    0.05,
                                    accountId,
                                    MinimalUserData(avatar, name, email)
                            )
                            .toSingle()
                }
        )
                .asObservable()
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribeBy(
                        onNext = {
                            if (it.isEmpty()) {
                                near_text_view.text = "No one's near :("
                            } else {
                                near_text_view.text = "Near members: " +
                                        it.map { n -> n.userData.name }.joinToString()
                            }
                        },
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
    }

    private fun stopLocationBroadcast() {
        locationBroadcastDisposable?.dispose()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationPermission.handlePermissionResult(requestCode, permissions, grantResults)
    }
}
