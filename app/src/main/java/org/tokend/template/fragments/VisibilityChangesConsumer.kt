package org.tokend.template.fragments

import io.reactivex.functions.Consumer
import io.reactivex.subjects.Subject

interface VisibilityChangesConsumer {
    val visibilityChangesSubject: Subject<Boolean>
}