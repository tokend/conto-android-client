package org.tokend.template.features.settings.view

import android.content.Context
import androidx.annotation.StyleRes
import org.tokend.template.R
import org.tokend.template.util.environments.AppEnvironmentsManager
import org.tokend.template.view.dialog.SingleCheckDialog

class EnvironmentSelectionDialog(
        private val context: Context,
        private val environmentManager: AppEnvironmentsManager,
        private val withSignOutWarning: Boolean,
        @StyleRes
        private val style: Int = R.style.AlertDialogStyle
) {
    fun show() {
        val currentEnvironment = environmentManager.getEnvironment()
        val availableEnvironments = environmentManager.availableEnvironments

        SingleCheckDialog(
                context,
                availableEnvironments.map { it.name },
                style
        ).apply {
            setTitle(R.string.select_environment)
            if (withSignOutWarning) {
                setMessage(R.string.environment_selection_sign_out_warning)
            }
            setDefaultCheckIndex(availableEnvironments.indexOf(currentEnvironment))
            setPositiveButtonListener { _, index ->
                availableEnvironments
                        .getOrNull(index)
                        ?.also(environmentManager::setEnvironment)
            }
        }.show()
    }
}