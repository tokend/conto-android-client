package org.tokend.template.features.shaketopay.view.adapter

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.util.LogoUtil

class NearbyUserItemViewHolder(view: View) : BaseViewHolder<NearbyUserListItem>(view) {
    private val avatarImageView: ImageView = view.findViewById(R.id.avatar_image_view)
    private val nameTextView: TextView = view.findViewById(R.id.name_text_view)

    private val avatarSize =
            view.context.resources.getDimensionPixelSize(R.dimen.nearby_user_avatar_size)

    override fun bind(item: NearbyUserListItem) {
        LogoUtil.setLogo(avatarImageView, item.name, item.avatarUrl, avatarSize)
        nameTextView.text = item.name
    }
}