package tk.mygod.portal.helper.nju

import java.util.concurrent.atomic.AtomicBoolean

import android.annotation.{SuppressLint, TargetApi}
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content._
import android.net.ConnectivityManager.NetworkCallback
import android.net._
import android.support.v4.app.NotificationCompat
import android.support.v4.content.{ContextCompat, LocalBroadcastManager}
import android.util.Log
import be.mygod.app.ServicePlus
import be.mygod.os.Build
import tk.mygod.portal.helper.nju.preference.MacAddressPreference
import tk.mygod.portal.helper.nju.util.RetryCounter

import scala.collection.mutable

object NetworkMonitor extends BroadcastReceiver with OnSharedPreferenceChangeListener {
  private final val TAG = "NetworkMonitor"
  private final val ACTION_LOGIN = "tk.mygod.portal.helper.nju.NetworkMonitor.ACTION_LOGIN"
  private final val ACTION_LOGIN_LEGACY = "tk.mygod.portal.helper.nju.NetworkMonitor.ACTION_LOGIN_LEGACY"
  private final val EXTRA_NETWORK_ID = "tk.mygod.portal.helper.nju.NetworkMonitor.EXTRA_NETWORK_ID"
  final val LOCAL_MAC = "misc.localMac"
  final val IGNORE_SYSTEM_VALIDATION = "misc.ignoreSystemValidation"

  def localMacs = app.pref.getString(LOCAL_MAC, MacAddressPreference.default()).split("\n").map(_.toLowerCase)
    .filter(_ != null).toSet
  def ignoreSystemValidation = app.pref.getBoolean(IGNORE_SYSTEM_VALIDATION, false)

  private lazy val networkCapabilities = {
    val result = classOf[NetworkCapabilities].getDeclaredField("mNetworkCapabilities")
    result.setAccessible(true)
    result
  }

  var instance: NetworkMonitor = _

  def cares(network: Int) =
    network > 6 && network != ConnectivityManager.TYPE_VPN || network == ConnectivityManager.TYPE_WIFI

  //noinspection ScalaDeprecation
  def preferNetworkLegacy(n: NetworkInfo = null) = {
    val network = if (n == null) listenerLegacy.preferredNetwork else n
    val preference = if (network == null) ConnectivityManager.TYPE_WIFI else network.getType
    app.cm.setNetworkPreference(preference)
    Log.v(TAG, "Setting network preference: " + preference)
    network
  }

  def loginNotificationBuilder = new NotificationCompat.Builder(app)
    .setColor(ContextCompat.getColor(app, R.color.material_primary_500))
    .setLights(ContextCompat.getColor(app, R.color.material_purple_a700), app.lightOnMs, app.lightOffMs)
    .setSmallIcon(R.drawable.ic_device_signal_wifi_statusbar_not_connected).setGroup(ACTION_LOGIN)
    .setContentTitle(app.getString(R.string.network_available_sign_in))
    .setShowWhen(false).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

  def cancelLoginNotification(id: Int) = {
    app.nm.cancel(id)
    LocalBroadcastManager.getInstance(app).sendBroadcast(new Intent(OnlineEntryActivity.ACTION_CANCEL)
      .putExtra(OnlineEntryActivity.EXTRA_NOTIFICATION_ID, id))
  }

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
    def doLogin(n: NetworkInfo) {
      val counter = new RetryCounter
      while (instance != null && loginedNetwork == null &&
        available.contains(serialize(n)) && PortalManager.loginLegacy(n)) counter.retry()
    }

    def onLogin(n: NetworkInfo, code: Int) {
      loginedNetwork = n
      cancelLoginNotification(getNotificationId(serialize(n)))
    }

