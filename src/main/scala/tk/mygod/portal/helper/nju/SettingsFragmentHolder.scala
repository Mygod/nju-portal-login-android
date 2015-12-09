package tk.mygod.portal.helper.nju

import android.os.Bundle
import android.support.v7.preference.PreferenceScreen
import android.view.{LayoutInflater, View, ViewGroup}
import tk.mygod.app.CircularRevealFragment
import tk.mygod.os.Build

/**
  * @author Mygod
  */
class SettingsFragmentHolder[T <: StoppablePreferenceFragment](val fragment: T, screen: PreferenceScreen = null)
  extends CircularRevealFragment {
  fragment.parent = this

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    val result = inflater.inflate(R.layout.fragment_holder, container, false)
    if (screen == null) configureToolbar(result, R.string.app_name, -1)
    else configureToolbar(result, screen.getTitle, 0)
    result
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle) {
    super.onViewCreated(view, savedInstanceState)
    (if (Build.version >= 17) getChildFragmentManager else getFragmentManager)
      .beginTransaction.add(R.id.content, fragment, null).commit
  }

  override def onDestroyView {
    super.onDestroyView
    if (Build.version < 17) getFragmentManager.beginTransaction.remove(fragment).commitAllowingStateLoss
  }
}
