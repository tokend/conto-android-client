package org.tokend.template.view.util

import android.content.Context
import android.widget.TextView
import org.tokend.sdk.factory.HttpClientFactory
import org.tokend.template.BuildConfig
import ru.noties.markwon.Markwon
import ru.noties.markwon.SpannableConfiguration
import ru.noties.markwon.il.AsyncDrawableLoader
import ru.noties.markwon.spans.SpannableTheme

class MarkdownUtil(private val configuration: SpannableConfiguration) {

    fun toMarkdown(content: String)
            : CharSequence {
        return Markwon.markdown(configuration, content)
    }

    companion object {
        fun getDefaultConfiguration(context: Context) = SpannableConfiguration
                .builder(context)
                .theme(
                        SpannableTheme
                                .builderWithDefaults(context)
                                .headingBreakHeight(0)
                                .thematicBreakHeight(0)
                                .build()
                )
                .asyncDrawableLoader(
                        AsyncDrawableLoader
                                .builder()
                                .client(
                                        HttpClientFactory()
                                                .getBaseHttpClientBuilder(
                                                        withLogs = BuildConfig.WITH_LOGS
                                                )
                                                .build()
                                )
                                .build()
                )
                .build()

        fun setMarkdownText(markdownContent: CharSequence,
                            destination: TextView) {
            Markwon.setText(destination, markdownContent)
        }
    }
}