package org.tokend.template.features.send.model

enum class PaymentType(val code: String) {
    USER_TO_USER("p2p"),
    USER_TO_SERVICE("p2s"),
    SERVICE_TO_USER("s2p"),
    SERVICE_TO_SERVICE("s2s");

    val keyValueKey = "payment:$code"
}