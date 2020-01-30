package org.tokend.template.util.validator

object CreditCardNumberValidator : CharSequenceValidator {
    override fun isValid(sequence: CharSequence?): Boolean {
        return if (sequence != null)
            performLuhnCheck(sequence)
        else
            false
    }

    private fun performLuhnCheck(content: CharSequence): Boolean {
        if (content.isEmpty() || content.length != 16) {
            return false
        }

        var checksum = 0

        for (i in content.length - 1 downTo 0 step 2) {
            checksum += content[i] - '0'
        }
        for (i in content.length - 2 downTo 0 step 2) {
            val n: Int = (content[i] - '0') * 2
            checksum += if (n > 9) n - 9 else n
        }

        return checksum % 10 == 0
    }
}