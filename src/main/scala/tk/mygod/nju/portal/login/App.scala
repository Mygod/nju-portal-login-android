package tk.mygod.nju.portal.login

import android.app.Application
import android.content.pm.PackageManager
import android.content.{Intent, ComponentName, Context}
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.{Build, Handler}
import android.provider.Settings
import android.widget.Toast
import eu.chainfire.libsuperuser.Shell.SU

object App {
  var instance: App = _
  var handler: Handler = _
  lazy val isRoot = SU.available
  val DEBUG = true

  val prefName = "pref"
  val autoConnectEnabledKey = "autoConnect.enabled"
}

class App extends Application {
  import App._

  override def onCreate {
    instance = this
    super.onCreate
    handler = new Handler
  }

  lazy val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]
  private lazy val wifiManager = getSystemService(Context.WIFI_SERVICE).asInstanceOf[WifiManager]
  private lazy val networkMonitorPermissionGranted = checkCallingOrSelfPermission(
    "android.permission.ACCESS_NETWORK_CONDITIONS") == PackageManager.PERMISSION_GRANTED
  /**
    * 0-4: Not available, no root, need moving, wifi scanning not available, yes.
    */
  def systemNetworkMonitorAvailable = if (Build.VERSION.SDK_INT >= 21) if (networkMonitorPermissionGranted)
    if (wifiManager.isScanAlwaysAvailable) 4 else 3 else if (App.isRoot) 2 else 1 else 0
  /**
    * 0-3: Not available, permission missing, yes (revoke available), yes.
    */
  def boundConnectionsAvailable = {
    val result = if (Build.VERSION.SDK_INT >= 21) if (Build.VERSION.SDK_INT < 23) 3
      else if (Settings.System.canWrite(this)) 2 else 1 else 0
    if (result > 1) PortalManager.startListenNetwork
    result
  }

  lazy val pref = getSharedPreferences(prefName, Context.MODE_PRIVATE)
  lazy val editor = pref.edit

  def autoConnectEnabled = pref.getBoolean(autoConnectEnabledKey, true)
  def autoConnectEnabled(value: Boolean) = {
    editor.putBoolean(autoConnectEnabledKey, value)
    if (value)
      getPackageManager.setComponentEnabledSetting(new ComponentName(this, classOf[NetworkConditionsReceiver]),
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    else {
      getPackageManager.setComponentEnabledSetting(new ComponentName(this, classOf[NetworkConditionsReceiver]),
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
      stopService(new Intent(this, classOf[PortalManager]))
    }
  }

  def connectTimeout = pref.getInt("speed.connectTimeout", 4000)
  def loginTimeout = pref.getInt("speed.loginTimeout", 4000)

  def showToast(msg: String) = handler.post(() => Toast.makeText(this, msg, Toast.LENGTH_SHORT).show)
}
