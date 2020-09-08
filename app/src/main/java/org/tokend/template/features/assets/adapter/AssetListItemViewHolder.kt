package org.tokend.template.features.assets.adapter

import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.TooltipCompat
import android.view.View
import android.widget.TextView
import org.jetbrains.anko.find
import org.jetbrains.anko.onClick
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.util.CircleLogoUtil

class AssetListItemViewHolder(view: View,
                              private val displayDescription: Boolean
) : BaseViewHolder<AssetListItem>(view) {
    private val logoImageView: AppCompatImageView = view.find(R.id.asset_logo_image_view)
    private val codeTextView: TextView = view.find(R.id.asset_code_text_view)
    private val nameTextView: TextView = view.find(R.id.asset_name_text_view)
    private val descriptionTextView: TextView = view.find(R.id.asset_description_text_view)
    private val detailsButton: TextView = view.find(R.id.asset_details_button)
    private val balanceExistsIndicator: View = view.find(R.id.asset_balance_exists_image_view)

    init {
        TooltipCompat.setTooltipText(balanceExistsIndicator,
                view.context.getText(R.string.asset_balance_exists))
    }

    override fun bind(item: AssetListItem) {
        CircleLogoUtil.setLogo(logoImageView, item.name ?: item.code,
                item.logoUrl, extras = arrayOf(item.code))

        nameTextView.text = item.name ?: item.code

        codeTextView.visibility = View.GONE

        detailsButton.onClick { view.callOnClick() }

        balanceExistsIndicator.visibility = View.GONE

        if (displayDescription && item.description != null) {
            descriptionTextView.visibility = View.VISIBLE
            descriptionTextView.text = item.description
        } else {
            descriptionTextView.visibility = View.GONE
        }
    }
}