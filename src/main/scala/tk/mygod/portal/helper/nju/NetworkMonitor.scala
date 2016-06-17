package tk.mygod.portal.helper.nju

import java.util.concurrent.atomic.AtomicBoolean

import android.annotation.TargetApi
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.net.ConnectivityManager.NetworkCallback
import android.net._
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import tk.mygod.app.ServicePlus
import tk.mygod.os.Build
import tk.mygod.util.Conversions._

import scala.collection.mutable
import scala.util.Random

object NetworkMonitor extends BroadcastReceiver {
  private final val TAG = "NetworkMonitor"
  private final val THREAD_TAG = TAG + "#reloginThread"
  private final val ACTION_LOGIN = "tk.mygod.portal.helper.nju.NetworkMonitor.ACTION_LOGIN"
  private final val ACTION_LOGIN_LEGACY = "tk.mygod.portal.helper.nju.NetworkMonitor.ACTION_LOGIN_LEGACY"
  private final val EXTRA_NETWORK_ID = "tk.mygod.portal.helper.nju.NetworkMonitor.EXTRA_NETWORK_ID"

  private lazy val networkCapabilities = {
    val result = classOf[NetworkCapabilities].getDeclaredField("mNetworkCapabilities")
    result.setAccessible(true)
    result
  }

  var instance: NetworkMonitor = _

  def cares(network: Int) =
    network > 6 && network != ConnectivityManager.TYPE_VPN || network == ConnectivityManager.TYPE_WIFI

  private var retryCount: Int = _
  private def retryDelay = {
    if (retryCount < 10) retryCount = retryCount + 1
    2000 + Random.nextInt(1000 << retryCount) // prevent overwhelming failing notifications
  }

  //noinspection ScalaDeprecation
  def preferNetworkLegacy(n: NetworkInfo = null) = {
    val network = if (n == null) listenerLegacy.preferredNetwork else n
    val preference = if (network == null) ConnectivityManager.TYPE_WIFI else network.getType
    app.cm.setNetworkPreference(preference)
    if (DEBUG) Log.v(TAG, "Setting network preference: " + preference)
    network
  }

  def makeLoginNotification = new NotificationCompat.Builder(app).setAutoCancel(true)
    .setColor(ContextCompat.getColor(app, R.color.material_primary_500))
    .setLights(ContextCompat.getColor(app, R.color.material_purple_a700), app.lightOnMs, app.lightOffMs)
    .setSmallIcon(R.drawable.ic_device_signal_wifi_not_connected).setGroup(ACTION_LOGIN)
    .setContentTitle(app.getString(R.string.network_available_sign_in))
    .setContentText(app.getString(R.string.app_name)).setShowWhen(false)

  //noinspection ScalaDeprecation
  class NetworkListenerLegacy {
    private val available = new mutable.LongMap[NetworkInfo]
    private val busy = new mutable.TreeSet[Long]
    var loginedNetwork: NetworkInfo = _

    private def serialize(n: NetworkInfo) = n.getType.toLong << 32 | n.getSubtype
    private def getNotificationId(id: Long) = (id ^ id >> 28 ^ id >> 56).toInt & 0xFFFFFFF | 0x10000000

    def doLogin(id: Long): Unit = available.get(id) match {
      case Some(n: NetworkInfo) => ThrowableFuture(if (busy.synchronized(busy.add(serialize(n)))) {
        doLogin(n)
        busy.synchronized(busy.remove(serialize(n)))
      })
      case _ =>
    }
    def doLogin(n: NetworkInfo) = while (instance != null && loginedNetwork == null &&
      available.contains(serialize(n)) && PortalManager.loginLegacy(n) == 1) Thread.sleep(retryDelay)

    def onLogin(n: NetworkInfo, code: Int) {
      loginedNetwork = n
      app.nm.cancel(getNotificationId(serialize(n)))
      if (instance != null && n != null) instance.reloginThread.synchronizedNotify(code)
    }

