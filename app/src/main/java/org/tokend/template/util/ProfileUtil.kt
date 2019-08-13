package org.tokend.template.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.support.annotation.Dimension
import android.support.v4.content.ContextCompat
import android.widget.ImageView
import com.squareup.picasso.Picasso
import org.tokend.template.R
import org.tokend.template.di.providers.UrlConfigProvider
import org.tokend.template.features.assets.LogoFactory
import org.tokend.template.features.kyc.model.KycForm
import org.tokend.template.features.kyc.model.KycState
import org.tokend.template.util.imagetransform.CircleTransform
import java.security.MessageDigest

object ProfileUtil {

    fun getAvatarUrl(kycState: KycState?,
                     urlConfigProvider: UrlConfigProvider,
                     email: String?): String? {
        val submittedForm = (kycState as? KycState.Submitted<*>)?.formData
        val avatar = (submittedForm as? KycForm.Corporate)?.avatar
        return avatar?.getUrl(urlConfigProvider.getConfig().storage)
                ?: email?.let(this::getGravatarUrl)
    }

    private fun getGravatarUrl(email: String): String {
        val hash = email.toLowerCase().md5()
        return "https://www.gravatar.com/avatar/$hash?d=404"
    }

    fun getAvatarPlaceholder(email: String,
                             context: Context,
                             @Dimension
                             sizePx: Int): Drawable {
        val placeholderImage = LogoFactory(context)
                .getForValue(
                        email.toUpperCase(),
                        sizePx,
                        ContextCompat.getColor(context, R.color.avatar_placeholder_background),
                        Color.WHITE
                )

        return BitmapDrawable(context.resources, placeholderImage)
    }

    fun setAvatar(view: ImageView,
                  email: String,
                  urlConfigProvider: UrlConfigProvider,
                  savedKycState: KycState.Submitted<*>?) {
        val context = view.context

        val placeholderDrawable = getAvatarPlaceholder(
                email, context, context.resources.getDimensionPixelSize(R.dimen.hepta_margin)
        )
        view.setImageDrawable(placeholderDrawable)

        getAvatarUrl(savedKycState, urlConfigProvider, email)?.let {
            Picasso.with(context)
                    .load(it)
                    .placeholder(placeholderDrawable)
                    .transform(CircleTransform())
                    .fit()
                    .centerCrop()
                    .into(view)
        }
    }

    /***
     * @returns hash string kind used on Gravatar.
     */
    private fun String.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        md.update(this.toByteArray())

        val byteData = md.digest()
        val hexString = StringBuffer()
        for (i in byteData.indices) {
            val hex = Integer.toHexString(255 and byteData[i].toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }

        return hexString.toString()
    }

    /**
     * @return person full name or company from KYC if it's available or [email] otherwise
     */
    fun getDisplayedName(kycState: KycState?, email: String): String {
        val form = (kycState as? KycState.Submitted<*>)?.formData

        return when (form) {
            is KycForm.General -> form.firstName + " " + form.lastName
            is KycForm.Corporate -> form.company
            else -> email
        }
    }
}