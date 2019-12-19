package org.tokend.template.features.kyc.storage

import android.content.SharedPreferences
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import org.tokend.sdk.factory.GsonFactory
import org.tokend.template.extensions.tryOrNull
import org.tokend.template.features.kyc.model.ActiveKyc
import org.tokend.template.features.kyc.model.KycForm

/**
 * Represents single submitted KYC state storage based on SharedPreferences.
 */
class ActiveKycPersistor(
        private val preferences: SharedPreferences
) {
    private class Container(
            @SerializedName("form")
            val form: JsonElement?,
            @SerializedName("form_class")
            val formClassName: String?,
            @SerializedName("root_class")
            val rootClassName: String
    )

    private val gson = GsonFactory().getBaseGson()

    /**
     * Saves given [kyc]
     */
    fun save(kyc: ActiveKyc) {
        val formKyc = (kyc as? ActiveKyc.Form)

        preferences
                .edit()
                .putString(
                        KEY,
                        gson.toJson(
                                Container(
                                        form = formKyc?.formData?.let(gson::toJsonTree),
                                        formClassName = formKyc?.formData?.javaClass?.name,
                                        rootClassName = kyc.javaClass.name
                                )
                        )
                )
                .apply()
    }

    /**
     * @return saved state
     */
    fun load(): ActiveKyc? {
        return preferences
                .getString(KEY, null)
                ?.let {
                    tryOrNull {
                        val container = gson.fromJson(it, Container::class.java)
                        deserializeKyc(container)
                    }
                }
    }

    private fun deserializeKyc(container: Container): ActiveKyc {
        return when (val stateClass = Class.forName(container.rootClassName)) {
            ActiveKyc.Missing::class.java -> {
                ActiveKyc.Missing
            }
            ActiveKyc.Form::class.java -> {
                val formClassName = container.formClassName!!
                val formContent = container.form!!

                val formClass = Class.forName(formClassName)
                val form = gson.fromJson(formContent, formClass) as KycForm

                ActiveKyc.Form(form)
            }
            else -> throw IllegalStateException("Unknown state class $stateClass")
        }
    }

    companion object {
        private const val KEY = "active_kyc"
    }
}