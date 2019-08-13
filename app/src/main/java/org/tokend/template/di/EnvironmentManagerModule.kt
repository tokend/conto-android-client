package org.tokend.template.di

import dagger.Module
import dagger.Provides
import org.tokend.template.util.environments.AppEnvironmentsManager
import javax.inject.Singleton

@Module
class EnvironmentManagerModule(
        private val environmentsManager: AppEnvironmentsManager
) {
    @Provides
    @Singleton
    fun envManager(): AppEnvironmentsManager {
        return environmentsManager
    }
}