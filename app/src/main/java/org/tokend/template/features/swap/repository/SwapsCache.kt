package org.tokend.template.features.swap.repository

import org.tokend.template.data.repository.base.MemoryOnlyRepositoryCache
import org.tokend.template.features.swap.model.SwapRecord

class SwapsCache : MemoryOnlyRepositoryCache<SwapRecord>() {
    override fun sortItems() {
        mItems.sortByDescending(SwapRecord::createdAt)
    }
}