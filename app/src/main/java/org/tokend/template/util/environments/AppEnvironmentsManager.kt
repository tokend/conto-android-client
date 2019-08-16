package org.tokend.template.util.environments

import android.content.SharedPreferences
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.tokend.template.data.model.UrlConfig
import org.tokend.template.di.providers.UrlConfigProvider

class AppEnvironmentsManager(
        availableEnvIds: Array<String>,
        private val defaultEnvId: String,
        private val urlConfigProvider: UrlConfigProvider,
        private val preferences: SharedPreferences,
        private val extraEnv: AppEnvironment? = null
) {
    private val environmentChangesSubject = PublishSubject.create<AppEnvironment>()

    private val environments = mutableListOf(
            AppEnvironment(
                    name = "Staging",
                    id = "staging",
                    config = UrlConfig(
                            api = "https://api.staging.conto.me",
                            storage = "https://s3.eu-north-1.amazonaws.com/contostaging-identity-storage-festive-cannon-2",
                            client = "https://staging.conto.me"
                    )
            ),
            AppEnvironment(
                    name = "Production",
                    id = "production",
                    config = UrlConfig(
                            api = "https://api.conto.me",
                            storage = "https://s3.eu-north-1.amazonaws.com/conto-identity-storage-ecstatic-beaver",
                            client = "https://conto.me"
                    )
            ),
            AppEnvironment(
                    name = "Demo",
                    id = "demo",
                    config = UrlConfig(
                            api = "https://api.demo.conto.me",
                            storage = "https://s3.eu-north-1.amazonaws.com/contodemo-identity-storage-stoic-haslett",
                            client = "https://demo.conto.me"
                    )
            )
    ).apply { extraEnv?.also { add(it) } }.associateBy(AppEnvironment::id)

    val availableEnvironments = availableEnvIds.mapNotNull(environments::get)

    val environmentChanges: Observable<AppEnvironment> = environmentChangesSubject

    val defaultEnvironment = availableEnvironments
            .find { it.id == defaultEnvId }
            ?: throw IllegalArgumentException("No environment available with ID $defaultEnvId")

    fun getEnvironment(): AppEnvironment = loadEnvironment() ?: defaultEnvironment

    fun initEnvironment() {
        applyEnvironment(getEnvironment())
    }

    fun setEnvironment(environment: AppEnvironment) {
        if (environment == getEnvironment()) {
            return
        }

        saveEnvironment(environment)
        applyEnvironment(environment)
        environmentChangesSubject.onNext(environment)
    }

    private fun applyEnvironment(environment: AppEnvironment) {
        urlConfigProvider.setConfig(environment.config)
    }

    // region Persistence
    private fun loadEnvironment(): AppEnvironment? {
        return preferences
                .getString(CURRENT_ENV_KEY, "")
                .let { id ->
                    availableEnvironments.find { it.id == id }
                }
    }

    private fun saveEnvironment(environment: AppEnvironment) {
        preferences
                .edit()
                .putString(CURRENT_ENV_KEY, environment.id)
                .apply()
    }
    // endregion

    private companion object {
        private const val CURRENT_ENV_KEY = "current_env"
    }
}