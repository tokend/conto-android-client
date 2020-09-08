package org.tokend.template.view.util

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.annotation.ColorInt
import android.widget.ImageView
import com.mikepenz.materialdrawer.util.DrawerImageLoader
import com.squareup.picasso.Picasso
import org.tokend.template.util.imagetransform.RemoveAlphaTransform

class PicassoDrawerImageLoader(private val context: Context,
                               private val mainPlaceholder: Drawable?,
                               @ColorInt
                               private val backgroundColor: Int,
                               private val profileListItemPlaceholder: Drawable? = mainPlaceholder
) : DrawerImageLoader.IDrawerImageLoader {
    override fun placeholder(ctx: Context?): Drawable? {
        return mainPlaceholder
    }

    override fun placeholder(ctx: Context?, tag: String?): Drawable? {
        return if (tag == DrawerImageLoader.Tags.PROFILE_DRAWER_ITEM.name)
            profileListItemPlaceholder
        else
            mainPlaceholder
    }

    override fun set(imageView: ImageView?, uri: Uri?, placeholder: Drawable?) {
        Picasso
                .with(context)
                .load(uri)
                .transform(RemoveAlphaTransform(backgroundColor))
                .placeholder(placeholder)
                .into(imageView)
    }

    override fun set(imageView: ImageView?, uri: Uri?, placeholder: Drawable?, tag: String?) {
        Picasso
                .with(context)
                .load(uri)
                .transform(RemoveAlphaTransform(backgroundColor))
                .placeholder(placeholder)
                .tag(tag)
                .into(imageView)
    }

    override fun cancel(imageView: ImageView?) {
        Picasso
                .with(context)
                .cancelRequest(imageView)
    }
}