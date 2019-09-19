package org.tokend.template.features.swap.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
class SwapQuoteAmountDetails(
        @JsonProperty("quote_amount")
        val quoteAmount: BigDecimal,
        @JsonProperty("quote_asset_code")
        val quoteAssetCode: String
)