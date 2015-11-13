package tk.mygod.nju.portal.login

import android.app.Application
import android.content.{ComponentName, Context}
import android.content.pm.PackageManager
import android.net.ConnectivityManager.NetworkCallback
import android.net._
import android.os.{Handler, Build}
import android.provider.Settings
import android.util.Log
import eu.chainfire.libsuperuser.Shell.SU

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object App {
  var instance: App = _

  val DEBUG = true
  private val TAG = "App"
  val prefName = "pref"
  val autoConnectEnabledKey = "autoConnect.enabled"

  val http = "http"

  val systemId = "NJUPortalLogin"
  val systemDir = "/system/priv-app/" + systemId
  val systemPath = systemDir + "/" + systemId + ".apk"

  lazy val isRoot = SU.available

  private var handler: Handler = _
  var testingNetwork: NetworkInfo = _
  lazy val connectTimeout = (() => Future(PortalManager.login(true))): Runnable
  def clearTimeout = {
    handler.removeCallbacks(connectTimeout)
    if (App.DEBUG) Log.d(TAG, "Previous timeout has been cleared.")
  }
  def setTimeout(network: NetworkInfo = null) = {
    clearTimeout  // new connection coming, don't care about previous ones any more
    if (network != null) testingNetwork = network
    handler.postDelayed(connectTimeout, 4000) // TODO: custom value
    if (App.DEBUG) Log.d(TAG, "A new timeout has been set.")
  }

  private def networkTransportType(network: NetworkInfo) = network.getType match {
    case ConnectivityManager.TYPE_WIFI | ConnectivityManager.TYPE_WIMAX => NetworkCapabilities.TRANSPORT_WIFI
    case ConnectivityManager.TYPE_BLUETOOTH => NetworkCapabilities.TRANSPORT_BLUETOOTH
    case ConnectivityManager.TYPE_ETHERNET => NetworkCapabilities.TRANSPORT_ETHERNET
    case ConnectivityManager.TYPE_VPN => NetworkCapabilities.TRANSPORT_VPN
    case _ => NetworkCapabilities.TRANSPORT_CELLULAR  // should probably never hit
  }

  //noinspection ScalaDeprecation
  def bindNetwork[T](network: NetworkInfo, callback: Network => T) = if (network == null) {
    App.instance.connectivityManager.setNetworkPreference(ConnectivityManager.TYPE_WIFI)  // a random guess
    callback(null)
  } else network.synchronized {
    if (App.instance.bindedConnectionsAvailable > 1) {
      if (App.DEBUG) Log.d(TAG, "Binding to network with type: " + networkTransportType(network))
      App.instance.connectivityManager.requestNetwork(
        new NetworkRequest.Builder().addTransportType(networkTransportType(network)).build, new NetworkCallback {
          override def onAvailable(network: Network) = callback(network)
        })
    } else {
      App.instance.connectivityManager.setNetworkPreference(network.getType)
      if (App.DEBUG) Log.d(TAG, "Setting network preference: " + network.getType)
      callback(null)
    }
  }
}

class App extends Application {
  import App._

  override def onCreate {
    instance = this
    super.onCreate
    handler = new Handler
  }

  /**
    * 0-3: Not available, no root, need moving, yes.
    */
  lazy val systemNetworkMonitorAvailable = if (Build.VERSION.SDK_INT >= 21) if (checkCallingOrSelfPermission(
    "android.permission.ACCESS_NETWORK_CONDITIONS") == PackageManager.PERMISSION_GRANTED) 3
  else if (App.isRoot) 2 else 1 else 0
  /**
    * 0-3: Not available, permission missing, yes (revoke available), yes.
    */
  def bindedConnectionsAvailable = if (Build.VERSION.SDK_INT >= 21)
    if (Build.VERSION.SDK_INT < 23) 3 else if (Settings.System.canWrite(this)) 2 else 1 else 0

  lazy val pref = getSharedPreferences(prefName, Context.MODE_PRIVATE)
  lazy val editor = pref.edit
  lazy val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]

  def autoConnectEnabled = pref.getBoolean(autoConnectEnabledKey, true)
  def autoConnectEnabled(value: Boolean) = {
    editor.putBoolean(autoConnectEnabledKey, value)
    getPackageManager.setComponentEnabledSetting(new ComponentName(this, classOf[NetworkConditionsReceiver]),
      if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP)
  }
}
