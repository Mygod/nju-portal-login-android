package tk.mygod.nju.portal.login

import java.net._

import android.annotation.TargetApi
import android.app.Service
import android.content.{Context, Intent}
import android.net.ConnectivityManager.NetworkCallback
import android.net._
import android.os.{Binder, Build}
import android.util.Log
import org.json4s.native.JsonMethods.{compact, parse, render}
import org.json4s.native.Serialization
import org.json4s.{JInt, JObject, JString, NoTypeHints}
import tk.mygod.util.CloseUtils._
import tk.mygod.util.IOUtils

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

object PortalManager {
  private val TAG = "PortalManager"

  private val loggingIn = "Logging in..."

  private val http = "http"
  private val portalDomain = "p.nju.edu.cn"
  private val portalLogin = "/portal_io/login"

  private val status = "status"

  private implicit val networkOrdering = Ordering.by[Network, Long](_.getNetworkHandle)
  private implicit val networkInfoOrdering = Ordering.by[NetworkInfo, (Int, Int)](i => (i.getType, i.getSubtype))

  var running: Boolean = _

  def cares(network: Int) =
    network > 5 && network != ConnectivityManager.TYPE_VPN || network == ConnectivityManager.TYPE_WIFI

  private var userInfoListener: JObject => Any = _
  def setUserInfoListener(listener: JObject => Any) {
    userInfoListener = listener
    if (listener == null) return
    val info = App.instance.pref.getString(status, "")
    if (!info.isEmpty) listener(parse(info).asInstanceOf[JObject])
  }

  private implicit val formats = Serialization.formats(NoTypeHints)
  private def processResult(resultStr: String) = {
    if (App.DEBUG) Log.d(TAG, resultStr)
    val json = parse(resultStr)
    val code = (json \ "reply_code").asInstanceOf[JInt].values.toInt
    val info = json \ "userinfo"
    if (App.DEBUG) Log.d(TAG, info.getClass.getName + " - " + info.toString)
    info match {
      case obj: JObject =>
        App.instance.editor.putString(status, compact(render(info))).apply
        if (userInfoListener != null) App.handler.post(() => userInfoListener(obj))
      case _ =>
    }
    if (App.instance.pref.getBoolean("notifications.login", true))
      App.instance.showToast("#%d: %s".format(code, (json \ "reply_msg").asInstanceOf[JString].values))
    code
  }

  /**
    * Setup HttpURLConnection.
    * @param conn HttpURLConnection.
    * @param timeout Connect/read timeout.
    * @param output 0-2: Nothing, post, post username/password.
    */
  private def setup(conn: HttpURLConnection, timeout: Int, output: Int = 0) {
    conn.setInstanceFollowRedirects(false)
    conn.setConnectTimeout(timeout)
    conn.setReadTimeout(timeout)
    conn.setUseCaches(false)
    if (output == 0) return
    conn.setRequestMethod("POST")
    if (output == 1) return
    conn.setDoOutput(true)
    //noinspection JavaAccessorMethodCalledAsEmptyParen
    autoClose(conn.getOutputStream())(os => IOUtils.writeAllText(os, "username=%s&password=%s".format(
      App.instance.pref.getString("account.username", ""), App.instance.pref.getString("account.password", ""))))
  }
}

//noinspection JavaAccessorMethodCalledAsEmptyParen
final class PortalManager extends Service {
  import PortalManager._

