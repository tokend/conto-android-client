package org.tokend.template.view.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.ImageView
import org.tokend.template.data.model.Asset
import org.tokend.template.features.assets.LogoFactory
import org.tokend.template.util.imagetransform.CircleTransform

object CircleLogoUtil {
    private fun generateLogo(content: String,
                             context: Context,
                             sizePx: Int,
                             extras: Array<out Any>): Drawable {
        return BitmapDrawable(
                context.resources,
                LogoFactory(context).getWithAutoBackground(content, sizePx, *extras)
        )
    }

    fun setLogo(view: ImageView,
                content: String,
                logoUrl: String?,
                sizePx: Int = (view.layoutParams as ViewGroup.LayoutParams).width,
                extras: Array<Any> = emptyArray()) {
        val placeholder = generateLogo(content, view.context, sizePx, extras)

        ImageViewUtil.loadImage(view, logoUrl, placeholder) {
            resize(sizePx, sizePx)
            centerInside()
            transform(CircleTransform())
        }
    }

    fun setAssetLogo(view: ImageView,
                     asset: Asset,
                     sizePx: Int = (view.layoutParams as ViewGroup.LayoutParams).width) {
        setLogo(view, asset.name ?: asset.code, asset.logoUrl,
                sizePx, arrayOf(asset.code))
    }

    fun setPersonLogo(view: ImageView,
                      logoUrl: String?,
                      email: String,
                      name: String?,
                      sizePx: Int = (view.layoutParams as ViewGroup.LayoutParams).width) {
        setLogo(view, name ?: email, logoUrl,
                sizePx, arrayOf(email))
    }
}