package tk.mygod.portal.helper.nju

import android.os.Bundle
import be.mygod.preference.PreferenceFragmentPlus

final class UsageFragment extends PreferenceFragmentPlus {
  override def onCreatePreferences(savedInstanceState: Bundle, rootKey: String): Unit =
    setPreferencesFromResource(R.xml.settings, "status.usage")
}
