package tk.mygod.portal.helper.nju

import tk.mygod.app.StoppableFragment
import tk.mygod.preference.PreferenceFragmentPlus

/**
  * @author Mygod
  */
abstract class StoppablePreferenceFragment extends PreferenceFragmentPlus {
  var parent: StoppableFragment = _
}
