package tk.mygod.portal.helper.nju

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content._
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.support.v7.app.AlertDialog
import android.text.TextUtils
import tk.mygod.app.ToolbarActivity

object MainActivity {
  private val ASKED_BOUND_CONNECTION = "misc.useBoundConnections.asked"
}

final class MainActivity extends ToolbarActivity with OnSharedPreferenceChangeListener {
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

  private def manageWriteSettings(dialog: DialogInterface = null, which: Int = 0) = startActivity(
    new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).setData(Uri.parse("package:" + getPackageName)))
  def testBoundConnections(requested: Boolean = false) = app.boundConnectionsAvailable match {
    case 1 => if (requested) {
      manageWriteSettings()
      true
    } else if (!app.pref.getBoolean(ASKED_BOUND_CONNECTION, false)) {
      new AlertDialog.Builder(this).setTitle(R.string.bound_connections_title)
        .setPositiveButton(android.R.string.yes, manageWriteSettings: DialogInterface.OnClickListener)
        .setMessage(R.string.bound_connections_message).setNegativeButton(android.R.string.no, null).create.show
      app.editor.putBoolean(ASKED_BOUND_CONNECTION, true)
      true
    } else false
    case 2 => if (requested) {
      manageWriteSettings()
      true
    } else false
    case _ => false
  }

  override def onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) = key match {
    case AUTO_LOGIN_ENABLED =>
      val value = app.autoLoginEnabled
      app.editor.putBoolean(AUTO_LOGIN_ENABLED, value)
      if (value) {
        getPackageManager.setComponentEnabledSetting(new ComponentName(this, classOf[NetworkMonitorListener]),
          PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        startNetworkMonitor
      } else {
        getPackageManager.setComponentEnabledSetting(new ComponentName(this, classOf[NetworkMonitorListener]),
          PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        stopService(serviceIntent)
      }
    case RELOGIN_DELAY => if (NetworkMonitor.instance != null) NetworkMonitor.instance.reloginThread.interrupt
    case _ => // ignore
  }

  def startNetworkMonitor = if (app.autoLoginEnabled) {
    startService(serviceIntent)
  }
}
