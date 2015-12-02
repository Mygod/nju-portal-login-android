package tk.mygod.nju.portal.login

import java.net.{UnknownHostException, SocketTimeoutException, HttpURLConnection, URL}

import android.annotation.TargetApi
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager.NetworkCallback
import android.net.NetworkRequest.Builder
import android.net.{NetworkCapabilities, ConnectivityManager, NetworkInfo, Network}
import android.os.Binder
import android.util.Log
import tk.mygod.os.Build
import tk.mygod.util.CloseUtils._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

object NetworkMonitor {
  private val TAG = "NetworkMonitor"
  var instance: NetworkMonitor = _

  private implicit val networkOrdering: Ordering[Network] =
    Ordering.by[Network, Int](n => if (n == null) 0 else n.hashCode)
  private implicit val networkInfoOrdering: Ordering[NetworkInfo] =
    Ordering.by[NetworkInfo, (Int, Int)](n => if (n == null) (0, 0) else (n.getType, n.getSubtype))

  def cares(network: Int) =
    network > 6 && network != ConnectivityManager.TYPE_VPN || network == ConnectivityManager.TYPE_WIFI

  private var retryCount: Int = _
  private def retryDelay = {
    if (retryCount < 10) retryCount = retryCount + 1
    2000 + Random.nextInt(1000 << retryCount) // prevent overwhelming failing notifications
  }
  private def onNetworkAvailable(start: Long) {
    val now = System.currentTimeMillis
    retryCount = 0
    if (App.instance.pref.getBoolean("notifications.connection", true))
      App.instance.showToast(App.instance.getString(R.string.network_available).format(now - start))
  }

  //noinspection ScalaDeprecation
  @TargetApi(21)
  def reportNetworkConnectivity(network: Network, hasConnectivity: Boolean) = if (Build.version >= 23)
    App.instance.cm.reportNetworkConnectivity(network, hasConnectivity) else App.instance.cm.reportBadNetwork(network)

  //noinspection ScalaDeprecation
  def preferNetworkLegacy(n: NetworkInfo = null) {
    val network = if (n == null) listenerLegacy.preferredNetwork else n
    val preference = if (network == null) ConnectivityManager.TYPE_WIFI else network.getType
    App.instance.cm.setNetworkPreference(preference)
    if (App.DEBUG) Log.d(TAG, "Setting network preference: " + preference)
  }

  //noinspection ScalaDeprecation
  class NetworkListenerLegacy {
    import networkInfoOrdering._

    private val available = new mutable.TreeSet[NetworkInfo]
    private val testing = new mutable.TreeSet[NetworkInfo]
    var loginedNetwork: NetworkInfo = _

    private def shouldLogin(n: NetworkInfo) =
      instance != null && loginedNetwork == null && available.contains(n) && App.instance.autoConnectEnabled
    private def onLoginResult(n: NetworkInfo, result: Int, code: Int): Unit = if (result != 2)
      if (code == 1 || code == 6) loginedNetwork = n else {
        Thread.sleep(retryDelay)
        if (shouldLogin(n)) PortalManager.loginLegacy(n, onLoginResult(n, _, _))
      }

    def onAvailable(n: NetworkInfo) {
      available.add(n)
      if (!App.instance.autoConnectEnabled || App.instance.boundConnectionsAvailable > 1) return
      if (App.instance.skipConnect) Future(PortalManager.loginLegacy(n, onLoginResult(n, _, _)))
      else if (testing.synchronized(testing.add(n))) Future {
        if (App.DEBUG) Log.d(TAG, "Testing connection manually...")
        val url = new URL(App.http, "mygod.tk", "/generate_204")
        preferNetworkLegacy(n)
        try autoDisconnect(url.openConnection.asInstanceOf[HttpURLConnection]) { conn =>
          PortalManager.setup(conn, App.instance.connectTimeout)
          val start = System.currentTimeMillis
          conn.getInputStream
          val code = conn.getResponseCode
          if (code == 204 || code == 200 && conn.getContentLength == 0) onNetworkAvailable(start)
        } catch {
          case _: SocketTimeoutException | _: UnknownHostException =>
            if (shouldLogin(n)) {
              testing.synchronized(testing.remove(n))
              PortalManager.loginLegacy(n, onLoginResult(n, _, _))
              return
            }
          case e: Exception =>
            App.instance.showToast(e.getMessage)
            e.printStackTrace
        }
        testing.synchronized(testing.remove(n))
      }
    }

