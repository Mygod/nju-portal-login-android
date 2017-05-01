package tk.mygod.portal.helper.nju

import java.util.concurrent.atomic.AtomicBoolean

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content._
import android.net.ConnectivityManager.NetworkCallback
import android.net._
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationCompat.Builder
import android.support.v4.content.{ContextCompat, LocalBroadcastManager}
import android.util.Log
import be.mygod.app.ServicePlus
import be.mygod.os.Build
import tk.mygod.portal.helper.nju.preference.MacAddressPreference
import tk.mygod.portal.helper.nju.util.RetryCounter

import scala.collection.mutable

object NetworkMonitor {
  private final val TAG = "NetworkMonitor"
  private final val ACTION_LOGIN = "tk.mygod.portal.helper.nju.NetworkMonitor.ACTION_LOGIN"
  private final val EXTRA_NETWORK_ID = "tk.mygod.portal.helper.nju.NetworkMonitor.EXTRA_NETWORK_ID"
  final val LOCAL_MAC = "misc.localMac"

  def localMacs: Set[String] = app.pref.getString(LOCAL_MAC, MacAddressPreference.default()).split("\n").map(_.toLowerCase)
    .filter(_ != null).toSet
  def ignoreSystemValidation: Boolean = app.pref.getBoolean("misc.ignoreSystemValidation", false)

  private lazy val networkCapabilities = {
    val result = classOf[NetworkCapabilities].getDeclaredField("mNetworkCapabilities")
    result.setAccessible(true)
    result
  }

  var instance: NetworkMonitor = _

  def cares(network: Int): Boolean =
    network > 6 && network != ConnectivityManager.TYPE_VPN || network == ConnectivityManager.TYPE_WIFI

  def loginNotificationBuilder: Builder = new NotificationCompat.Builder(app)
    .setColor(ContextCompat.getColor(app, R.color.material_primary_500))
    .setSmallIcon(R.drawable.ic_device_signal_wifi_statusbar_not_connected).setGroup(ACTION_LOGIN)
    .setContentTitle(app.getString(R.string.network_available_sign_in))
    .setShowWhen(false).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

  def cancelLoginNotification(id: Int): Boolean = {
    app.nm.cancel(id)
    LocalBroadcastManager.getInstance(app).sendBroadcast(new Intent(OnlineEntryActivity.ACTION_CANCEL)
      .putExtra(OnlineEntryActivity.EXTRA_NOTIFICATION_ID, id))
  }

  def loggedIn: Boolean = instance != null && instance.loggedIn
}

final class NetworkMonitor extends ServicePlus with OnSharedPreferenceChangeListener {
  import NetworkMonitor._

  private def loggedIn = listener != null && app.boundConnectionsAvailable && listener.loginedNetwork != null

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
    private def doLogin(n: Network) {
      val counter = new RetryCounter()
      while (available.contains(n.hashCode) && loginedNetwork == null && busy.synchronized(busy.contains(n.hashCode)) &&
        app.serviceStatus > 0) PortalManager.login(n) match {
        case 1 => counter.retry()
        case -1 => counter.reset()
        case _ =>
      }
    }

    private def doTestConnection(n: Network): Boolean = {
      val counter = new RetryCounter()
      while (available.contains(n.hashCode) && loginedNetwork == null && busy.synchronized(busy.contains(n.hashCode)) &&
        app.serviceStatus > 0) PortalManager.testConnection(n) match {
        case 0 =>
          app.reportNetworkConnectivity(n, hasConnectivity = true)
          NoticeManager.pushUnreadNotices // push notices only
          return false
        case 2 => return true
        case _ => counter.retry()
      }
      false
    }

