package org.tokend.template.features.shaketopay.view

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.drawable.Animatable
import android.location.Location
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
import android.view.animation.OvershootInterpolator
import android.view.animation.RotateAnimation
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
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
import org.tokend.template.features.shaketopay.logic.LocationUpdatesProvider
import org.tokend.template.util.IntervalPoller
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.PermissionManager
import org.tokend.template.util.ProfileUtil
import java.util.concurrent.TimeUnit

class ShakeToPayActivity : BaseActivity() {
    private val locationPermission =
            PermissionManager(Manifest.permission.ACCESS_FINE_LOCATION, 404)

    private lateinit var locationUpdatesProvider: LocationUpdatesProvider
    private var lastLocation: Location? = null
    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_shake_to_pay)

        initToolbar()
        initLocationUpdatesProvider()
        initAnimations()

        tryToSubscribeToLocationUpdates()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        title = ""
        toolbar.background = null
        appbar.background = ContextCompat.getDrawable(this, R.drawable.gradient_background_to_transparent_from_top)
        toolbar.setNavigationIcon(R.drawable.ic_close)
    }

    private fun initLocationUpdatesProvider() {
        locationUpdatesProvider = LocationUpdatesProvider(this)
    }

    private var animationsDisposable = CompositeDisposable()
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
                .addTo(animationsDisposable)

        Observable.interval(2200, 2500, TimeUnit.MILLISECONDS)
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    (circles_image_view.drawable as? Animatable)?.start()
                }
                .addTo(animationsDisposable)

        animationsDisposable.addTo(compositeDisposable)
    }

    private fun stopAnimations() {
        animationsDisposable.dispose()
    }

    private fun tryToSubscribeToLocationUpdates() {
        locationPermission.check(this,
                action = { subscribeToLocationUpdates() },
                deniedAction = { finish() }
        )
    }

    @SuppressLint("MissingPermission")
    private fun subscribeToLocationUpdates() {
        locationUpdatesProvider
                .locationAvailability
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe { isAvailable ->
                    if (isAvailable) {
                        onLocationAvailable()
                    } else {
                        onLocationNotAvailable()
                    }
                }
                .addTo(compositeDisposable)

        locationUpdatesProvider
                .getLocationUpdates(
                        updateIntervalMs = LOCATION_UPDATE_INTERVAL_MS,
                        updatesCount = LOCATION_UPDATES_COUNT,
                        includeLastKnownLocation = true
                )
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribeBy(
                        onNext = this::onNewLocation,
                        onError = { errorHandlerFactory.getDefault().handle(it) },
                        onComplete = this::onLocationUpdatesCompleted
                )
                .addTo(compositeDisposable)
    }

    private fun onLocationAvailable() {
        title_text_view.setText(R.string.looking_for_people_nearby)
        startLocationBroadcast()
    }

    private fun onLocationNotAvailable() {
        title_text_view.setText(R.string.obtaining_location_progress)
        stopLocationBroadcast()
    }

    private fun onNewLocation(location: Location) {
        lastLocation = location
        Log.i("Oleg", "New location $location")
    }

    private fun onLocationUpdatesCompleted() {
        stopLocationBroadcast()
        stopAnimations()
        Log.i("Oleg", "Updates completed")
    }

    private var locationBroadcastDisposable: Disposable? = null
    private fun startLocationBroadcast() {
        stopLocationBroadcast()

        val email = walletInfoProvider.getWalletInfo()?.email
                ?: return
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return
        val kycState = repositoryProvider.kycState().item
                ?: return
        val avatar = ProfileUtil.getAvatarUrl(kycState, urlConfigProvider, email)
        val name = ProfileUtil.getDisplayedName(kycState, email) ?: email

        val userData = MinimalUserData(avatar, name, email)

        locationBroadcastDisposable = IntervalPoller(
                minInterval = LOCATION_SEND_INTERVAL_MS,
                timeUnit = TimeUnit.MILLISECONDS,
                deferredDataSource = Single.defer<List<NearbyUser>> {
                    val lastLocation = this.lastLocation
                            ?: return@defer Single.error(IllegalStateException("No location yet"))

                    apiProvider
                            .getApi()
                            .integrations
                            .locator
                            .getUsersNearby(
                                    lastLocation.latitude,
                                    lastLocation.longitude,
                                    SEARCH_RADIUS_KM,
                                    accountId,
                                    userData
                            )
                            .toSingle()
                }
        )
                .asObservable()
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribeBy(
                        onNext = {
                            // TODO
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

    private companion object {
        private const val LOCATION_UPDATE_INTERVAL_MS = 5000L
        private const val LOCATION_UPDATES_COUNT = 10
        private const val LOCATION_SEND_INTERVAL_MS = 3000L
        private const val SEARCH_RADIUS_KM = 0.05
    }
}
