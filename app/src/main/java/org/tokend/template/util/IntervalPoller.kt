package org.tokend.template.util

import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import java.util.concurrent.TimeUnit

/**
 * Performs poll of [deferredDataSource] with minimal interval defined by [minInterval]
 *
 * I.e. if [minInterval] is 5 second and data request completes in 1
 * then it will wait 4 seconds to perform the next request,
 * otherwise it will be performed immediately.
 *
 * @param deferredDataSource request for the data to poll which can be repeated
 * @param nonFatalErrorFilter must return true if data request error is not fatal and polling
 * can be continues
 */
class IntervalPoller<T>(
        val minInterval: Long,
        val timeUnit: TimeUnit,
        val deferredDataSource: Single<T>,
        val nonFatalErrorFilter: (Throwable) -> Boolean = { true }
) {
    /**
     * @return [Single] that awaits for the first result
     */
    fun asSingle(): Single<T> {
        return asObservable().firstOrError()
    }

    /**
     * @return [Observable] that emits all results
     */
    fun asObservable(): Observable<T> {
        var lastResult: T? = null
        val pollingObservable = Observable.defer {
            deferredDataSource
                    .map {
                        lastResult = it
                        true
                    }
                    .onErrorResumeNext { error ->
                        if (nonFatalErrorFilter(error))
                            Single.just(false)
                        else
                            Single.error(error)
                    }
                    .onErrorReturnItem(false)
                    .toObservable()
        }

        return Observable.zip(
                pollingObservable,
                Observable.timer(minInterval, timeUnit),
                BiFunction { hasResult: Boolean, _: Any -> hasResult }
        )
                .repeat()
                .filter { it }
                .map { lastResult!! }
    }
}