package org.tokend.template.features.accountdetails.view

import android.content.Context
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.CoordinatorLayout
import android.util.AttributeSet
import android.view.View

/**
 * Resolves scrolling conflict when inner vertical scrolling
 * closes the sheet.
 */
class BottomSheetBehaviorWithNestedScrollChild<V : View>(context: Context?, attrs: AttributeSet?)
    : BottomSheetBehavior<V>(context, attrs) {
    private var nestedScrollChild: View? = null

    fun setNestedScrollChild(view: View) {
        this.nestedScrollChild = view
    }

    override fun onNestedPreScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View,
                                   dx: Int, dy: Int, consumed: IntArray, type: Int) {
        if (type != 1 && nestedScrollChild?.canScrollVertically(-1) == true) {
            return
        } else {
            super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)
        }
    }
}