    def onAvailable(n: NetworkInfo) {
      available += (serialize(n), n)
      if (app.serviceStatus > 0 && app.boundConnectionsAvailable < 2 &&
        busy.synchronized(busy.add(serialize(n)))) ThrowableFuture {
        app.serviceStatus match {
          case 1 =>
            if (PortalManager.testConnectionLegacy(n)) {
              if (receiverRegistered.compareAndSet(false, true))
                app.registerReceiver(NetworkMonitor, new IntentFilter(ACTION_LOGIN_LEGACY))
              val id = serialize(n)
              val builder = makeLoginNotification
                .setContentIntent(app.pendingBroadcast(new Intent(ACTION_LOGIN_LEGACY).putExtra(EXTRA_NETWORK_ID, id)))
              app.nm.notify(getNotificationId(id), PortalManager.queryOnlineLegacy(n).headOption match {
                case None => builder.build
                case Some(entry) => entry.makeNotification(builder)
              })
              NoticeManager.pushUnreadNotices
            }
          case 2 =>
            doLogin(n)
            NoticeManager.pushUnreadNotices
          case 3 => if (PortalManager.testConnectionLegacy(n)) {
            doLogin(n)
            NoticeManager.pushUnreadNotices
          }
          case 4 => if (PortalManager.testConnectionLegacy(n)) {
            PortalManager.queryOnlineLegacy(n).headOption match {
              case None => PortalManager.queryOnlineLegacy(n).headOption match {
                case None => doLogin(n) // double check
                case Some(entry) => pushNotification(n, entry)
              }
              case Some(entry) => pushNotification(n, entry)
            }
            NoticeManager.pushUnreadNotices
          }
          case _ =>
        }
        busy.synchronized(busy.remove(serialize(n)))
      }
    }
    private def pushNotification(n: NetworkInfo, entry: PortalManager.OnlineEntry) {
      if (receiverRegistered.compareAndSet(false, true))
        app.registerReceiver(NetworkMonitor, new IntentFilter(ACTION_LOGIN_LEGACY))
      val id = n.hashCode
      app.nm.notify(getNotificationId(id), entry.makeNotification(makeLoginNotification.setContentIntent(
        app.pendingBroadcast(new Intent(ACTION_LOGIN_LEGACY).putExtra(EXTRA_NETWORK_ID, id)))))
    }

    def onLost(n: NetworkInfo) {
      val id = serialize(n)
      available.remove(id)
      app.nm.cancel(getNotificationId(id))
      if (loginedNetwork != null && id == serialize(loginedNetwork)) {
        loginedNetwork = null
        if (instance != null) instance.reloginThread.synchronizedNotify()
      }
    }

    def preferredNetwork = if (loginedNetwork != null && available.contains(serialize(loginedNetwork))) loginedNetwork
      else app.cm.getAllNetworkInfo.collectFirst {
        case n: NetworkInfo if cares(n.getType) => n
      }.orNull
  }
  lazy val listenerLegacy = new NetworkListenerLegacy

  /**
    * Get login status.
    *
    * @return 0-2: Logged out, logged in, logged in (legacy).
    */
  def loginStatus =
    if (instance != null && instance.loggedIn) 1 else if (listenerLegacy.loginedNetwork != null) 2 else 0

  private val receiverRegistered = new AtomicBoolean
  def onReceive(context: Context, intent: Intent) = listenerLegacy.doLogin(intent.getLongExtra(EXTRA_NETWORK_ID, -1))
}

final class NetworkMonitor extends ServicePlus {
  import NetworkMonitor._

