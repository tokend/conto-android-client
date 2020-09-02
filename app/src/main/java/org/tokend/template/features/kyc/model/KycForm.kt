package org.tokend.template.features.kyc.model

import com.google.gson.annotations.SerializedName
import org.json.JSONObject
import org.tokend.sdk.api.base.model.RemoteFile
import org.tokend.sdk.api.blobs.model.Blob

/**
 * KYC form data with documents
 */
sealed class KycForm(
        @SerializedName("documents")
        var documents: MutableMap<String, RemoteFile>? = mutableMapOf()
) {
    abstract val roleKey: String

    class Corporate(documents: MutableMap<String, RemoteFile>,
                    @SerializedName(COMPANY_KEY)
                    val company: String
    ) : KycForm(documents) {
        override val roleKey: String
            get() = KEY_CORPORATE_ACCOUNT_ROLE

        val avatar: RemoteFile?
            get() = documents?.get("kyc_avatar")

        companion object {
            const val COMPANY_KEY = "company"
        }
    }

    class General(@SerializedName(FIRST_NAME_KEY)
                  val firstName: String,
                  @SerializedName("last_name")
                  val lastName: String
    ) : KycForm(null) {
        override val roleKey: String
            get() = KEY_GENERAL_ACCOUNT_ROLE

        val avatar: RemoteFile?
            get() = documents?.get(AVATAR_DOCUMENT_KEY)

        val fullName: String
            get() = "$firstName $lastName"

        companion object {
            const val FIRST_NAME_KEY = "first_name"
            const val AVATAR_DOCUMENT_KEY = "kyc_avatar"
        }
    }

    /**
     * Empty form to use in case when the original form
     * can't be processed
     */
    object Empty : KycForm() {
        override val roleKey: String
            get() =
                throw IllegalArgumentException("You can't use empty form to change role")
    }

    companion object {
        fun fromBlob(blob: Blob): KycForm {
            val valueJson = JSONObject(blob.valueString)

            val isGeneral = valueJson.has(General.FIRST_NAME_KEY)
            val isCorporate = valueJson.has(Corporate.COMPANY_KEY)

            return when {
                isCorporate ->
                    blob.getValue(Corporate::class.java)
                isGeneral ->
                    blob.getValue(General::class.java)
                else ->
                    throw IllegalArgumentException("Unknown KYC form type")
            }
        }
        private const val KEY_GENERAL_ACCOUNT_ROLE = "account_role:general"
        private const val KEY_CORPORATE_ACCOUNT_ROLE = "account_role:corporate"
    }

}