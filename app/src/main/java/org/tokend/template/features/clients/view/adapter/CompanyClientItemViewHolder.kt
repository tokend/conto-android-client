package org.tokend.template.features.clients.view.adapter

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.support.v4.content.ContextCompat
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.list_item_company_client.view.*
import org.apmem.tools.layouts.FlowLayout
import org.jetbrains.anko.onClick
import org.jetbrains.anko.onLongClick
import org.tokend.template.R
import org.tokend.template.features.assets.LogoFactory
import org.tokend.template.features.clients.model.CompanyClientRecord
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.adapter.base.SimpleItemClickListener
import org.tokend.template.view.util.formatter.AmountFormatter

class CompanyClientItemViewHolder(
        view: View,
        private val amountFormatter: AmountFormatter
) : BaseViewHolder<CompanyClientListItem>(view) {
    private val nameTextView: TextView = view.name_text_view
    private val logoImageView: ImageView = view.logo_image_view
    private val statusImageView: ImageView = view.status_image_view
    private val balancesLayout: ViewGroup = view.balances_layout
    private val noBalancesTextView: TextView = view.no_balances_text_view
    private val dividerView: View = view.divider_view
    private val bgSelected = ContextCompat.getDrawable(view.context, R.drawable.bg_selection)
    private val checkIcon = ContextCompat.getDrawable(view.context, R.drawable.ic_check_circle_accent)
    private lateinit var clientIcon: Drawable
    private val logoFactory = LogoFactory(view.context)

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

    private val statusActiveDrawable =
            ContextCompat.getDrawable(view.context, R.drawable.company_client_active)
    private val statusNotRegisteredDrawable =
            ContextCompat.getDrawable(view.context, R.drawable.company_client_not_registered)
    private val statusBlockedDrawable =
            ContextCompat.getDrawable(view.context, R.drawable.company_client_blocked)

    override fun bind(item: CompanyClientListItem) {
        nameTextView.text = item.name ?: item.email

        clientIcon = BitmapDrawable(
                itemView.context.resources,
                logoFactory.getWithAutoBackground(item.name
                        ?: item.email.toUpperCase(), logoSize, item.email)
        )

        initState(item.isChecked)
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
                                balance.asset,
                                withAssetName = true
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

        statusImageView.setImageDrawable(
                when (item.status) {
                    CompanyClientRecord.Status.NOT_REGISTERED -> statusNotRegisteredDrawable
                    CompanyClientRecord.Status.ACTIVE -> statusActiveDrawable
                    CompanyClientRecord.Status.BLOCKED -> statusBlockedDrawable
                }
        )
    }

    fun bind(item: CompanyClientListItem,
             clickListener: SimpleItemClickListener<CompanyClientListItem>?,
             selectionListener: (CompanyClientListItem, Int) -> Unit) {
        super.bind(item, clickListener)
        initState(item.isChecked)
        view.onLongClick {
            selectionListener.invoke(item, adapterPosition)
            true
        }
        logoImageView.onClick {
            selectionListener.invoke(item, adapterPosition)
        }
        logoImageView.onLongClick {
            selectionListener.invoke(item, adapterPosition)
            true
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

    private fun initState(isChecked: Boolean) {
        if (isChecked) {
            view.background = bgSelected
            logoImageView.setImageDrawable(checkIcon)
            statusImageView.visibility = View.INVISIBLE
        } else {
            view.background = null
            logoImageView.setImageDrawable(clientIcon)
            statusImageView.visibility = View.VISIBLE
        }
    }

    companion object {
        private const val BALANCES_TO_DISPLAY = 3
    }
}