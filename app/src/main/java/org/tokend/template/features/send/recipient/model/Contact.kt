package org.tokend.template.features.send.recipient.model

data class Contact(val id: String,
                   val name: String,
                   val data: List<ContactData>,
                   val photoUri: String?)