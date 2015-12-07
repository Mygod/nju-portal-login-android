package tk.mygod.nju.portal.login

import java.io.IOException
import java.net._

import android.annotation.TargetApi
import android.net.{Network, NetworkInfo}
import android.text.TextUtils
import android.util.Log
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import tk.mygod.os.Build
import tk.mygod.util.CloseUtils._
import tk.mygod.util.Conversions._
import tk.mygod.util.IOUtils

/**
  * Portal manager. Supports:
  *   Desktop v = 201510210840
  *   Mobile v = 201509101358
  *
  * @author Mygod
  */
//noinspection JavaAccessorMethodCalledAsEmptyParen
object PortalManager {
  final val DOMAIN = "p.nju.edu.cn"
  final val ROOT_URL = HTTP + "://" + DOMAIN
  private final val TAG = "PortalManager"
  private final val STATUS = "status"
  case class NetworkUnavailableException() extends IOException { }

  var currentUsername: String = _
  def username = app.pref.getString("account.username", "")
  def password = app.pref.getString("account.password", "")

  private var userInfoListener: JObject => Any = _
  def setUserInfoListener(listener: JObject => Any) {
    userInfoListener = listener
    if (listener == null) return
    val info = app.pref.getString(STATUS, "")
    if (!info.isEmpty) listener(parse(info).asInstanceOf[JObject])
  }

  private implicit val formats = Serialization.formats(NoTypeHints)
  private def processResult(resultStr: String) = {
    if (DEBUG) Log.v(TAG, resultStr)
    val json = parse(resultStr)
    val code = (json \ "reply_code").asInstanceOf[JInt].values.toInt
    val info = json \ "userinfo"
    info match {
      case obj: JObject =>
        app.editor.putString(STATUS, compact(render(info))).apply
        if (userInfoListener != null) {
          currentUsername = (obj \ "username").asInstanceOf[JString].values
          app.handler.post(userInfoListener(obj))
        }
      case _ =>
    }
    if (app.pref.getBoolean("notifications.login", true))
      app.showToast("#%d: %s".format(code, (json \ "reply_msg").asInstanceOf[JString].values))
    code
  }

  //noinspection ScalaDeprecation
  @TargetApi(21)
  def reportNetworkConnectivity(network: Network, hasConnectivity: Boolean) = if (Build.version >= 23)
    app.cm.reportNetworkConnectivity(network, hasConnectivity) else app.cm.reportBadNetwork(network)

  /**
    * Setup HttpURLConnection.
    *
    * @param conn HttpURLConnection.
    * @param timeout Connect/read timeout.
    * @param output 0-2: Nothing, post, post username/password.
    */
  def setup(conn: HttpURLConnection, timeout: Int, output: Int = 0) {
    conn.setInstanceFollowRedirects(false)
    conn.setConnectTimeout(timeout)
    conn.setReadTimeout(timeout)
    conn.setUseCaches(false)
    if (output == 0) return
    conn.setRequestMethod("POST")
    if (output == 1) return
    conn.setDoOutput(true)
    //noinspection JavaAccessorMethodCalledAsEmptyParen
    autoClose(conn.getOutputStream())(os =>
      IOUtils.writeAllText(os, "username=%s&password=%s".format(username, password)))
  }

  private case class CaptivePortalException() extends Exception
  /**
    * Based on: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java#640
    */
  private def loginCore(conn: URL => URLConnection) = {
    if (DEBUG) Log.d(TAG, "Logging in...")
    var code: Option[Int] = None
    try autoDisconnect(conn(new URL(HTTP, DOMAIN, "/portal_io/login")).asInstanceOf[HttpURLConnection])
    { conn =>
      setup(conn, app.loginTimeout, 2)
      code = Some(conn.getResponseCode)
      if (!code.contains(200)) throw new CaptivePortalException
      val result = processResult(IOUtils.readAllText(conn.getInputStream()))
      (if (result == 3 || result == 8) 2 else 0, result)
    } catch {
      case e: CaptivePortalException =>
        if (DEBUG) Log.w(TAG, "Unknown response code: " + code)
        (2, 0)
      case e: ParserUtil.ParseException =>
        if (DEBUG) Log.w(TAG, "Parse failed: " + e.getMessage)
        (2, 0)
      case e: SocketException =>
        app.showToast(e.getMessage)
        e.printStackTrace
        (2, 0)
      case e: SocketTimeoutException =>
        val msg = e.getMessage
        app.showToast(if (TextUtils.isEmpty(msg)) app.getString(R.string.error_socket_timeout) else msg)
        (1, 0)
      case e: UnknownHostException =>
        app.showToast(e.getMessage)
        (1, 0)
      case e: Exception =>
        app.showToast(e.getMessage)
        e.printStackTrace
        (1, 0)
    }
  }
  @TargetApi(21)
  def login(n: Network) = {
    val network = if (n == null) NetworkMonitor.instance.listener.preferredNetwork else n
    if (network == null) throw new NetworkUnavailableException
    val (result, code) = loginCore(network.openConnection)
    if (code == 1 || code == 6) {
      reportNetworkConnectivity(network, true)
      NetworkMonitor.instance.listener.onLogin(network, code)
    }
    result
  }
  def loginLegacy(n: NetworkInfo = null) = {
    var network = n
    val (result, code) = loginCore({
      network = NetworkMonitor.preferNetworkLegacy(n)
      _.openConnection
    })
    if (code == 1 || code == 6) NetworkMonitor.listenerLegacy.onLogin(network, code)
    result
  }
  def login: Int =
    if (NetworkMonitor.instance != null && app.boundConnectionsAvailable > 1) login(null) else loginLegacy()

  def logout = try {
    val url = new URL(HTTP, DOMAIN, "/portal_io/logout")
    var network: Network = null
    autoDisconnect((if (NetworkMonitor.instance != null && app.boundConnectionsAvailable > 1) {
      network = NetworkMonitor.instance.listener.preferredNetwork
      if (network == null) throw new NetworkUnavailableException
      network.openConnection(url)
    } else {
      NetworkMonitor.preferNetworkLegacy()
      url.openConnection
    }).asInstanceOf[HttpURLConnection]) { conn =>
      setup(conn, 0, 1)
      if (processResult(IOUtils.readAllText(conn.getInputStream())) == 101) {
        if (app.boundConnectionsAvailable > 1 && network != null) reportNetworkConnectivity(network, false)
        NetworkMonitor.listenerLegacy.loginedNetwork = null
        if (NetworkMonitor.instance != null) {
          if (NetworkMonitor.instance.listener != null) NetworkMonitor.instance.listener.loginedNetwork = null
          NetworkMonitor.instance.reloginThread.synchronizedNotify()
        }
        true
      } else false
    }
  } catch {
    case e: ConnectException =>
      app.showToast(e.getMessage)
      false
    case e: Exception =>
      app.showToast(e.getMessage)
      e.printStackTrace
      false
  }
}
