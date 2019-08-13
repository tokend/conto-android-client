package org.tokend.template.activities

import android.graphics.drawable.ColorDrawable
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import com.mikepenz.materialdrawer.AccountHeader
import com.mikepenz.materialdrawer.AccountHeaderBuilder
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import org.tokend.template.R
import org.tokend.template.features.clients.view.CompanyClientsFragment
import org.tokend.template.features.dashboard.balances.view.CompanyBalancesFragment
import org.tokend.template.features.settings.SettingsFragment
import org.tokend.template.features.signin.model.ForcedAccountType
import org.tokend.template.util.Navigator
import org.tokend.template.util.ProfileUtil

class CorporateMainActivity : MainActivity() {
    override val defaultFragmentId = CompanyClientsFragment.ID

    override fun getNavigationItems(): List<PrimaryDrawerItem> {
        return mutableListOf(
                PrimaryDrawerItem()
                        .withName(R.string.clients_title)
                        .withIdentifier(CompanyClientsFragment.ID)
                        .withIcon(R.drawable.ic_accounts),
                PrimaryDrawerItem()
                        .withName(R.string.balances_screen_title)
                        .withIdentifier(CompanyBalancesFragment.ID)
                        .withIcon(R.drawable.ic_coins)
        ).apply { addAll(super.getNavigationItems()) }
    }

    override fun addRequiredNavigationItems(builder: DrawerBuilder,
                                            items: Map<Long, PrimaryDrawerItem>) {
        builder.apply {
            addDrawerItems(items[CompanyClientsFragment.ID])
            addDrawerItems(items[CompanyBalancesFragment.ID])
            addDrawerItems(items[SettingsFragment.ID])
            addDrawerItems(DividerDrawerItem())
            addDrawerItems(items[CONTRIBUTE_ITEM_ID])
        }
    }

    override fun getHeaderInstance(email: String): AccountHeader {
        return AccountHeaderBuilder()
                .withActivity(this)
                .withAccountHeader(R.layout.navigation_drawer_header)
                .withHeaderBackground(
                        ColorDrawable(ContextCompat.getColor(this, R.color.white))
                )
                .withTextColor(ContextCompat.getColor(this, R.color.primary_text))
                .withSelectionListEnabledForSingleProfile(false)
                .withProfileImagesVisible(true)
                .withDividerBelowHeader(true)
                .addProfiles(getProfileHeaderItem(email))
                .withOnAccountHeaderListener { _, _, _ ->
                    openAccountIdShare()
                    false
                }
                .build()
                .also(this::initAccountTypeSwitch)
    }

    override fun getProfileHeaderItem(email: String): ProfileDrawerItem {
        val kycState = kycStateRepository.item
        val avatarUrl = ProfileUtil.getAvatarUrl(kycState, urlConfigProvider, email)
        val name = ProfileUtil.getDisplayedName(kycState, email)

        return ProfileDrawerItem()
                .withIdentifier(1)
                .withName(name)
                .withEmail(getString(R.string.kyc_form_type_corporate))
                .apply {
                    avatarUrl?.also { withIcon(it) }
                }
    }

    override fun getCompaniesProfileItems(): Collection<ProfileDrawerItem> = emptyList()

    override fun getAccountTypeSwitchHint(): String = getString(R.string.switch_to_client)

    override fun switchAccountType() {
        AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setMessage(R.string.switch_to_client_confirmation)
                .setPositiveButton(R.string.yes) { _, _ ->
                    Navigator.from(this).toForcingAccountType(ForcedAccountType.GENERAL)
                }
                .setNegativeButton(R.string.no, null)
                .show()
    }

    override fun openAccountIdShare() {
        val walletInfo = walletInfoProvider.getWalletInfo() ?: return
        Navigator.from(this).openAccountQrShare(walletInfo, true)
    }

    override fun getFragment(screenIdentifier: Long): Fragment? {
        return super.getFragment(screenIdentifier) ?: when (screenIdentifier) {
            CompanyClientsFragment.ID -> factory.getCompanyClientsFragment()
            CompanyBalancesFragment.ID -> factory.getCompanyBalancesFragment()
            else -> null
        }
    }
}