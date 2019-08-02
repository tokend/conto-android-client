package org.tokend.template.features.send.recipient.view.adapter

import org.tokend.template.features.send.recipient.model.Contact
import org.tokend.template.features.send.recipient.model.ContactData

class ContactListItem(
        val id: String,
        val name: String,
        val data: List<ContactData>,
        val photoUri: String?,
        var isExpanded: Boolean
) {
    constructor(source: Contact): this(
            id = source.id,
            name = source.name,
            data = source.data,
            photoUri = source.photoUri,
            isExpanded = false
    )
}