package org.tokend.template.util.validator

object GlobalPhoneNumberValidator :
        RegexValidator("[+][0-9]{1,3}[0-9]{1,3}[0-9]{3}[0-9]{3,4}")