    private def testConnection(n: Network) = if (busy.synchronized(busy.add(n.hashCode))) ThrowableFuture {
      try app.serviceStatus match {
        case 1 =>
          if (doTestConnection(n)) {
            if (receiverRegistered.compareAndSet(false, true))
              app.registerReceiver(loginReceiver, new IntentFilter(ACTION_LOGIN))
            val id = n.hashCode
            val nid = getNotificationId(id)
            app.nm.notify(nid, PortalManager.queryOnline(n).headOption match {
              case None => loginNotificationBuilder
                .setContentIntent(
                  app.pendingBroadcast(new Intent(ACTION_LOGIN).putExtra(EXTRA_NETWORK_ID, id)))
                .setAutoCancel(true)
                .build()
              case Some(entry) => entry.makeNotification(new Intent(OnlineEntryActivity.ACTION_SHOW)
                .putExtra(OnlineEntryActivity.EXTRA_NETWORK_ID, id)
                .putExtra(OnlineEntryActivity.EXTRA_NOTIFICATION_ID, nid))
            })
            NoticeManager.pushUnreadNotices
          }
        case 2 =>
          doLogin(n)
          NoticeManager.pushUnreadNotices
        case 3 => if (doTestConnection(n)) {
          doLogin(n)
          NoticeManager.pushUnreadNotices
        }
        case 4 => if (doTestConnection(n)) {
          PortalManager.queryOnline(n).headOption match {
            case None => doLogin(n)
            case Some(entry) =>
              if (receiverRegistered.compareAndSet(false, true))
                app.registerReceiver(loginReceiver, new IntentFilter(ACTION_LOGIN))
              val id = n.hashCode
              val nid = getNotificationId(id)
              app.nm.notify(nid, entry.makeNotification(new Intent(OnlineEntryActivity.ACTION_SHOW)
                .putExtra(OnlineEntryActivity.EXTRA_NETWORK_ID, id)
                .putExtra(OnlineEntryActivity.EXTRA_NOTIFICATION_ID, nid)))
          }
          NoticeManager.pushUnreadNotices
        }
        case _ =>
      } finally busy.synchronized(busy.remove(n.hashCode))
    } else Log.d(TAG, "Skipping repeated connection test request.")
    private def getCapabilities(capabilities: NetworkCapabilities) =
      networkCapabilities.get(capabilities).asInstanceOf[Long]

    def onLogin(n: Network, code: Int) {
      loginedNetwork = n
      cancelLoginNotification(getNotificationId(n.hashCode))
    }

    def reevaluate(): Unit = for ((id, (n, c)) <- available) testConnection(n)
    override def onAvailable(n: Network) {
      val capabilities = app.cm.getNetworkCapabilities(n)
      if (available.contains(n.hashCode)) {
        Log.d(TAG, "onAvailable (OLD: %s): %s".format(n, capabilities))
        if (Build.version < 23 && !ignoreSystemValidation) busy.synchronized(busy.remove(n.hashCode)) // validated on 5.x
      } else {
        Log.d(TAG, "onAvailable (%s): %s".format(n, capabilities))
        available(n.hashCode) = (n, getCapabilities(capabilities))
        if (Build.version < 23 || ignoreSystemValidation ||
          !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) testConnection(n)
      }
    }
    override def onCapabilitiesChanged(n: Network, capabilities: NetworkCapabilities = null) {
      val newCapabilities = getCapabilities(capabilities)
      if (available(n.hashCode)._2 == newCapabilities) return
      Log.d(TAG, "onCapabilitiesChanged (%s): %s".format(n, capabilities))
      available(n.hashCode) = (n, newCapabilities)
      if (Build.version < 23 || ignoreSystemValidation ||
        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        testConnection(n) else busy.synchronized(busy.remove(n.hashCode))
    }
    override def onLost(n: Network) {
      Log.d(TAG, "onLost (%s)".format(n))
      val id = n.hashCode
      available.remove(id)
      cancelLoginNotification(getNotificationId(id))
      if (n.equals(loginedNetwork)) loginedNetwork = null
    }

    def preferredNetwork: Network =
      if (loginedNetwork != null && available.contains(loginedNetwork.hashCode)) loginedNetwork
      else available.collectFirst {
        case (_, (n, _)) => n
      }.orNull
  }
  var listener: NetworkListener = _

  def initBoundConnections(): Unit = if (listener == null && app.boundConnectionsAvailable) {
    listener = new NetworkListener
    app.cm.requestNetwork(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
      .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
      .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build, listener)
  }

  private val receiverRegistered = new AtomicBoolean
  private lazy val loginReceiver: BroadcastReceiver =
    (_, intent) => listener.doLogin(intent.getIntExtra(EXTRA_NETWORK_ID, -1))

  override def onCreate() {
    super.onCreate()
    initBoundConnections()
    app.pref.registerOnSharedPreferenceChangeListener(this)
    instance = this
    Log.d(TAG, "Network monitor created.")
  }

  override def onDestroy() {
    instance = null
    app.pref.unregisterOnSharedPreferenceChangeListener(this)
    if (listener != null) {
      app.cm.unregisterNetworkCallback(listener)
      listener = null
    }
    if (receiverRegistered.compareAndSet(true, false)) app.unregisterReceiver(loginReceiver)
    super.onDestroy()
    Log.d(TAG, "Network monitor destroyed.")
  }

  def onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String): Unit =
    if (listener != null && key == LOCAL_MAC && !loggedIn) listener.reevaluate()
}