  private lazy val cm = getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]

  private var retryCount: Int = _
  private def retryDelay = {
    if (retryCount < 10) retryCount = retryCount + 1
    2000 + Random.nextInt(1000 << retryCount) // prevent overwhelming failing notifications
  }
  private def onNetworkAvailable(start: Long) {
    retryCount = 0
    if (App.instance.pref.getBoolean("notifications.connection", true))
      App.instance.showToast(getString(R.string.network_available).format(System.currentTimeMillis - start))
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private class NetworkListener extends NetworkCallback {
    private val available = new mutable.TreeSet[Network]
    private val testing = new mutable.HashMap[Network, Long]
    var loginedNetwork: Network = _

    private def waitForNetwork(n: Network) = if (!testing.contains(n))
      if (App.instance.skipConnect) {
        if (App.instance.autoConnectEnabled) login(n, onLoginResult(n, _, _))
      } else {
        testing += ((n, System.currentTimeMillis))
        App.handler.postDelayed(() => if (testing.contains(n) && available.contains(n)) {
          testing.remove(n)
          Future {
            Thread.sleep(retryDelay)
            if (App.instance.autoConnectEnabled && available.contains(n)) login(n, onLoginResult(n, _, _))
          }
        }, App.instance.connectTimeout)
      }
    private def onLoginResult(n: Network, result: Int, code: Int): Unit =
      if (result != 2) if (code == 1 || code == 6) loginedNetwork = n else waitForNetwork(n)
    private def onAvailable(n: Network, unsure: Boolean) = testing.get(n) match {
      case Some(start) =>
        if (App.DEBUG && Build.VERSION.SDK_INT >= 23 && unsure) Log.w(TAG, "onAvailable called twice! WTF?")
        onNetworkAvailable(start)
        testing.remove(n)
      case None => if (unsure) waitForNetwork(n)
    }

    override def onAvailable(n: Network) {
      if (App.DEBUG) Log.d(TAG, "onAvailable (%s)".format(n))
      available.add(n)
      onAvailable(n, true)
    }
    override def onCapabilitiesChanged(n: Network, networkCapabilities: NetworkCapabilities) {
      if (App.DEBUG) Log.d(TAG, "onCapabilitiesChanged (%s): %s".format(n, networkCapabilities))
      if (App.instance.autoConnectEnabled &&
        !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL))
        if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) waitForNetwork(n)
        else if (Build.VERSION.SDK_INT >= 23)
          if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) onAvailable(n, false)
          else waitForNetwork(n)
    }
    override def onLost(n: Network) {
      if (App.DEBUG) Log.d(TAG, "onLost (%s)".format(n))
      available.remove(n)
    }

    def preferredNetwork = if (available.contains(loginedNetwork)) loginedNetwork else available.collectFirst {
      case n: Network => n
    }.orNull
  }
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private var listener: NetworkListener = _

  //noinspection ScalaDeprecation
  private class NetworkListenerLegacy {
    private val available = new mutable.TreeSet[NetworkInfo]
    private val testing = new mutable.TreeSet[NetworkInfo]
    var loginedNetwork: NetworkInfo = _

    private def shouldLogin(n: NetworkInfo) = running && available.contains(n) && App.instance.autoConnectEnabled
    private def onLoginResult(n: NetworkInfo, result: Int, code: Int): Unit = if (code == 1 || code == 6) {
      loginedNetwork = n
      testing.remove(n)
    } else if (result == 2 || !shouldLogin(n)) testing.remove(n) else {
      Thread.sleep(retryDelay)
      loginLegacy(n, onLoginResult(n, _, _))
    }

    def onAvailable(n: NetworkInfo) {
      available.add(n)
      if (testing.contains(n)) return
      testing.add(n)
      Future(if (App.instance.skipConnect) loginLegacy(n, onLoginResult(n, _, _)) else {
        if (App.DEBUG) Log.d(TAG, "Testing connection manually...")
        val url = new URL(http, "mygod.tk", "/generate_204")  // TODO: custom domain
        preferNetworkLegacy(n)
        try autoDisconnect(url.openConnection.asInstanceOf[HttpURLConnection]) { conn =>
          setup(conn, App.instance.connectTimeout)
          val start = System.currentTimeMillis
          conn.getInputStream
          val code = conn.getResponseCode
          if (code == 204 || code == 200 && conn.getContentLength == 0) onNetworkAvailable(start)
        } catch {
          case _: SocketTimeoutException | _: UnknownHostException =>
            if (shouldLogin(n)) {
              loginLegacy(n, onLoginResult(n, _, _))
              return
            }
          case e: Exception =>
            App.instance.showToast(e.getMessage)
            e.printStackTrace
        }
        testing.remove(n)
      })
    }

    def onLost(n: NetworkInfo) = available.remove(n)

    def preferredNetwork = if (available.contains(loginedNetwork)) loginedNetwork else
      cm.getAllNetworkInfo.collectFirst {
        case n: NetworkInfo if cares(n.getType) => n
      }.orNull
  }
  private lazy val listenerLegacy = new NetworkListenerLegacy

  //noinspection ScalaDeprecation
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private def reportNetworkConnectivity(network: Network, hasConnectivity: Boolean) = if (Build.VERSION.SDK_INT >= 23)
    cm.reportNetworkConnectivity(network, hasConnectivity) else cm.reportBadNetwork(network)

  //noinspection ScalaDeprecation
  private def preferNetworkLegacy(n: NetworkInfo = null) {
    val network = if (n == null) listenerLegacy.preferredNetwork else n
    val preference = if (network == null) ConnectivityManager.TYPE_WIFI else network.getType
    cm.setNetworkPreference(preference)
    if (App.DEBUG) Log.d(TAG, "Setting network preference: " + preference)
  }

  /**
    * Based on: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java#640
    */
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  def login(network: Network, onResult: (Int, Int) => Unit = null) {
    if (App.DEBUG) Log.d(TAG, loggingIn)
    try autoDisconnect(network.openConnection(new URL(http, portalDomain, portalLogin))
      .asInstanceOf[HttpURLConnection]) { conn =>
      setup(conn, App.instance.loginTimeout, 2)
      val result = processResult(IOUtils.readAllText(conn.getInputStream()))
      if (result == 1 || result == 6) reportNetworkConnectivity(network, true)
      if (onResult != null) onResult(if (result == 3 || result == 8) 2 else 0, result)
    } catch {
      case e: SocketException =>
        App.instance.showToast(e.getMessage)
        e.printStackTrace
        if (onResult != null) onResult(2, 0)
      case e: Exception =>
        App.instance.showToast(e.getMessage)
        e.printStackTrace
        if (onResult != null) onResult(1, 0)
    }
  }
  def loginLegacy(network: NetworkInfo = null, onResult: (Int, Int) => Unit = null) {
    if (App.DEBUG) Log.d(TAG, loggingIn)
    preferNetworkLegacy(network)
    try autoDisconnect(new URL(http, portalDomain, portalLogin).openConnection.asInstanceOf[HttpURLConnection])
    { conn =>
      setup(conn, App.instance.loginTimeout, 2)
      val result = processResult(IOUtils.readAllText(conn.getInputStream()))
      if (onResult != null) onResult(if (result == 3 || result == 8) 2 else 0, result)
    } catch {
      case e: SocketException =>
        e.printStackTrace
        onResult(2, 0)
      case e: SocketTimeoutException =>
        App.instance.showToast(App.instance.getString(R.string.error_socket_timeout))
        if (onResult != null) onResult(1, 0)
      case e: UnknownHostException =>
        App.instance.showToast(e.getMessage)
        if (onResult != null) onResult(1, 0)
      case e: Exception =>
        App.instance.showToast(e.getMessage)
        e.printStackTrace
        if (onResult != null) onResult(1, 0)
    }
  }
  def login {
    if (App.instance.boundConnectionsAvailable > 1) {
      val network = listener.preferredNetwork
      if (network != null) {
        login(network)
        return
      }
    }
    loginLegacy()
  }

  def logout = try {
    val url = new URL(http, portalDomain, "/portal_io/logout")
    var network: Network = null
    autoDisconnect((if (App.instance.boundConnectionsAvailable > 1 && listener.preferredNetwork != null) {
      network = listener.preferredNetwork
      if (network != null) network.openConnection(url) else {
        preferNetworkLegacy()
        url.openConnection
      }
    } else {
      preferNetworkLegacy()
      url.openConnection
    }).asInstanceOf[HttpURLConnection]) { conn =>
      setup(conn, App.instance.loginTimeout, 1)
      if (processResult(IOUtils.readAllText(conn.getInputStream())) == 101 && network != null &&
        App.instance.boundConnectionsAvailable > 1) reportNetworkConnectivity(network, false)
    }
    if (listener != null) listener.loginedNetwork = null
    listenerLegacy.loginedNetwork = null
  } catch {
    case e: Exception =>
      App.instance.showToast(e.getMessage)
      e.printStackTrace
  }

  class ServiceBinder extends Binder {
    def service = PortalManager.this
  }
  def onBind(intent: Intent) = new ServiceBinder

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    if (intent != null && intent.getAction == ConnectivityManager.CONNECTIVITY_ACTION &&
      App.instance.autoConnectEnabled) {
      //noinspection ScalaDeprecation
      val n = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO).asInstanceOf[NetworkInfo]
      if (n.isConnected) listenerLegacy.onAvailable(n) else listenerLegacy.onLost(n)
    }
    Service.START_STICKY
  }

  def initBoundConnections = if (listener == null && App.instance.boundConnectionsAvailable > 1) {
    listener = new NetworkListener
    cm.requestNetwork(new NetworkRequest.Builder()
      .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
      .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
      .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build, listener)
  }

  override def onCreate = {
    super.onCreate
    initBoundConnections
    running = true
    if (App.DEBUG) Log.d(TAG, "Service created.")
  }

  override def onDestroy = {
    super.onDestroy
    if (listener != null) {
      cm.unregisterNetworkCallback(listener)
      listener = null
    }
    running = false
    if (App.DEBUG) Log.d(TAG, "Service destroyed.")
  }
}
