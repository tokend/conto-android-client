package org.tokend.template.features.kyc.model

sealed class ActiveKyc {
    object Missing : ActiveKyc()
    class Form(val formData: KycForm) : ActiveKyc()
}