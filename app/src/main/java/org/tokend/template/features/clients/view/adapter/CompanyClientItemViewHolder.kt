package org.tokend.template.features.clients.view.adapter

import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
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
import org.tokend.template.features.clients.model.CompanyClientRecord
import org.tokend.template.view.adapter.base.BaseViewHolder
import org.tokend.template.view.adapter.base.SimpleItemClickListener
import org.tokend.template.view.util.LogoUtil
import org.tokend.template.view.util.formatter.AmountFormatter

class CompanyClientItemViewHolder(
        view: View,
        private val amountFormatter: AmountFormatter
) : BaseViewHolder<CompanyClientListItem>(view) {
    private val emailTextView: TextView = view.email_text_view
    private val logoImageView: ImageView = view.logo_image_view
    private val statusImageView: ImageView = view.status_image_view
    private val balancesLayout: ViewGroup = view.balances_layout
    private val noBalancesTextView: TextView = view.no_balances_text_view
    private val dividerView: View = view.divider_view
    private val transition = view.background as TransitionDrawable
    private val checkIcon = ContextCompat.getDrawable(view.context, R.drawable.ic_check_colorized)
    private lateinit var clientIcon: Drawable


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
        emailTextView.text = item.email
        clientIcon = LogoUtil.generateLogo(item.email.toUpperCase(), view.context, logoSize)
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
             selectionListener: (String, Boolean) -> Unit) {
        super.bind(item, clickListener)
        initState(item.isChecked)
        view.onLongClick {
            changeSelectionState(item, selectionListener)
            true
        }
        logoImageView.onClick {
            changeSelectionState(item, selectionListener)
        }
        logoImageView.onLongClick {
            changeSelectionState(item, selectionListener)
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
        if(isChecked) {
            transition.level = 1000
            logoImageView.setImageDrawable(checkIcon)
            statusImageView.visibility = View.INVISIBLE
        } else {
            transition.resetTransition()
            logoImageView.setImageDrawable(clientIcon)
            statusImageView.visibility = View.VISIBLE
        }
    }

    private fun changeSelectionState(item: CompanyClientListItem,
                                     selectionListener: (String, Boolean) -> Unit) {
        item.isChecked = !item.isChecked
        selectionListener.invoke(item.id, item.isChecked)
        animateSelection(item.isChecked)
    }

    private fun animateSelection(isChecked: Boolean) {
        if(isChecked) {
            transition.startTransition(TRANSITION_DURATION)
            statusImageView.visibility = View.INVISIBLE
            logoImageView.setImageDrawable(checkIcon)
        } else {
            transition.reverseTransition(TRANSITION_DURATION)
            statusImageView.visibility = View.VISIBLE
            logoImageView.setImageDrawable(clientIcon)
        }
    }

    companion object {
        private const val BALANCES_TO_DISPLAY = 3
        private const val TRANSITION_DURATION = 250
    }
}