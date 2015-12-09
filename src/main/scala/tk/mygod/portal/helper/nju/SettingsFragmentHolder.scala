package tk.mygod.portal.helper.nju

import android.os.Bundle
import android.support.v7.preference.PreferenceScreen
import android.view.{LayoutInflater, View, ViewGroup}
import tk.mygod.app.CircularRevealFragment

/**
  * @author Mygod
  */
class SettingsFragmentHolder[T <: StoppablePreferenceFragment](val fragment: T, screen: PreferenceScreen = null)
  extends CircularRevealFragment {
  fragment.parent = this

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    val result = inflater.inflate(R.layout.fragment_holder, container, false)
    configureToolbar(result, if (screen == null) R.string.app_name else screen.getTitle, if (screen == null) -1 else 0)
    result
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle) {
    super.onViewCreated(view, savedInstanceState)
    getChildFragmentManager.beginTransaction.add(R.id.content, fragment, null).commit
  }
}