    def onLost(n: NetworkInfo) {
      available.remove(n)
      if (n.equiv(loginedNetwork)) loginedNetwork = null
    }

    def preferredNetwork = if (available.contains(loginedNetwork)) loginedNetwork else
      App.instance.cm.getAllNetworkInfo.collectFirst {
        case n: NetworkInfo if cares(n.getType) => n
      }.orNull
  }
  lazy val listenerLegacy = new NetworkListenerLegacy
}

final class NetworkMonitor extends Service {
  import NetworkMonitor._

  @TargetApi(21)
  class NetworkListener extends NetworkCallback {
    private val available = new mutable.TreeSet[Network]
    private val testing = new mutable.HashMap[Network, Long]
    var loginedNetwork: Network = _

    private def shouldLogin(n: Network) =
      available.contains(n) && loginedNetwork == null && App.instance.autoConnectEnabled
    private def waitForNetwork(n: Network, retry: Boolean = false) = if (App.instance.skipConnect) {
      if (shouldLogin(n)) PortalManager.login(n, onLoginResult(n, _, _))
    } else if (!testing.contains(n)) {
      testing.synchronized(testing(n) = System.currentTimeMillis)
      Thread.sleep(App.instance.connectTimeout)
      if (testing.synchronized(testing.remove(n)).nonEmpty && available.contains(n)) {
        if (retry) Thread.sleep(retryDelay)
        if (shouldLogin(n)) PortalManager.login(n, onLoginResult(n, _, _))
      }
    }
    private def onLoginResult(n: Network, result: Int, code: Int): Unit =
      if (result != 2) if (code == 1 || code == 6) loginedNetwork = n else waitForNetwork(n, true)
    private def onAvailable(n: Network, unsure: Boolean) = testing.get(n) match {
      case Some(start) =>
        onNetworkAvailable(start)
        testing.synchronized(testing.remove(n))
      case None => if (unsure) Future(waitForNetwork(n))
    }

    override def onAvailable(n: Network) {
      if (App.DEBUG) Log.d(TAG, "onAvailable (%s)".format(n))
      if (available.contains(n)) {
        if (Build.version < 23) onAvailable(n, false)                     // this is validated on 5.x
        else if (App.DEBUG) Log.w(TAG, "onAvailable called twice! WTF?")  // this is unexpected on 6.0+
      } else {
        available.add(n)
        if (Build.version < 23) onAvailable(n, true)
        else if (!App.instance.cm.getNetworkCapabilities(n).hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
          Future(waitForNetwork(n))
      }
    }
    override def onCapabilitiesChanged(n: Network, networkCapabilities: NetworkCapabilities) {
      if (App.DEBUG) Log.d(TAG, "onCapabilitiesChanged (%s): %s".format(n, networkCapabilities))
      if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL))
        if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) Future(waitForNetwork(n))
        else if (Build.version >= 23)
          if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) onAvailable(n, false)
          else Future(waitForNetwork(n))
    }
    override def onLost(n: Network) {
      if (App.DEBUG) Log.d(TAG, "onLost (%s)".format(n))
      available.remove(n)
      if (n.equals(loginedNetwork)) loginedNetwork = null
    }

    def preferredNetwork = if (available.contains(loginedNetwork)) loginedNetwork else available.collectFirst {
      case n: Network => n
    }.orNull
  }
  @TargetApi(21)
  var listener: NetworkListener = _

  class ServiceBinder extends Binder {
    def service = NetworkMonitor.this
  }
  def onBind(intent: Intent) = new ServiceBinder

  def initBoundConnections = if (listener == null && App.instance.boundConnectionsAvailable > 1) {
    listener = new NetworkListener
    App.instance.cm.requestNetwork(new Builder()
      .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
      .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
      .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build, listener)
  }

  override def onCreate {
    super.onCreate
    initBoundConnections
    instance = this
    if (App.DEBUG) Log.d(TAG, "Service created.")
  }

  override def onDestroy {
    super.onDestroy
    if (listener != null) {
      App.instance.cm.unregisterNetworkCallback(listener)
      listener = null
    }
    instance = null
    if (App.DEBUG) Log.d(TAG, "Service destroyed.")
  }
}
