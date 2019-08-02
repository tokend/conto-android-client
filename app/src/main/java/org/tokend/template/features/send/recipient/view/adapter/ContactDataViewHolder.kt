package org.tokend.template.features.send.recipient.view.adapter

import android.view.View
import kotlinx.android.synthetic.main.item_email.view.*
import org.tokend.template.features.send.recipient.model.ContactData
import org.tokend.template.view.adapter.base.BaseViewHolder

class ContactDataViewHolder(itemView: View) : BaseViewHolder<Any>(itemView) {

    override fun bind(item: Any) {
        item as ContactData
        itemView.email_text_view.text = item.data
    }
}