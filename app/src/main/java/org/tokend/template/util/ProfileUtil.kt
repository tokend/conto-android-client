package org.tokend.template.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.Dimension
import androidx.core.content.ContextCompat
import android.view.ViewGroup
import android.widget.ImageView
import org.tokend.template.R
import org.tokend.template.di.providers.UrlConfigProvider
import org.tokend.template.features.kyc.model.ActiveKyc
import org.tokend.template.features.kyc.model.KycForm
import org.tokend.template.view.util.ImageViewUtil
import java.security.MessageDigest
import org.tokend.template.view.util.LogoFactory

object ProfileUtil {

    fun getAvatarUrl(activeKyc: ActiveKyc?,
                     urlConfigProvider: UrlConfigProvider,
                     email: String?): String? {
        val form = (activeKyc as? ActiveKyc.Form)?.formData
        val avatar = (form as? KycForm.Corporate)?.avatar
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
                  activeKyc: ActiveKyc?,
                  sizePx: Int = (view.layoutParams as ViewGroup.LayoutParams).width) {
        val placeholderDrawable = getAvatarPlaceholder(email, view.context, sizePx)
        val avatarUrl = getAvatarUrl(activeKyc, urlConfigProvider, email)

        ImageViewUtil.loadImageCircle(view, avatarUrl, placeholderDrawable)
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
     * @return person full name or company from KYC if it's available
     */
    fun getDisplayedName(activeKyc: ActiveKyc?, email: String): String? {
        val form = (activeKyc as? ActiveKyc.Form)?.formData

        return when (form) {
            is KycForm.General -> form.fullName
            is KycForm.Corporate -> form.company
            else -> null
        }
    }
}