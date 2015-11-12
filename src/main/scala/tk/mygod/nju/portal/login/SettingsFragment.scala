package tk.mygod.nju.portal.login

import android.os.Bundle
import android.support.v7.preference.Preference
import tk.mygod.preference.{EditTextPreferenceDialogFragment, EditTextPreference, PreferenceFragmentPlus}

final class SettingsFragment extends PreferenceFragmentPlus {
  def onCreatePreferences(savedInstanceState: Bundle, rootKey: String) {
    getPreferenceManager.setSharedPreferencesName(App.prefName)
    addPreferencesFromResource(R.xml.settings)
  }

  override def onDisplayPreferenceDialog(preference: Preference) =
    if (preference.isInstanceOf[EditTextPreference])
      displayPreferenceDialog(new EditTextPreferenceDialogFragment(preference.getKey))
    else super.onDisplayPreferenceDialog(preference)
}
