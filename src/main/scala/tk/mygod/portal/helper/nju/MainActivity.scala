package tk.mygod.portal.helper.nju

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content._
import android.os.Bundle
import android.provider.Settings
import android.support.v14.preference.PreferenceFragment
import android.support.v14.preference.PreferenceFragment.OnPreferenceStartScreenCallback
import android.support.v4.app.ActivityOptionsCompat
import android.support.v7.app.AlertDialog
import android.support.v7.preference.PreferenceScreen
import android.text.TextUtils
import tk.mygod.app.{CircularRevealActivity, ToolbarActivity}
import tk.mygod.util.Conversions._

object MainActivity {
  private val ASKED_BOUND_CONNECTION = "misc.useBoundConnections.asked"
  val ACTION_VIEW_NOTICES = "tk.mygod.portal.helper.nju.MainActivity.VIEW_NOTICES"
}

final class MainActivity extends ToolbarActivity with OnSharedPreferenceChangeListener
  with OnPreferenceStartScreenCallback {
  import MainActivity._

  private lazy val serviceIntent = intent[NetworkMonitor]

  override protected def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    configureToolbar()
    testBoundConnections()
    startNetworkMonitor
    app.pref.registerOnSharedPreferenceChangeListener(this)
    if (TextUtils.isEmpty(PortalManager.username) || TextUtils.isEmpty(PortalManager.password))
      makeSnackbar(R.string.settings_account_missing).show
  }

  override protected def onDestroy {
    app.pref.unregisterOnSharedPreferenceChangeListener(this)
    super.onDestroy
  }

  private def manageWriteSettings(dialog: DialogInterface = null, which: Int = 0) = startActivity(
    new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).setData("package:" + getPackageName))
  def testBoundConnections(requested: Boolean = false) = app.boundConnectionsAvailable match {
    case 1 => if (requested) {
      manageWriteSettings()
      true
    } else if (!app.pref.getBoolean(ASKED_BOUND_CONNECTION, false)) {
      new AlertDialog.Builder(this).setTitle(R.string.bound_connections_title)
        .setPositiveButton(android.R.string.yes, manageWriteSettings: DialogInterface.OnClickListener)
        .setNegativeButton(android.R.string.no, ((_, _) => app.editor.putBoolean(ASKED_BOUND_CONNECTION, true)): DialogInterface.OnClickListener)
        .setMessage(R.string.bound_connections_message).create.show
      true
    } else false
    case 2 => if (requested) {
      manageWriteSettings()
      true
    } else false
    case _ => false
  }

  override def onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) = key match {
    case SERVICE_STATUS => if (app.serviceStatus > 0) {
      app.setEnabled[BootReceiver](true)
      app.setEnabled[NetworkMonitorListener](true)
      startNetworkMonitor
    } else {
      app.setEnabled[BootReceiver](false)
      app.setEnabled[NetworkMonitorListener](false)
      stopService(serviceIntent)
    }
    case NoticeManager.SYNC_INTERVAL => NoticeManager.updatePeriodicSync
    case _ => // ignore
  }

  def startNetworkMonitor = if (app.serviceStatus > 0) startService(serviceIntent)

  def onPreferenceStartScreen(fragment: PreferenceFragment, screen: PreferenceScreen) = {
    startActivity(CircularRevealActivity.putLocation(intent[UsageActivity], getLocationOnScreen),
      ActivityOptionsCompat.makeSceneTransitionAnimation(this).toBundle)
    true
  }
}
