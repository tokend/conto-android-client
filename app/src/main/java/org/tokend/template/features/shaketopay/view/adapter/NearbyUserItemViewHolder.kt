package org.tokend.template.features.shaketopay.view.adapter

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.util.CircleLogoUtil

class NearbyUserItemViewHolder(view: View) : BaseViewHolder<NearbyUserListItem>(view) {
    private val avatarImageView: ImageView = view.findViewById(R.id.avatar_image_view)
    private val nameTextView: TextView = view.findViewById(R.id.name_text_view)

    override fun bind(item: NearbyUserListItem) {
        CircleLogoUtil.setLogo(avatarImageView, item.name, item.avatarUrl)
        nameTextView.text = item.name
    }
}