package org.tokend.template.features.shaketopay.view

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.drawable.Animatable
import android.location.Location
import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
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
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.features.shaketopay.logic.LocationUpdatesProvider
import org.tokend.template.features.shaketopay.model.NearbyUserRecord
import org.tokend.template.features.shaketopay.view.adapter.NearbyUserListItem
import org.tokend.template.features.shaketopay.view.adapter.NearbyUsersAdapter
import org.tokend.template.util.*
import org.tokend.template.view.util.ColumnCalculator
import org.tokend.template.view.util.ScrollOnTopItemUpdateAdapterObserver
import java.util.*
import java.util.concurrent.TimeUnit

class ShakeToPayActivity : BaseActivity() {
    private val locationPermission =
            PermissionManager(Manifest.permission.ACCESS_FINE_LOCATION, 404)

    private lateinit var locationUpdatesProvider: LocationUpdatesProvider
    private var lastLocation: Location? = null

    private val adapter = NearbyUsersAdapter()
    private lateinit var layoutManager: GridLayoutManager
    private var isListExpanded = false

    private val nearbyUsers = LinkedHashSet<NearbyUserRecord>()

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_shake_to_pay)

        initToolbar()
        initNearbyUsersList()
        initLocationUpdatesProvider()
        initAnimations()

        tryToSubscribeToLocationUpdates()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        title = ""
        toolbar.background = null
        appbar.background = ContextCompat.getDrawable(this,
                R.drawable.gradient_background_to_transparent_from_top)
        toolbar.setNavigationIcon(R.drawable.ic_close)
    }

    private fun initLocationUpdatesProvider() {
        locationUpdatesProvider = LocationUpdatesProvider(this)
    }

    private fun initNearbyUsersList() {
        layoutManager = GridLayoutManager(this, 1)
        updateListColumnsCount()

        nearby_users_recycler_view.layoutManager = layoutManager

        nearby_users_recycler_view.adapter = adapter

        adapter.onItemClick { _, item ->
            item.source?.also(this::openPayment)
        }

        adapter.registerAdapterDataObserver(
                ScrollOnTopItemUpdateAdapterObserver(nearby_users_recycler_view)
        )
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
    }

    private fun onLocationUpdatesCompleted() {
        stopLocationBroadcast()
        stopAnimations()

        if (nearbyUsers.isEmpty()) {
            title_text_view.setText(R.string.no_nearby_users_found)
        } else {
            title_text_view.setText(R.string.people_nearby_enumeration)
        }
    }

    private var locationBroadcastDisposable: Disposable? = null
    private fun startLocationBroadcast() {
        stopLocationBroadcast()

        val email = walletInfoProvider.getWalletInfo()?.email
                ?: return
        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return
        val activeKyc = repositoryProvider.activeKyc().item
                ?: return
        val avatar = ProfileUtil.getAvatarUrl(activeKyc, urlConfigProvider, email)
        val name = ProfileUtil.getDisplayedName(activeKyc, email) ?: email

        val userData = MinimalUserData(avatar, name, email)

        locationBroadcastDisposable = IntervalPoller(
                minInterval = LOCATION_SEND_INTERVAL_MS,
                timeUnit = TimeUnit.MILLISECONDS,
                deferredDataSource = Single.defer<List<NearbyUserRecord>> {
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
                            .map { items ->
                                items.map(::NearbyUserRecord)
                            }
                }
        )
                .asObservable()
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribeBy(
                        onNext = this::onNewUsersNearby,
                        onError = { errorHandlerFactory.getDefault().handle(it) }
                )
                .addTo(compositeDisposable)
    }

    private fun stopLocationBroadcast() {
        locationBroadcastDisposable?.dispose()
    }

    private fun onNewUsersNearby(users: Collection<NearbyUserRecord>) {
        nearbyUsers.addAll(users)
        displayNearbyUsers()
    }

    private fun displayNearbyUsers() {
        val items = nearbyUsers.map(::NearbyUserListItem)

        if (items.isNotEmpty() && !isListExpanded) {
            expandList()
        }

        adapter.setData(items)
    }

    private fun expandList() {
        isListExpanded = true

        val targetLayoutParams = circles_image_view.layoutParams as ConstraintLayout.LayoutParams

        val animator = ValueAnimator.ofFloat(1.1f, 1.6f).apply {
            interpolator = AccelerateInterpolator()
            duration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
            addUpdateListener {
                val value = animatedValue as Float
                targetLayoutParams.dimensionRatio = "$value:1"
                circles_image_view.layoutParams = targetLayoutParams
            }
        }

        animator.start()
    }

    private fun openPayment(user: NearbyUserRecord) {
        Navigator.from(this).openSend(
                recipientAccount = user.accountId,
                recipientNickname = user.email
        )
    }

    private fun updateListColumnsCount() {
        val maxWidth = resources.getDimensionPixelSize(R.dimen.nearby_user_avatar_size) * 3
        layoutManager.spanCount = ColumnCalculator.getColumnCount(this, maxWidth)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateListColumnsCount()
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
