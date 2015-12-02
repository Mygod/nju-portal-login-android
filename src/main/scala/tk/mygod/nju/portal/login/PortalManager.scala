package tk.mygod.nju.portal.login

import java.net._

import android.annotation.TargetApi
import android.net.{NetworkInfo, Network}
import android.util.Log
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import tk.mygod.util.CloseUtils._
import tk.mygod.util.IOUtils

/**
  * @author Mygod
  */
//noinspection JavaAccessorMethodCalledAsEmptyParen
object PortalManager {
  private val TAG = "PortalManager"

  private val portalDomain = "p.nju.edu.cn"

  private val status = "status"

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
    autoClose(conn.getOutputStream())(os => IOUtils.writeAllText(os, "username=%s&password=%s".format(
      App.instance.pref.getString("account.username", ""), App.instance.pref.getString("account.password", ""))))
  }

  private case class CaptivePortalException() extends Exception
  /**
    * Based on: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java#640
    */
  private def loginCore(conn: URL => URLConnection, onResult: (Int, Int) => Unit = null) {
    if (App.DEBUG) Log.d(TAG, "Logging in...")
    var code: Option[Int] = None
    try autoDisconnect(conn(new URL(App.http, portalDomain, "/portal_io/login")).asInstanceOf[HttpURLConnection])
    { conn =>
      setup(conn, App.instance.loginTimeout, 2)
      code = Some(conn.getResponseCode)
      if (!code.contains(200)) throw new CaptivePortalException
      val result = processResult(IOUtils.readAllText(conn.getInputStream()))
      if (onResult != null) onResult(if (result == 3 || result == 8) 2 else 0, result)
    } catch {
      case e: CaptivePortalException =>
        if (App.DEBUG) Log.w(TAG, "Unknown response code: " + code)
        if (onResult != null) onResult(2, 0)
      case e: ParserUtil.ParseException =>
        if (App.DEBUG) Log.w(TAG, "Parse failed: " + e.getMessage)
        if (onResult != null) onResult(2, 0)
      case e: SocketException =>
        App.instance.showToast(e.getMessage)
        e.printStackTrace
        if (onResult != null) onResult(2, 0)
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
  @TargetApi(21)
  def login(network: Network, onResult: (Int, Int) => Unit = null) =
    loginCore(network.openConnection, (code, result) => {
      if (result == 1 || result == 6) NetworkMonitor.reportNetworkConnectivity(network, true)
      if (onResult != null) onResult(code, result)
    })
  def loginLegacy(network: NetworkInfo = null, onResult: (Int, Int) => Unit = null) = loginCore({
    NetworkMonitor.preferNetworkLegacy(network)
    _.openConnection
  }, onResult)
  def login {
    if (NetworkMonitor.instance != null && App.instance.boundConnectionsAvailable > 1) {
      val network = NetworkMonitor.instance.listener.preferredNetwork
      if (network != null) {
        login(network)
        return
      }
    }
    loginLegacy()
  }

  def logout = try {
    val url = new URL(App.http, portalDomain, "/portal_io/logout")
    var network: Network = null
    autoDisconnect((if (NetworkMonitor.instance != null && App.instance.boundConnectionsAvailable > 1) {
      network = NetworkMonitor.instance.listener.preferredNetwork
      if (network != null) network.openConnection(url) else {
        NetworkMonitor.preferNetworkLegacy()
        url.openConnection
      }
    } else {
      NetworkMonitor.preferNetworkLegacy()
      url.openConnection
    }).asInstanceOf[HttpURLConnection]) { conn =>
      setup(conn, App.instance.loginTimeout, 1)
      if (processResult(IOUtils.readAllText(conn.getInputStream())) == 101 &&
        App.instance.boundConnectionsAvailable > 1 && network != null)
        NetworkMonitor.reportNetworkConnectivity(network, false)
    }
    if (NetworkMonitor.instance != null && NetworkMonitor.instance.listener != null)
      NetworkMonitor.instance.listener.loginedNetwork = null
    NetworkMonitor.listenerLegacy.loginedNetwork = null
  } catch {
    case e: Exception =>
      App.instance.showToast(e.getMessage)
      e.printStackTrace
  }
}
