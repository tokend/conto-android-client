package org.tokend.template.view.util

object PhoneNumberUtil {
    /**
     * Transforms any garbage to "+"-based global number
     * leaving only digits.
     *
     * Sample: +38 (095) 398-44-11 -> +380953984411
     */
    fun getCleanGlobalNumber(phoneNumber: CharSequence): String {
        return "+" + phoneNumber
                .mapNotNull { it.takeIf(Char::isDigit) }
                .joinToString("")
    }
}