    def reevaluate =
      if (app.serviceStatus > 0 && app.boundConnectionsAvailable < 2) for ((id, n) <- available) onAvailable(n)
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
              val nid = getNotificationId(id)
              app.nm.notify(nid, PortalManager.queryOnlineLegacy(n).headOption match {
                case None => loginNotificationBuilder
                  .setContentIntent(
                    app.pendingBroadcast(new Intent(ACTION_LOGIN_LEGACY).putExtra(EXTRA_NETWORK_ID, id)))
                  .setAutoCancel(true)
                  .build()
                case Some(entry) => entry.makeNotification(new Intent(OnlineEntryActivity.ACTION_SHOW_LEGACY)
                  .putExtra(OnlineEntryActivity.EXTRA_NETWORK_ID, id)
                  .putExtra(OnlineEntryActivity.EXTRA_NOTIFICATION_ID, nid))
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
              case None => doLogin(n)
              case Some(entry) =>
                if (receiverRegistered.compareAndSet(false, true))
                  app.registerReceiver(NetworkMonitor, new IntentFilter(ACTION_LOGIN_LEGACY))
                val id = n.hashCode
                val nid = getNotificationId(id)
                app.nm.notify(nid, entry.makeNotification(new Intent(OnlineEntryActivity.ACTION_SHOW_LEGACY)
                  .putExtra(OnlineEntryActivity.EXTRA_NETWORK_ID, id)
                  .putExtra(OnlineEntryActivity.EXTRA_NOTIFICATION_ID, nid)))
            }
            NoticeManager.pushUnreadNotices
          }
          case _ =>
        }
        busy.synchronized(busy.remove(serialize(n)))
      }
    }

    def onLost(n: NetworkInfo) {
      val id = serialize(n)
      available.remove(id)
      cancelLoginNotification(getNotificationId(id))
      if (loginedNetwork != null && id == serialize(loginedNetwork)) loginedNetwork = null
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

  def onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) =
    if (key == LOCAL_MAC && loginStatus <= 0) listenerLegacy.reevaluate
}

final class NetworkMonitor extends ServicePlus with OnSharedPreferenceChangeListener {
  import NetworkMonitor._

  @SuppressLint(Array("NewApi"))
  private def loggedIn = listener != null && app.boundConnectionsAvailable > 1 && listener.loginedNetwork != null

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
    private def doLogin(n: Network) {
      val counter = new RetryCounter()
      while (available.contains(n.hashCode) && loginedNetwork == null && busy.synchronized(busy.contains(n.hashCode)) &&
        app.serviceStatus > 0 && PortalManager.login(n)) counter.retry()
    }

    private def testConnection(n: Network) = if (busy.synchronized(busy.add(n.hashCode))) ThrowableFuture {
      try app.serviceStatus match {
        case 1 =>
          if (PortalManager.testConnection(n)) {
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
        case 3 => if (PortalManager.testConnection(n)) {
          doLogin(n)
          NoticeManager.pushUnreadNotices
        }
        case 4 => if (PortalManager.testConnection(n)) {
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
    }
    private def getCapabilities(capabilities: NetworkCapabilities) =
      networkCapabilities.get(capabilities).asInstanceOf[Long]

    def onLogin(n: Network, code: Int) {
      loginedNetwork = n
      cancelLoginNotification(getNotificationId(n.hashCode))
    }

    def reevaluate = for ((id, (n, c)) <- available) testConnection(n)
    override def onAvailable(n: Network) {
      val capabilities = app.cm.getNetworkCapabilities(n)
      Log.d(TAG, "onAvailable (%s): %s".format(n, capabilities))
      if (available.contains(n.hashCode)) {
        if (Build.version < 23 && !ignoreSystemValidation) busy.synchronized(busy.remove(n.hashCode)) // validated on 5.x
      } else {
        available(n.hashCode) = (n, getCapabilities(capabilities))
        if (Build.version < 23 || ignoreSystemValidation ||
          !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) testConnection(n)
      }
    }
    override def onCapabilitiesChanged(n: Network, capabilities: NetworkCapabilities = null) {
      Log.d(TAG, "onCapabilitiesChanged (%s): %s".format(n, capabilities))
      val newCapabilities = getCapabilities(capabilities)
      if (available(n.hashCode)._2 == newCapabilities) return
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

    def preferredNetwork = if (loginedNetwork != null && available.contains(loginedNetwork.hashCode)) loginedNetwork
      else available.collectFirst {
        case (_, (n, _)) => n
      }.orNull
  }
  @TargetApi(21)
  var listener: NetworkListener = _

  @SuppressLint(Array("NewApi"))
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
    app.pref.registerOnSharedPreferenceChangeListener(this)
    instance = this
    Log.d(TAG, "Network monitor created.")
  }

  @SuppressLint(Array("NewApi"))
  override def onDestroy {
    instance = null
    app.pref.unregisterOnSharedPreferenceChangeListener(this)
    if (listener != null) {
      app.cm.unregisterNetworkCallback(listener)
      listener = null
    }
    if (receiverRegistered.compareAndSet(true, false)) app.unregisterReceiver(loginReceiver)
    super.onDestroy
    Log.d(TAG, "Network monitor destroyed.")
  }

  @SuppressLint(Array("NewApi"))
  def onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) =
    if (listener != null && key == LOCAL_MAC && loginStatus <= 0) listener.reevaluate
}
