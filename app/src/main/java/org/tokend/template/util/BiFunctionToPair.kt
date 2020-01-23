package org.tokend.template.util

import io.reactivex.functions.BiFunction

/**
 * Transforms arguments to [Pair]
 */
class BiFunctionToPair<A : Any, B : Any> : BiFunction<A, B, Pair<A, B>> {
    override fun apply(a: A, b: B): Pair<A, B> =
            a to b

    companion object {
        inline fun <reified A: Any, reified B: Any>forTypes() = BiFunctionToPair<A, B>()
    }
}