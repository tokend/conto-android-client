package org.tokend.template.view.util

import android.support.annotation.IdRes
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.support.v7.app.AppCompatActivity

/**
 * Displays fragments in specified container
 * with transitions and back stack management
 */
class UserFlowFragmentDisplayer
private constructor(
        fragmentManagerGetter: () -> FragmentManager,
        @IdRes
        private val containerId: Int
) {
    constructor(activity: AppCompatActivity,
                @IdRes
                containerId: Int) : this({ activity.supportFragmentManager }, containerId)

    constructor(fragment: Fragment,
                @IdRes
                containerId: Int) : this({ fragment.childFragmentManager }, containerId)

    private val fragmentManager: FragmentManager by lazy(fragmentManagerGetter)

    /**
     * Displays given [fragment] in the container
     *
     * @param fragment fragment to display
     * @param tag unique tag for back stack
     * @param forward if set to true then "forward" transition will be used, "back" otherwise.
     * null will cause no transition at all
     */
    fun display(
            fragment: Fragment,
            tag: String,
            forward: Boolean?
    ) {
        fragmentManager.beginTransaction()
                .setTransition(
                        when (forward) {
                            true -> FragmentTransaction.TRANSIT_FRAGMENT_OPEN
                            false -> FragmentTransaction.TRANSIT_FRAGMENT_CLOSE
                            null -> FragmentTransaction.TRANSIT_NONE
                        }
                )
                .replace(containerId, fragment, tag)
                .addToBackStack(tag)
                .commit()

        fragmentManager.executePendingTransactions()
    }

    /**
     * Removes fragment with given [tag]
     */
    fun remove(tag: String) {
        val fragment = fragmentManager.findFragmentByTag(tag)
                ?: return

        fragmentManager.beginTransaction()
                .remove(fragment)
                .commit()
    }

    /**
     * @return true if pop was successful,
     * false if only one fragment left in the stack
     */
    fun tryPopBackStack(): Boolean {
        return if (fragmentManager.backStackEntryCount <= 1) {
            false
        } else {
            fragmentManager.popBackStackImmediate()
            true
        }
    }

    /**
     * Clears fragments back stack
     */
    fun clearBackStack() {
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }
}