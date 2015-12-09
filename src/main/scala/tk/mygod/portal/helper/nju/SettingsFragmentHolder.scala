package tk.mygod.portal.helper.nju

import android.os.Bundle
import android.support.v14.preference.PreferenceFragment
import android.view.{LayoutInflater, View, ViewGroup}
import tk.mygod.app.CircularRevealFragment
import tk.mygod.os.Build

/**
  * @author Mygod
  */
object SettingsFragmentHolder {
  def create[T <: PreferenceScreenFragment](fragment: T, title: String, rootKey: String) = {
    val args = new Bundle()
    args.putString(PreferenceFragment.ARG_PREFERENCE_ROOT, rootKey)
    fragment.setArguments(args)
    new SettingsFragmentHolder(fragment, title)
  }

  private final val TITLE = "TITLE"
}

class SettingsFragmentHolder[T <: PreferenceScreenFragment] extends CircularRevealFragment {
  import SettingsFragmentHolder._

  def this(f: T, t: String) {
    this()
    fragment = f
    title = t
    brandNew = true
  }

  private var fragment: T = _
  private var title: String = _
  private var brandNew: Boolean = _
  private lazy val childTag = getTag + '*'
  private lazy val fm = if (Build.version >= 17) getChildFragmentManager else getFragmentManager

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    val result = inflater.inflate(R.layout.fragment_holder, container, false)
    if (fragment == null) fragment = fm.findFragmentByTag(childTag).asInstanceOf[T]
    if (title == null) title = savedInstanceState.getString(TITLE)
    val args = fragment.getArguments
    configureToolbar(result, title,
      if (args == null || args.getString(PreferenceFragment.ARG_PREFERENCE_ROOT) == null) -1 else 0)
    result
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle) {
    super.onViewCreated(view, savedInstanceState)
    if (brandNew) fm.beginTransaction.add(R.id.content, fragment, childTag).commit
    fragment.parent = this
  }

  override def onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString(TITLE, title)
  }

  override def onDestroyView {
    super.onDestroyView
    if (Build.version < 17) fm.beginTransaction.remove(fragment).commitAllowingStateLoss
    fragment.parent = null
  }
}
