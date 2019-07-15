package org.tokend.template.features.clients.view.adapter

import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.list_item_company_client.view.*
import org.apmem.tools.layouts.FlowLayout
import org.jetbrains.anko.sp
import org.tokend.template.R
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.util.LogoUtil
import org.tokend.template.view.util.formatter.AmountFormatter

class CompanyClientItemViewHolder(
        view: View,
        private val amountFormatter: AmountFormatter
) : BaseViewHolder<CompanyClientListItem>(view) {
    private val emailTextView: TextView = view.email_text_view
    private val logoImageView: ImageView = view.logo_image_view
    private val balancesLayout: ViewGroup = view.balances_layout
    private val noBalancesTextView: TextView = view.no_balances_text_view
    private val dividerView: View = view.divider_view

    var dividerIsVisible: Boolean
        get() = dividerView.visibility == View.VISIBLE
        set(value) {
            dividerView.visibility = if (value) View.VISIBLE else View.GONE
        }

    private val logoSize =
            view.context.resources.getDimensionPixelSize(R.dimen.asset_list_item_logo_size)

    private val balanceThemedContext = ContextThemeWrapper(view.context, R.style.StrokedBadgeText)
    private val balanceTextViewMargin =
            view.context.resources.getDimensionPixelSize(R.dimen.quarter_standard_margin)

    override fun bind(item: CompanyClientListItem) {
        emailTextView.text = item.email
        LogoUtil.setLogo(logoImageView, item.email.toUpperCase(), null, logoSize)

        balancesLayout.removeAllViews()

        val balancesToDisplay =
                if (item.balances.size <= BALANCES_TO_DISPLAY + 1)
                    item.balances
                else
                    item.balances.subList(0, BALANCES_TO_DISPLAY)

        if (balancesToDisplay.isNotEmpty()) {
            balancesLayout.visibility = View.VISIBLE
            noBalancesTextView.visibility = View.GONE

            balancesToDisplay.forEach { balance ->
                addBalanceBadge(
                        amountFormatter.formatAssetAmount(
                                balance.amount,
                                balance.asset
                        )
                )
            }

            val notDisplayed = item.balances.size - balancesToDisplay.size
            if (notDisplayed > 0) {
                addBalanceBadge(view.context.getString(
                        R.string.template_client_balances_and_some_more,
                        notDisplayed
                ))
            }
        } else {
            balancesLayout.visibility = View.GONE
            noBalancesTextView.visibility = View.VISIBLE
        }
    }

    private fun addBalanceBadge(text: String) {
        val textView = TextView(balanceThemedContext, null, R.style.StrokedBadgeText)
        textView.text = text
        balancesLayout.addView(textView)
        textView.layoutParams = (textView.layoutParams as FlowLayout.LayoutParams).apply {
            setMargins(balanceTextViewMargin, balanceTextViewMargin, balanceTextViewMargin, balanceTextViewMargin)
        }
    }

    companion object {
        private const val BALANCES_TO_DISPLAY = 3
    }
}