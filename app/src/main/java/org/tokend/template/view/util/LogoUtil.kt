package org.tokend.template.view.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.support.annotation.Dimension
import android.widget.ImageView
import com.squareup.picasso.Picasso
import org.tokend.template.data.model.AssetRecord
import org.tokend.template.features.assets.LogoFactory
import org.tokend.template.util.imagetransform.CircleTransform

object LogoUtil {
    fun generateLogo(value: String,
                     context: Context,
                     @Dimension
                     logoSize: Int): Drawable {
        val bitmap = LogoFactory(context).getWithAutoBackground(
                value,
                logoSize
        )

        return BitmapDrawable(context.resources, bitmap)
    }

    fun setLogo(view: ImageView,
                value: String,
                logoUrl: String?,
                @Dimension
                logoSize: Int) {
        val context = view.context
        val picasso = Picasso.with(context)

        if (logoUrl != null) {
            picasso.load(logoUrl)
                    .resize(logoSize, logoSize)
                    .centerInside()
                    .transform(CircleTransform())
                    .into(view)
        } else {
            picasso.cancelRequest(view)
            view.setImageDrawable(generateLogo(value, context, logoSize))
        }
    }
}