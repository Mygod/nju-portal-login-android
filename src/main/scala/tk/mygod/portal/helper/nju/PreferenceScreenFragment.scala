package tk.mygod.portal.helper.nju

import android.os.Bundle
import tk.mygod.preference.PreferenceFragmentPlus

/**
  * @author Mygod
  */
abstract class PreferenceScreenFragment extends PreferenceFragmentPlus {
  override def onCreatePreferences(savedInstanceState: Bundle, rootKey: String) =
    setPreferencesFromResource(R.xml.settings, rootKey)
}
