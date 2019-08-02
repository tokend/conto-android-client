package org.tokend.template.features.send.recipient.logic

import android.content.Context
import android.provider.ContactsContract
import io.reactivex.Single
import org.tokend.template.features.send.recipient.model.Contact
import org.tokend.template.features.send.recipient.model.ContactData
import org.tokend.template.util.validator.GlobalPhoneNumberValidator
import org.tokend.template.view.util.PhoneNumberUtil

object ContactsManager {
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

    @JvmStatic
    fun getContacts(context: Context): Single<List<Contact>> {

        return Single.create {
            val contacts = arrayListOf<Contact>()
            val emails = arrayListOf<Pair<String, String>>()
            val numbers = arrayListOf<Pair<String, String>>()
            val idsWithEmail = arrayListOf<String>()
            val idsWithNumbers = arrayListOf<String>()

            val cr = context.contentResolver

            val emailsCursor = cr.query(EMAILS_CONTENT_URI, null, null, null, null)
            if (emailsCursor != null && emailsCursor.moveToFirst()) {
                do {
                    val id = emailsCursor.getString(emailsCursor.getColumnIndex(EMAIL_CONTACT_ID))
                    val email = emailsCursor.getString(emailsCursor.getColumnIndex(EMAIL))
                    idsWithEmail.add(id)
                    emails.add(id to email)
                } while (emailsCursor.moveToNext())
                emailsCursor.close()
            }

            val numbersCursor = cr.query(PHONE_NUMBERS_CONTENT_URI, null, null, null, null)
            if (numbersCursor != null && numbersCursor.moveToFirst()) {
                do {
                    val id = numbersCursor.getString(numbersCursor.getColumnIndex(NUMBER_CONTACT_ID))
                    val number = numbersCursor.getString(numbersCursor.getColumnIndex(NUMBER))
                    val cleanedNumber = PhoneNumberUtil.getCleanGlobalNumber(number)
                    if (!GlobalPhoneNumberValidator.isValid(cleanedNumber)) {
                        continue
                    }
                    idsWithNumbers.add(id)
                    numbers.add(id to cleanedNumber)
                } while (numbersCursor.moveToNext())
                numbersCursor.close()
            }

            val contactsCursor = cr.query(CONTACTS_CONTENT_URI, null, null, null, null)
            if (contactsCursor != null && contactsCursor.moveToFirst()) {
                do {
                    val id = contactsCursor.getString(contactsCursor.getColumnIndex(CONTACT_ID))

                    if (idsWithEmail.contains(id) || idsWithNumbers.contains(id)) {
                        val photoUri = contactsCursor.getString(contactsCursor.getColumnIndex(PHOTO))
                        val name = contactsCursor.getString(contactsCursor.getColumnIndex(NAME))
                        val dataById = arrayListOf<ContactData>()

                        emails.filter { pair -> pair.first == id }.forEach { pair ->
                            dataById.add(ContactData(pair.first, pair.second))
                        }

                        numbers.filter { pair -> pair.first == id }.forEach { pair ->
                            dataById.add(ContactData(pair.first, pair.second))
                        }

                        contacts.add(Contact(id, name, dataById, photoUri))
                    }
                } while (contactsCursor.moveToNext())
                contactsCursor.close()
            }
            contacts.sortWith(Comparator { o1, o2 -> o1.name.compareTo(o2.name, true) })
            it.onSuccess(contacts)
        }
    }
}