  private def loggedIn = listener != null && app.boundConnectionsAvailable > 1 && listener.loginedNetwork != null

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
        app.handler.post(startForeground(-1, notificationBuilder.build))
        if (DEBUG) Log.v(THREAD_TAG, "Waiting %dms...".format(delay))
        synchronized(wait(delay))
        if (DEBUG) Log.v(THREAD_TAG, "Timed out or notified.")
        loginStatus match {
          case 0 => inactive
          case 1 =>
            val network = listener.loginedNetwork
            PortalManager.logout
            Thread.sleep(2000)
            PortalManager.login(network)
          case 2 =>
            val network = listenerLegacy.loginedNetwork
            PortalManager.logout
            Thread.sleep(2000)
            PortalManager.loginLegacy(network)
        }
    } catch {
      case ignore: InterruptedException => if (DEBUG) Log.v(THREAD_TAG, "Interrupted.")
    }
  }
  val reloginThread = new ReloginThread

  @TargetApi(21)
  class NetworkListener extends NetworkCallback {
    private val available = new mutable.HashMap[Int, (Network, Long)]
    private val busy = new mutable.HashSet[Int]
    var loginedNetwork: Network = _

    private def getNotificationId(id: Int) = (id ^ id >> 28) & 0xFFFFFFF | 0x20000000

    def doLogin(id: Int): Unit = available.get(id) match {
      case Some((n, _)) => ThrowableFuture(if (busy.synchronized(busy.add(n.hashCode))) {
        doLogin(n)
        busy.synchronized(busy.remove(n.hashCode))
      })
      case _ =>
    }
    private def doLogin(n: Network) = while (available.contains(n.hashCode) && loginedNetwork == null &&
      busy.synchronized(busy.contains(n.hashCode)) && app.serviceStatus > 0 && PortalManager.login(n) == 1)
      Thread.sleep(retryDelay)

    private def testConnection(n: Network) = if (busy.synchronized(busy.add(n.hashCode))) ThrowableFuture {
      app.serviceStatus match {
        case 1 =>
          if (PortalManager.testConnection(n)) {
            if (receiverRegistered.compareAndSet(false, true))
              app.registerReceiver(loginReceiver, new IntentFilter(ACTION_LOGIN))
            val id = n.hashCode
            val builder = makeLoginNotification
              .setContentIntent(app.pendingBroadcast(new Intent(ACTION_LOGIN).putExtra(EXTRA_NETWORK_ID, id)))
            app.nm.notify(getNotificationId(id), PortalManager.queryOnline(n).headOption match {
              case None => builder.build
              case Some(entry) => entry.makeNotification(builder)
            })
            NoticeManager.pushUnreadNotices
          }
        case 2 =>
          doLogin(n)
          NoticeManager.pushUnreadNotices
        case 3 => if (PortalManager.testConnection(n)) {
          doLogin(n)
          NoticeManager.pushUnreadNotices
        }
        case 4 => if (PortalManager.testConnection(n)) {
          PortalManager.queryOnline(n).headOption match {
            case None => PortalManager.queryOnline(n).headOption match {
              case None => doLogin(n) // double check
              case Some(entry) => pushNotification(n, entry)
            }
            case Some(entry) => pushNotification(n, entry)
          }
          NoticeManager.pushUnreadNotices
        }
        case _ =>
      }
      busy.synchronized(busy.remove(n.hashCode))
    }
    private def pushNotification(n: Network, entry: PortalManager.OnlineEntry) {
      if (receiverRegistered.compareAndSet(false, true))
        app.registerReceiver(loginReceiver, new IntentFilter(ACTION_LOGIN))
      val id = n.hashCode
      app.nm.notify(getNotificationId(id), entry.makeNotification(makeLoginNotification
        .setContentIntent(app.pendingBroadcast(new Intent(ACTION_LOGIN).putExtra(EXTRA_NETWORK_ID, id)))))
    }
    private def getCapabilities(capabilities: NetworkCapabilities) =
      networkCapabilities.get(capabilities).asInstanceOf[Long]

    def onLogin(n: Network, code: Int) {
      loginedNetwork = n
      app.nm.cancel(getNotificationId(n.hashCode))
      reloginThread.synchronizedNotify(code)
    }

    override def onAvailable(n: Network) {
      val capabilities = app.cm.getNetworkCapabilities(n)
      if (DEBUG) Log.d(TAG, "onAvailable (%s): %s".format(n, capabilities))
      if (available.contains(n.hashCode)) {
        if (Build.version < 23) busy.synchronized(busy.remove(n.hashCode))  // validated on 5.x
      } else {
        available(n.hashCode) = (n, getCapabilities(capabilities))
        if (Build.version < 23 || !(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
          capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL))) testConnection(n)
      }
    }
    override def onCapabilitiesChanged(n: Network, capabilities: NetworkCapabilities) {
      if (DEBUG) Log.d(TAG, "onCapabilitiesChanged (%s): %s".format(n, capabilities))
      val newCapabilities = getCapabilities(capabilities)
      if (available(n.hashCode)._2 == newCapabilities) return
      available(n.hashCode) = (n, newCapabilities)
      if (Build.version >= 23 && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        busy.synchronized(busy.remove(n.hashCode)) else {
        loginedNetwork = null
        if (Build.version < 23 || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL))
          testConnection(n)
      }
    }
    override def onLost(n: Network) {
      if (DEBUG) Log.d(TAG, "onLost (%s)".format(n))
      val id = n.hashCode
      available.remove(id)
      app.nm.cancel(getNotificationId(id))
      if (n.equals(loginedNetwork)) {
        loginedNetwork = null
        reloginThread.synchronizedNotify()
      }
    }

    def preferredNetwork = if (loginedNetwork != null && available.contains(loginedNetwork.hashCode)) loginedNetwork
      else available.collectFirst {
        case (_, (n, _)) => n
      }.orNull
  }
  @TargetApi(21)
  var listener: NetworkListener = _

  def initBoundConnections = if (listener == null && app.boundConnectionsAvailable > 1) {
    listener = new NetworkListener
    app.cm.requestNetwork(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
      .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
      .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build, listener)
  }

  private val receiverRegistered = new AtomicBoolean
  private lazy val loginReceiver: BroadcastReceiver =
    (_, intent) => listener.doLogin(intent.getIntExtra(EXTRA_NETWORK_ID, -1))

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
    if (receiverRegistered.compareAndSet(true, false)) app.unregisterReceiver(loginReceiver)
    instance = null
    if (DEBUG) Log.d(TAG, "Service destroyed.")
  }
}
