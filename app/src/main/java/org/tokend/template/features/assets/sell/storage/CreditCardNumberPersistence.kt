package org.tokend.template.features.assets.sell.storage

import android.content.SharedPreferences
import org.tokend.template.data.repository.base.ObjectPersistenceOnPrefs

class CreditCardNumberPersistence(
        preferences: SharedPreferences
) : ObjectPersistenceOnPrefs<String>(String::class.java, preferences, "card_number") {
    override fun serializeItem(item: String) = item
    override fun deserializeItem(serialized: String) = serialized
}