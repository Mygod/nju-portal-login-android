package tk.mygod.portal.helper.nju

import android.os.Bundle
import be.mygod.preference.PreferenceFragmentPlus

/**
  * @author Mygod
  */
final class UsageFragment extends PreferenceFragmentPlus {
  override def onCreatePreferences(savedInstanceState: Bundle, rootKey: String) =
    setPreferencesFromResource(R.xml.settings, "status.usage")
}
