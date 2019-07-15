package org.tokend.template.features.invites.view

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import kotlinx.android.synthetic.main.activity_invite_new_users.*
import kotlinx.android.synthetic.main.toolbar.*
import org.jetbrains.anko.onClick
import org.tokend.template.R
import org.tokend.template.activities.BaseActivity
import org.tokend.template.extensions.hasError
import org.tokend.template.util.validator.EmailValidator
import org.tokend.template.view.util.input.SimpleTextWatcher

class InviteNewUsersActivity : AppCompatActivity() {

    private var invitees: List<String>? = null

    private var canInvite = false
        set(value) {
            field = value
            invite_button.isEnabled = value
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initViews()
        updateInviteAvailability()
    }

    private fun initViews() {
        initToolbar()
        initInputField()
        initInviteButton()
    }

    private fun initToolbar() {
        setContentView(R.layout.activity_invite_new_users)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.invite_new_users_title)
    }

    private fun initInputField() {
        emails_edit_text.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                emails_edit_text.error = null
                updateInviteAvailability()
            }
        })
    }

    private fun initInviteButton() {
        invite_button.onClick {
            tryToInvite()
        }
    }

    private fun updateInviteAvailability() {
        canInvite = !emails_edit_text.hasError()
                && !emails_edit_text.text.isNullOrBlank()
    }

    private fun tryToInvite() {
        invitees = emails_edit_text.text
                ?.split(',')
                ?.filter { it.isNotBlank() }
                ?.map { it.trim() }

        checkEmails()

        if (!canInvite) {
            return
        }


    }

    private fun checkEmails() {
        invitees?.find {
            !EmailValidator.isValid(it)
        }?.let {
            emails_edit_text.error = getString(R.string.error_missed_comma_or_invalid_email)
            updateInviteAvailability()
        }
    }
}
