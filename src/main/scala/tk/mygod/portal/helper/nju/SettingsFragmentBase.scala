package tk.mygod.portal.helper.nju

import android.os.Bundle
import android.support.v14.preference.PreferenceFragment
import android.view.{LayoutInflater, ViewGroup}
import tk.mygod.app.CircularRevealFragment

/**
  * @author Mygod
  */
class SettingsFragmentBase extends PreferenceScreenFragment with CircularRevealFragment {
  def setRootKey(rootKey: String) {
    val args = new Bundle
    args.putString(PreferenceFragment.ARG_PREFERENCE_ROOT, rootKey)
    setArguments(args)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    val preference = super.onCreateView(inflater, container, savedInstanceState)
    val result = inflater.inflate(R.layout.fragment_holder, container, false)
    result.findViewById(R.id.preference_holder).asInstanceOf[ViewGroup].addView(preference)
    result
  }
}
