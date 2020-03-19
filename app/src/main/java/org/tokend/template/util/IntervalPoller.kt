package org.tokend.template.util

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.subjects.PublishSubject
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
 * can be continued
 */
open class IntervalPoller<T>(
        val minInterval: Long,
        val timeUnit: TimeUnit,
        val deferredDataSource: Single<T>,
        val nonFatalErrorFilter: (Throwable) -> Boolean = { true }
) {
    private enum class ResultState { AVAILABLE, NOT_AVAILABLE; }

    /**
     * @return [Single] that awaits for the first result
     */
    fun asSingle(): Single<T> {
        return asObservable().firstOrError()
    }

    /**
     * @return [Completable] that awaits for the first result
     */
    fun asCompletable(): Completable {
        return asSingle().ignoreElement()
    }

    /**
     * @return [Observable] that emits all results
     */
    fun asObservable(): Observable<T> {
        val resultSubject = PublishSubject.create<T>()

        val pollingObservable = Observable.defer {
            deferredDataSource
                    .map {
                        resultSubject.onNext(it)
                        ResultState.AVAILABLE
                    }
                    .onErrorResumeNext { error ->
                        if (nonFatalErrorFilter(error))
                            Single.just(ResultState.NOT_AVAILABLE)
                        else
                            Single.error(error)
                    }
                    .toObservable()
        }

        var isDisposed = false

        val ticker =
                Observable.zip(
                        pollingObservable,
                        Observable.timer(minInterval, timeUnit),
                        BiFunction { hasResult: ResultState, _: Any -> hasResult }
                ).repeatUntil { isDisposed }

        return Observable.merge(
                ticker,
                resultSubject
        )
                .filter { it !is ResultState }
                .map { it as T }
                .doOnDispose { isDisposed = true }
    }
}