package org.tokend.template.features.send.recipient.contacts.repository

import android.content.Context
import android.provider.ContactsContract
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.tokend.template.data.repository.base.RepositoryCache
import org.tokend.template.data.repository.base.SimpleMultipleItemsRepository
import org.tokend.template.features.send.recipient.contacts.model.ContactRecord

class ContactsRepository(val context: Context,
                         itemsCache: RepositoryCache<ContactRecord>
) : SimpleMultipleItemsRepository<ContactRecord>(itemsCache) {

    override fun getItems(): Single<List<ContactRecord>> {
        val contacts = mutableListOf<ContactRecord>()
        val credentialsByIds = mutableMapOf<String, MutableSet<String>>()

        val contentResolver = context.contentResolver

        return Single.defer {
            val emailsCursor = contentResolver.query(EMAILS_CONTENT_URI,
                    null, null, null, null)
            if (emailsCursor != null && emailsCursor.moveToFirst()) {
                do {
                    val id = emailsCursor.getString(emailsCursor.getColumnIndex(EMAIL_CONTACT_ID))
                    val email = emailsCursor.getString(emailsCursor.getColumnIndex(EMAIL))
                    credentialsByIds.getOrPut(id, ::mutableSetOf).add(email)
                } while (emailsCursor.moveToNext())
                emailsCursor.close()
            }

            val phonesCursor = contentResolver.query(PHONE_NUMBERS_CONTENT_URI,
                    null, null, null, null)
            if (phonesCursor != null && phonesCursor.moveToFirst()) {
                do {
                    val id = phonesCursor.getString(phonesCursor.getColumnIndex(NUMBER_CONTACT_ID))
                    val phoneNumber = phonesCursor.getString(phonesCursor.getColumnIndex(NUMBER))
                    credentialsByIds.getOrPut(id, ::mutableSetOf).add(phoneNumber)
                } while (phonesCursor.moveToNext())
                phonesCursor.close()
            }

            val dataCursor = contentResolver.query(CONTACTS_CONTENT_URI,
                    null, null, null, null)
            if (dataCursor != null && dataCursor.moveToFirst()) {
                do {
                    val id = dataCursor.getString(dataCursor.getColumnIndex(CONTACT_ID))

                    if (credentialsByIds.containsKey(id)) {
                        val photoUri = dataCursor.getString(dataCursor.getColumnIndex(PHOTO))
                        val name = dataCursor.getString(dataCursor.getColumnIndex(NAME))

                        contacts.add(ContactRecord(
                                id = id,
                                name = name,
                                photoUri = photoUri,
                                credentials = credentialsByIds.getValue(id).toList()
                        ))
                    }
                } while (dataCursor.moveToNext())
                dataCursor.close()
            }

            Single.just<List<ContactRecord>>(contacts)
        }.subscribeOn(Schedulers.newThread())
    }

    private companion object {
        private val CONTACTS_CONTENT_URI = ContactsContract.Contacts.CONTENT_URI
        private val EMAILS_CONTENT_URI = ContactsContract.CommonDataKinds.Email.CONTENT_URI
        private const val EMAIL_CONTACT_ID = ContactsContract.CommonDataKinds.Email.CONTACT_ID
        private val PHONE_NUMBERS_CONTENT_URI = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        private const val NUMBER_CONTACT_ID = ContactsContract.CommonDataKinds.Phone.CONTACT_ID
        private const val CONTACT_ID = ContactsContract.Contacts._ID

        private const val NAME = ContactsContract.Contacts.DISPLAY_NAME
        private const val EMAIL = ContactsContract.CommonDataKinds.Email.DATA
        private const val PHOTO = ContactsContract.CommonDataKinds.Photo.PHOTO_URI
        private const val NUMBER = ContactsContract.CommonDataKinds.Phone.NUMBER
    }
}