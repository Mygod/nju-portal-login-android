package tk.mygod.nju.portal.login

import android.os.Bundle
import android.support.v7.preference.Preference
import tk.mygod.preference.{EditTextPreferenceDialogFragment, EditTextPreference, PreferenceFragmentPlus}

final class SettingsFragment extends PreferenceFragmentPlus {
  private lazy val activity = getActivity.asInstanceOf[MainActivity]

  def onCreatePreferences(savedInstanceState: Bundle, rootKey: String) {
    getPreferenceManager.setSharedPreferencesName(App.prefName)
    addPreferencesFromResource(R.xml.settings)

    findPreference(App.autoConnectEnabledKey)
      .setOnPreferenceChangeListener((preference: Preference, newValue: Any) => {
        App.instance.autoConnectEnabled(newValue.asInstanceOf[Boolean])
        true
      })

    var preference = findPreference("autoConnect.useSystemNetworkMonitor")
    preference.setEnabled(App.instance.systemNetworkMonitorAvailable >= 2)
    preference.setOnPreferenceClickListener((preference: Preference) => activity.testNetworkMonitor(true))
    preference.setSummary("Use system NetworkMonitor to save battery.\n" +
      (App.instance.systemNetworkMonitorAvailable match {
        case 0 => "Not available on Android 4.x."
        case 1 => "Requires root."
        case 2 => "Needs changing to a system privileged app."
        case 3 => "Enabled. Click to uninstall."
      }))

    preference = findPreference("autoConnect.useBindedConnections")
    val available = App.instance.bindedConnectionsAvailable
    preference.setEnabled(available == 1 || available == 2)
    preference.setOnPreferenceClickListener((preference: Preference) => activity.testBindedConnections(true))
    preference.setSummary("Use binded connections to avoid mobile traffic.\n" +
      (App.instance.bindedConnectionsAvailable match {
        case 0 => "Not available on Android 4.x."
        case 1 => "Needs permission to modify system settings."
        case 2 => "Enabled. Click to revoke the permission."
        case 3 => "Enabled."
      }))

    findPreference("manualConnect.login").setOnPreferenceClickListener((preference: Preference) => {
      PortalManager.login()
      true
    })
    findPreference("manualConnect.logout").setOnPreferenceClickListener((preference: Preference) => {
      PortalManager.logout
      true
    })
  }

  override def onDisplayPreferenceDialog(preference: Preference) =
    if (preference.isInstanceOf[EditTextPreference])
      displayPreferenceDialog(new EditTextPreferenceDialogFragment(preference.getKey))
    else super.onDisplayPreferenceDialog(preference)
}
