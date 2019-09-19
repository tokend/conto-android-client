package org.tokend.template.features.swap.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
class SwapQuoteAmountDetails(
        @JsonProperty("quoteAmount")
        val quoteAmount: BigDecimal,
        @JsonProperty("quoteAssetCode")
        val quoteAssetCode: String
)