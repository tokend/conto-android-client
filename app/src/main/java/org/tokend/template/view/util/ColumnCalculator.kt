package org.tokend.template.view.util

import android.app.Activity
import android.util.DisplayMetrics
import org.tokend.template.R

object ColumnCalculator {
    fun getColumnCount(activity: Activity,
                       maxContentWidthPx: Int
                       = activity.resources.getDimensionPixelSize(R.dimen.max_content_width)): Int {
        val displayMetrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(displayMetrics)

        val screenWidth = displayMetrics.widthPixels.toDouble()
        return (screenWidth / maxContentWidthPx)
                .let { Math.ceil(it) }
                .toInt()
    }
}