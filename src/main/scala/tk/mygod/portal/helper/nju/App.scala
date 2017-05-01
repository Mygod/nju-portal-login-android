package tk.mygod.portal.helper.nju

import android.accounts.AccountManager
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.{ComponentName, Context, SharedPreferences}
import android.net.wifi.WifiManager
import android.net.{ConnectivityManager, Network}
import android.os.Handler
import android.provider.Settings
import android.widget.Toast
import be.mygod.app.ApplicationPlus
import be.mygod.os.Build
import com.j256.ormlite.logger.LocalLog

import scala.reflect._

class App extends ApplicationPlus {
  val handler = new Handler

  override def onCreate {
    app = this
    if (!BuildConfig.DEBUG) System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "WARNING")
    super.onCreate
    systemService[AccountManager].addAccountExplicitly(NoticeManager.account, null, null)
    NoticeManager.updatePeriodicSync()
  }

  def boundConnectionsAvailable: Boolean = Build.version < 23 || Settings.System.canWrite(this)

  lazy val cm: ConnectivityManager = systemService[ConnectivityManager]
  lazy val nm: NotificationManager = systemService[NotificationManager]
  lazy val pm: PackageManager = getPackageManager
  lazy val wm: WifiManager = systemService[WifiManager]
  lazy val pref: SharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
  lazy val editor: SharedPreferences.Editor = pref.edit

  def serviceStatus: Int = pref.getString(SERVICE_STATUS, "3").toInt

  def showToast(msg: String): Boolean = handler.post(() => makeToast(msg, Toast.LENGTH_SHORT).show())

  def reportNetworkConnectivity(network: Network, hasConnectivity: Boolean): Unit = if (Build.version >= 23)
    cm.reportNetworkConnectivity(network, hasConnectivity) else cm.reportBadNetwork(network)

  private def readSystemInteger(key: String) =
    getResources.getInteger(Resources.getSystem.getIdentifier(key, "integer", "android"))
  lazy val lightOnMs: Int = readSystemInteger("config_defaultNotificationLedOn")
  lazy val lightOffMs: Int = readSystemInteger("config_defaultNotificationLedOff")

  def getEnabled[T: ClassTag]: Boolean = PackageManager.COMPONENT_ENABLED_STATE_ENABLED ==
    pm.getComponentEnabledSetting(new ComponentName(this, classTag[T].runtimeClass))
  def setEnabled[T: ClassTag](enabled: Boolean): Unit = pm.setComponentEnabledSetting(
    new ComponentName(this, classTag[T].runtimeClass),
    if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
    PackageManager.DONT_KILL_APP)

  def getIpLookup(ip: CharSequence): String = "https://ipinfo.io/" + ip
  def getMacLookup(mac: CharSequence): String = "http://www.coffer.com/mac_find/?string=" + mac
}
