package tk.mygod.portal.helper.nju

import android.accounts.AccountManager
import android.annotation.{SuppressLint, TargetApi}
import android.app.{Application, NotificationManager}
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.{ComponentName, Context}
import android.net.{ConnectivityManager, Network}
import android.net.wifi.WifiManager
import android.os.Handler
import android.provider.Settings
import android.widget.Toast
import be.mygod.content.ContextPlus
import be.mygod.os.Build
import com.j256.ormlite.logger.LocalLog

import scala.reflect._

class App extends Application with ContextPlus {
  val handler = new Handler

  override def onCreate {
    app = this
    if (!BuildConfig.DEBUG) System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "WARNING")
    super.onCreate
    systemService[AccountManager].addAccountExplicitly(NoticeManager.account, null, null)
    NoticeManager.updatePeriodicSync
  }

  /**
    * 0-3: Not available, permission missing, yes (revoke available), yes.
    */
  @SuppressLint(Array("NewApi"))
  def boundConnectionsAvailable = if (Build.version >= 21) if (Build.version < 23) 3
    else if (Settings.System.canWrite(this)) 2 else 1 else 0

  lazy val cm = systemService[ConnectivityManager]
  lazy val nm = systemService[NotificationManager]
  lazy val pm = getPackageManager
  lazy val wm = systemService[WifiManager]
  lazy val pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
  lazy val editor = pref.edit

  def serviceStatus = pref.getString(SERVICE_STATUS, "3").toInt

  def showToast(msg: String) = handler.post(() => makeToast(msg, Toast.LENGTH_SHORT).show)

  //noinspection ScalaDeprecation
  @TargetApi(21)
  def reportNetworkConnectivity(network: Network, hasConnectivity: Boolean) = if (Build.version >= 23)
    cm.reportNetworkConnectivity(network, hasConnectivity) else cm.reportBadNetwork(network)

  private def readSystemInteger(key: String) =
    getResources.getInteger(Resources.getSystem.getIdentifier(key, "integer", "android"))
  lazy val lightOnMs = readSystemInteger("config_defaultNotificationLedOn")
  lazy val lightOffMs = readSystemInteger("config_defaultNotificationLedOff")

  def getEnabled[T: ClassTag] = PackageManager.COMPONENT_ENABLED_STATE_ENABLED ==
    pm.getComponentEnabledSetting(new ComponentName(this, classTag[T].runtimeClass))
  def setEnabled[T: ClassTag](enabled: Boolean) = pm.setComponentEnabledSetting(
    new ComponentName(this, classTag[T].runtimeClass),
    if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
    PackageManager.DONT_KILL_APP)

  def getIpLookup(ip: CharSequence) = "https://ipinfo.io/" + ip
  def getMacLookup(mac: CharSequence) = "http://www.coffer.com/mac_find/?string=" + mac
}
