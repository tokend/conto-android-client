package org.tokend.template.features.shaketopay.view.adapter

import org.tokend.template.features.shaketopay.model.NearbyUserRecord

class NearbyUserListItem(
        val id: String,
        val name: String,
        val avatarUrl: String?,
        val source: NearbyUserRecord?
) {
    constructor(source: NearbyUserRecord) : this(
            id = source.accountId,
            name = source.name,
            avatarUrl = source.avatarUrl,
            source = source
    )
}