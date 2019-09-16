package org.tokend.template.features.shaketopay.logic

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.BehaviorSubject

/**
 * Provides highly accurate location updates from [FusedLocationProviderClient]
 */
class LocationUpdatesProvider(context: Context) {
    private val locationClient = LocationServices.getFusedLocationProviderClient(context)

    private val locationAvailabilitySubject = BehaviorSubject.create<Boolean>()

    /**
     * Emits location availability changes
     */
    val locationAvailability: Observable<Boolean>
        get() = locationAvailabilitySubject

    private fun onNewLocationAvailability(isAvailable: Boolean) {
        if (locationAvailabilitySubject.value != isAvailable) {
            locationAvailabilitySubject.onNext(isAvailable)
        }
    }

    /**
     * @return lazy observable that will emit [updatesCount] location updates
     * with interval not lower than [updateIntervalMs]
     *
     * @param includeLastKnownLocation if set to true the observable will
     * emit last known location first if it is available
     */
    @SuppressLint("MissingPermission")
    fun getLocationUpdates(updateIntervalMs: Long,
                           updatesCount: Int,
                           includeLastKnownLocation: Boolean): Observable<Location> {
        val request = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = updateIntervalMs
            fastestInterval = updateIntervalMs + 2000
            numUpdates = updatesCount
        }

        return Observable.create { emitter ->
            val emitNewLocation = { location: Location ->
                if (!emitter.isDisposed) {
                    emitter.onNext(location)
                }
            }

            val emitError = { e: Throwable ->
                if (!emitter.isDisposed) {
                    emitter.tryOnError(e)
                }
            }

            val updatesCallback = object : LocationCallback() {
                var emittedCount = 0
                override fun onLocationAvailability(availability: LocationAvailability) {
                    onNewLocationAvailability(availability.isLocationAvailable)
                }

                override fun onLocationResult(result: LocationResult) {
                    emitNewLocation(result.lastLocation)
                    emittedCount++
                    if (emittedCount == updatesCount) {
                        emitter.onComplete()
                        locationClient.removeLocationUpdates(this)
                    }
                }
            }

            if (includeLastKnownLocation) {
                locationClient
                        .lastLocation
                        .addOnCompleteListener { lastLocationTask ->
                            val error = lastLocationTask.exception
                            if (error != null) {
                                emitError(error)
                            } else {
                                val lastLocation = lastLocationTask.result
                                if (lastLocation != null) {
                                    emitNewLocation(lastLocation)
                                }
                            }
                        }
            }

            locationClient
                    .requestLocationUpdates(request, updatesCallback, Looper.getMainLooper())

            emitter.setDisposable(object : Disposable {
                private var disposed = false
                override fun isDisposed(): Boolean = disposed
                override fun dispose() {
                    disposed = true
                    locationClient.removeLocationUpdates(updatesCallback)
                }
            })
        }
    }
}