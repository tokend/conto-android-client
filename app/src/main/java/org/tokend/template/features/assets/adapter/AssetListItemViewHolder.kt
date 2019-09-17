package org.tokend.template.features.assets.adapter

import android.support.v7.widget.AppCompatImageView
import android.support.v7.widget.TooltipCompat
import android.view.View
import android.widget.Button
import android.widget.TextView
import org.jetbrains.anko.find
import org.jetbrains.anko.onClick
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.adapter.base.SimpleItemClickListener
import org.tokend.template.view.util.CircleLogoUtil

class AssetListItemViewHolder(view: View,
                              private val displayDescription: Boolean
) : BaseViewHolder<AssetListItem>(view) {
    private val logoImageView: AppCompatImageView = view.find(R.id.asset_logo_image_view)
    private val codeTextView: TextView = view.find(R.id.asset_code_text_view)
    private val nameTextView: TextView = view.find(R.id.asset_name_text_view)
    private val descriptionTextView: TextView = view.find(R.id.asset_description_text_view)
    private val detailsButton: TextView = view.find(R.id.asset_details_button)
    private val primaryActionButton: Button = view.find(R.id.asset_primary_action_button)
    private val balanceExistsIndicator: View = view.find(R.id.asset_balance_exists_image_view)

    init {
        TooltipCompat.setTooltipText(balanceExistsIndicator,
                view.context.getText(R.string.asset_balance_exists))
    }

    override fun bind(item: AssetListItem, clickListener: SimpleItemClickListener<AssetListItem>?) {
        super.bind(item, clickListener)
        primaryActionButton.onClick {
            clickListener?.invoke(it, item)
        }
    }

    override fun bind(item: AssetListItem) {
        CircleLogoUtil.setLogo(logoImageView, item.code, item.logoUrl)

        nameTextView.text = item.name ?: item.code

        if (!item.name.isNullOrEmpty()) {
            codeTextView.visibility = View.VISIBLE
            codeTextView.text = item.code
        } else {
            codeTextView.visibility = View.GONE
        }

        detailsButton.onClick { view.callOnClick() }

        if (item.balanceExists) {
            balanceExistsIndicator.visibility = View.VISIBLE
            primaryActionButton.text = view.context.getString(R.string.view_asset_history_action)
        } else {
            balanceExistsIndicator.visibility = View.GONE
            primaryActionButton.text = view.context.getString(R.string.create_balance_action)
        }

        if (displayDescription && item.description != null) {
            descriptionTextView.visibility = View.VISIBLE
            descriptionTextView.text = item.description
        } else {
            descriptionTextView.visibility = View.GONE
        }
    }
}