package tk.mygod.portal.helper.nju

import android.os.Bundle
import android.view.{LayoutInflater, ViewGroup}
import tk.mygod.app.CircularRevealFragment
import tk.mygod.preference.PreferenceFragmentPlus

/**
  * @author Mygod
  */
abstract class SettingsFragmentBase extends PreferenceFragmentPlus with CircularRevealFragment {
  def layout = R.layout.fragment_holder
  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    val preference = super.onCreateView(inflater, container, savedInstanceState)
    val result = inflater.inflate(layout, container, false)
    result.findViewById(R.id.preference_holder).asInstanceOf[ViewGroup].addView(preference)
    result
  }
}
