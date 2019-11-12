package org.tokend.template.features.invest.logic

import com.google.gson.JsonParser
import io.reactivex.Single
import org.tokend.sdk.api.blobs.model.Blob
import org.tokend.template.data.repository.BlobsRepository
import org.tokend.template.view.util.MarkdownUtil

/**
 * Loads sale overview blob content and transforms it to markdown
 */
class SaleOverviewMarkdownLoader(private val markdownUtil: MarkdownUtil,
                                 private val blobsRepository: BlobsRepository) {
    fun load(blobId: String): Single<CharSequence> {
        return blobsRepository
                .getById(blobId)
                .map(Blob::valueString)
                .map { rawValue ->
                    try {
                        // Unescape content.
                        JsonParser().parse(rawValue).asString
                    } catch (e: Exception) {
                        rawValue
                    }
                }
                .map { markdownUtil.toMarkdown(it) }
    }
}