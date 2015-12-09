package tk.mygod.portal.helper.nju

import android.os.Bundle
import android.support.v7.preference.PreferenceScreen
import android.util.TypedValue
import android.view.View

/**
  * @author Mygod
  */
abstract class SettingsSubFragment[T](screen: PreferenceScreen)
  extends StoppablePreferenceFragment {
  def onCreatePreferences(savedInstanceState: Bundle, rootKey: String) {
    setPreferenceScreen(screen)
    ThrowableFuture(backgroundWork match {
      case Some(result) => runOnUiThread(onResult(result))
      case None => runOnUiThread(parent.exit())
    })
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle) {
    super.onViewCreated(view, savedInstanceState)
    val typedValue = new TypedValue
    view.getContext.getTheme.resolveAttribute(android.R.attr.windowBackground, typedValue, true)
    view.setBackgroundColor(typedValue.data)
  }

  protected def backgroundWork: Option[T]
  protected def onResult(result: T)
}
