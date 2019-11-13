package org.tokend.template.features.booking.add.view

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.util.ObservableTransformers

class BookingActivity : BaseActivity() {

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.fragment_user_flow)

        initToolbar()

        toTimeScreen()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbar)
        title = getString(R.string.book_a_seat)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun toTimeScreen() {
        val fragment = BookingTimeFragment()

        fragment.resultSingle
                .compose(ObservableTransformers.defaultSchedulersSingle())
                .subscribeBy()
                .addTo(compositeDisposable)

        displayFragment(fragment, "time", null)
    }

    private fun displayFragment(
            fragment: Fragment,
            tag: String,
            forward: Boolean?
    ) {
        supportFragmentManager.beginTransaction()
                .setTransition(
                        when (forward) {
                            true -> FragmentTransaction.TRANSIT_FRAGMENT_OPEN
                            false -> FragmentTransaction.TRANSIT_FRAGMENT_CLOSE
                            null -> FragmentTransaction.TRANSIT_NONE
                        }
                )
                .replace(R.id.fragment_container_layout, fragment)
                .addToBackStack(tag)
                .commit()
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount <= 1) {
            finish()
        } else {
            supportFragmentManager.popBackStackImmediate()
        }
    }
}
