package org.tokend.template.features.shaketopay.view

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.drawable.Animatable
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.support.v4.content.ContextCompat
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
import android.view.animation.OvershootInterpolator
import android.view.animation.RotateAnimation
import com.google.android.gms.location.*
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_shake_to_pay.*
import kotlinx.android.synthetic.main.appbar.*
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
        initAnimations()

        tryToObserveLocation()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        title = ""
        toolbar.background = null
        appbar.background = ContextCompat.getDrawable(this, R.drawable.gradient_background_to_transparent_from_top)
        toolbar.setNavigationIcon(R.drawable.ic_close)
    }

    private fun initLocationClient() {
        locationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun initAnimations() {
        val phonePivotX = 1.15f
        val phonePivotY = 1.3f
        val phoneRotationDegree = -6f

        val shakeLastBounceAnimation = RotateAnimation(phoneRotationDegree, 0f,
                Animation.RELATIVE_TO_SELF, phonePivotX,
                Animation.RELATIVE_TO_SELF, phonePivotY
        ).apply {
            interpolator = OvershootInterpolator(2.5f)
            duration = 240
        }

        val shakeWithLastBounceAnimation = RotateAnimation(0f, phoneRotationDegree,
                Animation.RELATIVE_TO_SELF, phonePivotX,
                Animation.RELATIVE_TO_SELF, phonePivotY
        ).apply {
            interpolator = AccelerateInterpolator()
            repeatMode = Animation.REVERSE
            repeatCount = 4
            duration = 120
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    phone_image_view.startAnimation(shakeLastBounceAnimation)
                }
            })
        }

        Observable.interval(1500, 5000, TimeUnit.MILLISECONDS)
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    phone_image_view.startAnimation(shakeWithLastBounceAnimation)
                }
                .addTo(compositeDisposable)

        Observable.interval(2200, 2500, TimeUnit.MILLISECONDS)
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    (circles_image_view.drawable as? Animatable)?.start()
                }
                .addTo(compositeDisposable)
    }

    private fun tryToObserveLocation() {
        locationPermission.check(this,
                action = { observeLocation() },
                deniedAction = { finish() }
        )
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
        title_text_view.setText(R.string.looking_for_people_nearby)
        startLocationBroadcast()
    }

    private fun onLocationNotAvailable() {
        title_text_view.setText(R.string.obtaining_location_progress)
        stopLocationBroadcast()
    }

    private var locationBroadcastDisposable: Disposable? = null
    private fun startLocationBroadcast() {
        stopLocationBroadcast()

        locationBroadcastDisposable = IntervalPoller(
                minInterval = 3,
                timeUnit = TimeUnit.SECONDS,
                deferredDataSource = Single.defer<List<NearbyUser>> {
                    val lastLocation = this.lastLocation
                            ?: return@defer Single.error(IllegalStateException("No location yet"))

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
