package tk.mygod.nju.portal.login

import java.net.{HttpURLConnection, SocketTimeoutException, URL, UnknownHostException}

import android.annotation.TargetApi
import android.net.ConnectivityManager.NetworkCallback
import android.net._
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import tk.mygod.app.ServicePlus
import tk.mygod.os.Build
import tk.mygod.util.CloseUtils._
import tk.mygod.util.Conversions._

import scala.collection.mutable
import scala.util.Random

object NetworkMonitor {
  private final val TAG = "NetworkMonitor"
  private final val THREAD_TAG = TAG + "#reloginThread"
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
    if (app.pref.getBoolean("notifications.connection", true))
      app.showToast(app.getString(R.string.network_available).format(now - start))
  }

  //noinspection ScalaDeprecation
  def preferNetworkLegacy(n: NetworkInfo = null) = {
    val network = if (n == null) listenerLegacy.preferredNetwork else n
    val preference = if (network == null) ConnectivityManager.TYPE_WIFI else network.getType
    app.cm.setNetworkPreference(preference)
    if (DEBUG) Log.v(TAG, "Setting network preference: " + preference)
    network
  }

  //noinspection ScalaDeprecation
  class NetworkListenerLegacy {
    import networkInfoOrdering._

    private val available = new mutable.TreeSet[NetworkInfo]
    private val testing = new mutable.TreeSet[NetworkInfo]
    var loginedNetwork: NetworkInfo = _

    def login(n: NetworkInfo) = while (instance != null && loginedNetwork == null && available.contains(n) &&
      app.autoConnectEnabled && PortalManager.loginLegacy(n) == 1) Thread.sleep(retryDelay)
    def onLogin(n: NetworkInfo, code: Int) {
      loginedNetwork = n
      if (instance != null && n != null) instance.reloginThread.synchronizedNotify(code)
    }

    def onAvailable(n: NetworkInfo) {
      available.add(n)
      if (!app.autoConnectEnabled || app.boundConnectionsAvailable > 1) return
      if (app.skipConnect) ThrowableFuture(login(n))
      else if (testing.synchronized(testing.add(n))) ThrowableFuture {
        if (DEBUG) Log.d(TAG, "Testing connection manually...")
        val url = new URL(HTTP, "mygod.tk", "/generate_204")
        preferNetworkLegacy(n)
        try autoDisconnect(url.openConnection.asInstanceOf[HttpURLConnection]) { conn =>
          PortalManager.setup(conn, app.connectTimeout)
          val start = System.currentTimeMillis
          conn.getInputStream
          val code = conn.getResponseCode
          if (code == 204 || code == 200 && conn.getContentLength == 0) onNetworkAvailable(start)
          testing.synchronized(testing.remove(n))
        } catch {
          case _: SocketTimeoutException | _: UnknownHostException =>
            testing.synchronized(testing.remove(n))
            login(n)
          case e: Exception =>
            testing.synchronized(testing.remove(n))
            app.showToast(e.getMessage)
            e.printStackTrace
        }
      }
    }

    def onLost(n: NetworkInfo) {
      available.remove(n)
      if (n.equiv(loginedNetwork)) {
        loginedNetwork = null
        if (instance != null) instance.reloginThread.synchronizedNotify()
      }
    }

    def preferredNetwork = if (available.contains(loginedNetwork)) loginedNetwork else
      app.cm.getAllNetworkInfo.collectFirst {
        case n: NetworkInfo if cares(n.getType) => n
      }.orNull
  }
  lazy val listenerLegacy = new NetworkListenerLegacy
}

final class NetworkMonitor extends ServicePlus {
  import NetworkMonitor._

  /**
    * Get login status.
    *
    * @return 0-2: Logged out, logged in, logged in (legacy).
    */
  private def loginStatus =
    if (listener != null && app.boundConnectionsAvailable > 1 && listener.loginedNetwork != null) 1
    else if (listenerLegacy.loginedNetwork != null) 2 else 0

  private lazy val notificationBuilder = new NotificationCompat.Builder(this)
    .setColor(ContextCompat.getColor(this, R.color.material_primary_500)).setContentIntent(pendingIntent[MainActivity])
    .setSmallIcon(R.drawable.ic_av_timer)
  class ReloginThread extends Thread {
    @volatile private var running = true
    @volatile private var isInactive = true

    def stopRunning {
      running = false
      interrupt
    }
    def synchronizedNotify(code: Int = 1) = code match {
      case 1 => synchronized(notify)  // login / logout / connection lost
      case 6 => if (isInactive && PortalManager.username == PortalManager.currentUsername) synchronized(notify)
      case _ =>                       // ignore
    }

