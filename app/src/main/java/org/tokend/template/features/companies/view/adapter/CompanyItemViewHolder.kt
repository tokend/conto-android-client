package org.tokend.template.features.companies.view.adapter

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.util.CircleLogoUtil

class CompanyItemViewHolder(view: View) : BaseViewHolder<CompanyListItem>(view) {

    private val logoImageView = view.findViewById<ImageView>(R.id.company_logo_image_view)
    private val nameTextView = view.findViewById<TextView>(R.id.company_name_text_view)
    private val dividerView = view.findViewById<View>(R.id.divider_view)
    private val existIndicator = view.findViewById<ImageView>(R.id.company_exists_image_view)
    private val industryTextView = view.findViewById<TextView>(R.id.company_industry_text_view)

    var dividerIsVisible: Boolean
        get() = dividerView.visibility == View.VISIBLE
        set(value) {
            dividerView.visibility = if (value) View.VISIBLE else View.GONE
        }

    override fun bind(item: CompanyListItem) {
        nameTextView.text = item.name

        industryTextView.apply {
            visibility = item.industry?.let {
                text = it
                View.VISIBLE
            } ?: View.GONE
        }

        CircleLogoUtil.setLogo(logoImageView, item.name, item.logoUrl)

        existIndicator.visibility = if (item.exist) {
            View.VISIBLE
        } else View.GONE
    }
}