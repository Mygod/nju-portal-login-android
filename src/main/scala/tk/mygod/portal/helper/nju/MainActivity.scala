package tk.mygod.portal.helper.nju

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content._
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.support.v14.preference.PreferenceFragment
import android.support.v14.preference.PreferenceFragment.OnPreferenceStartScreenCallback
import android.support.v7.app.AlertDialog
import android.support.v7.preference.PreferenceScreen
import android.text.TextUtils
import tk.mygod.app.{FragmentStackActivity, LocationObservedActivity}
import tk.mygod.util.Conversions._

object MainActivity {
  private val ASKED_BOUND_CONNECTION = "misc.useBoundConnections.asked"
}

final class MainActivity extends FragmentStackActivity with LocationObservedActivity
  with OnSharedPreferenceChangeListener with OnPreferenceStartScreenCallback {
  import MainActivity._

  private lazy val serviceIntent = intent[NetworkMonitor]
  var noticeFragment: NoticeFragment = _

  override protected def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    if (getFragmentManager.getBackStackEntryCount <= 0) push(new SettingsFragment)
    testBoundConnections()
    startNetworkMonitor
    app.pref.registerOnSharedPreferenceChangeListener(this)
    if (TextUtils.isEmpty(PortalManager.username) || TextUtils.isEmpty(PortalManager.password))
      makeSnackbar(R.string.settings_account_missing).show
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
      getPackageManager.setComponentEnabledSetting(new ComponentName(this, classOf[NetworkMonitorListener]),
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
      startNetworkMonitor
    } else {
      getPackageManager.setComponentEnabledSetting(new ComponentName(this, classOf[NetworkMonitorListener]),
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
      stopService(serviceIntent)
    }
    case RELOGIN_DELAY => if (NetworkMonitor.instance != null) NetworkMonitor.instance.reloginThread.interrupt
    case NoticeManager.SYNC_INTERVAL => NoticeManager.updatePeriodicSync
    case _ => // ignore
  }

  def startNetworkMonitor = if (app.serviceStatus > 0) startService(serviceIntent)

  def onPreferenceStartScreen(fragment: PreferenceFragment, screen: PreferenceScreen) = {
    val fragment = new SettingsUsageFragment
    fragment.setRootKey(screen.getKey)
    fragment.setSpawnLocation(getLocationOnScreen)
    push(fragment)
  }

  def showNoticeFragment {
    val fragment = if (noticeFragment == null) new NoticeFragment else noticeFragment
    fragment.setSpawnLocation(getLocationOnScreen)
    push(fragment)
  }
}
