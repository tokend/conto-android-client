package org.tokend.template.features.swap.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
class SwapDetails(
        @JsonProperty("quoteAmount")
        val quoteAmount: BigDecimal,
        @JsonProperty("quoteAssetCode")
        val quoteAssetCode: String,
        @JsonProperty("sourceEmail")
        val sourceEmail: String,
        @JsonProperty("destEmail")
        val destEmail: String
)