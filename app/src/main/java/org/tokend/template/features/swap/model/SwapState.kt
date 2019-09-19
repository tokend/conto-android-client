package org.tokend.template.features.swap.model

enum class SwapState {
    CREATED,
    WAITING_FOR_CLOSE_BY_SOURCE,
    CAN_BE_RECEIVED_BY_DEST,
    COMPLETED,
    CANCELED,
    CANCELED_BY_COUNTERPARTY
}