    private def inactive {
      app.handler.post(stopForeground(true))
      isInactive = true
      synchronized(wait)
    }
    override def run = while (running) try if (loginStatus == 0) inactive else app.reloginDelay match {
      case 0 => inactive
      case delay =>
        isInactive = false
        notificationBuilder.setWhen(System.currentTimeMillis)
          .setContentTitle(getString(R.string.auto_relogin_active, delay: Integer))
          .setPriority(if (app.pref.getBoolean("notifications.reloginIcon", true))
            NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_MIN)
        app.handler.post(startForeground(1, notificationBuilder.build))
        if (DEBUG) Log.v(THREAD_TAG, "Waiting %dms...".format(delay))
        synchronized(wait(delay))
        if (DEBUG) Log.v(THREAD_TAG, "Timed out or notified.")
        loginStatus match {
          case 0 => inactive
          case 1 =>
            val network = listener.loginedNetwork
            PortalManager.logout
            Thread.sleep(2000)
            listener.login(network)
          case 2 =>
            val network = listenerLegacy.loginedNetwork
            PortalManager.logout
            Thread.sleep(2000)
            listenerLegacy.login(network)
        }
    } catch {
      case ignore: InterruptedException => if (DEBUG) Log.v(THREAD_TAG, "Interrupted.")
    }
  }
  val reloginThread = new ReloginThread

  @TargetApi(21)
  class NetworkListener extends NetworkCallback {
    private val available = new mutable.TreeSet[Network]
    private val testing = new mutable.HashMap[Network, Long]
    var loginedNetwork: Network = _

    private def waitForNetwork(n: Network, retry: Boolean = false) =
      if (app.skipConnect) login(n) else if (!testing.contains(n)) {
      testing.synchronized(testing(n) = System.currentTimeMillis)
      Thread.sleep(app.connectTimeout)
      if (testing.synchronized(testing.remove(n)).nonEmpty && available.contains(n)) {
        if (retry) Thread.sleep(retryDelay)
        login(n)
      }
    }
    private def onAvailable(n: Network, unsure: Boolean) = testing.get(n) match {
      case Some(start) =>
        onNetworkAvailable(start)
        testing.synchronized(testing.remove(n))
      case None => if (unsure) ThrowableFuture(waitForNetwork(n))
    }

    def login(n: Network): Unit = if (available.contains(n) && loginedNetwork == null && app.autoConnectEnabled &&
      PortalManager.login(n) == 1) waitForNetwork(n, true)
    def onLogin(n: Network, code: Int) {
      loginedNetwork = n
      reloginThread.synchronizedNotify(code)
    }

    override def onAvailable(n: Network) {
      if (DEBUG) Log.d(TAG, "onAvailable (%s)".format(n))
      if (available.contains(n)) {
        if (Build.version < 23) onAvailable(n, false)     // this is validated on 5.x
        else Log.e(TAG, "onAvailable called twice! WTF?") // this is unexpected on 6.0+
      } else {
        available.add(n)
        if (Build.version < 23) onAvailable(n, true)
        else if (!app.cm.getNetworkCapabilities(n).hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
          ThrowableFuture(waitForNetwork(n))
      }
    }
    override def onCapabilitiesChanged(n: Network, networkCapabilities: NetworkCapabilities) {
      if (DEBUG) Log.d(TAG, "onCapabilitiesChanged (%s): %s".format(n, networkCapabilities))
      if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL))
        if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
          ThrowableFuture(waitForNetwork(n))
        else if (Build.version >= 23)
          if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) onAvailable(n, false)
          else ThrowableFuture(waitForNetwork(n))
    }
    override def onLost(n: Network) {
      if (DEBUG) Log.d(TAG, "onLost (%s)".format(n))
      available.remove(n)
      if (n.equals(loginedNetwork)) {
        loginedNetwork = null
        reloginThread.synchronizedNotify()
      }
    }

    def preferredNetwork = if (available.contains(loginedNetwork)) loginedNetwork else available.collectFirst {
      case n: Network => n
    }.orNull
  }
  @TargetApi(21)
  var listener: NetworkListener = _

  def initBoundConnections = if (listener == null && app.boundConnectionsAvailable > 1) {
    listener = new NetworkListener
    app.cm.requestNetwork(new NetworkRequest.Builder()
      .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
      .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
      .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build, listener)
  }

  override def onCreate {
    super.onCreate
    initBoundConnections
    reloginThread.start
    instance = this
    if (DEBUG) Log.d(TAG, "Service created.")
  }

  override def onDestroy {
    super.onDestroy
    if (listener != null) {
      app.cm.unregisterNetworkCallback(listener)
      listener = null
    }
    reloginThread.stopRunning
    instance = null
    if (DEBUG) Log.d(TAG, "Service destroyed.")
  }
}
