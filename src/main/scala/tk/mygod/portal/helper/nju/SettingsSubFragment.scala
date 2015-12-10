package tk.mygod.portal.helper.nju

import android.os.Bundle
import android.support.v14.preference.PreferenceFragment
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener
import android.view.View

/**
  * @author Mygod
  */
abstract class SettingsSubFragment[T] extends SettingsFragmentBase with OnRefreshListener {
  final def setRootKey(rootKey: String) {
    val args = new Bundle
    args.putString(PreferenceFragment.ARG_PREFERENCE_ROOT, rootKey)
    setArguments(args)
  }
  override def onCreatePreferences(savedInstanceState: Bundle, rootKey: String) =
    setPreferencesFromResource(R.xml.settings, rootKey)

  override def layout = R.layout.fragment_refreshable
  private var swiper: SwipeRefreshLayout = _
  override def onViewCreated(view: View, savedInstanceState: Bundle) {
    super.onViewCreated(view, savedInstanceState)
    swiper = view.findViewById(R.id.preference_holder).asInstanceOf[SwipeRefreshLayout]
    swiper.setColorSchemeResources(R.color.material_accent_500, R.color.material_primary_500)
    swiper.setOnRefreshListener(this)
    swiper.setRefreshing(true)
    ThrowableFuture(work)
  }

  private def work = (try backgroundWork catch {
    case e: PortalManager.NetworkUnavailableException =>
      app.showToast(app.getString(R.string.error_network_unavailable))
      None
    case e: Exception =>
      e.printStackTrace
      app.showToast(e.getMessage)
      None
  }) match {
    case Some(result) => runOnUiThread {
      onResult(result)
      swiper.setRefreshing(false)
    }
    case None => runOnUiThread(exit())
  }
  protected def backgroundWork: Option[T]
  protected def onResult(result: T)

  override def onRefresh {
    swiper.setRefreshing(true)
    ThrowableFuture(work)
  }
}
