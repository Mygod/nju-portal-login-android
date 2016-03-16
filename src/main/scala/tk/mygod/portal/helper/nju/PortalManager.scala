package tk.mygod.portal.helper.nju

import java.io.IOException
import java.net._
import java.security.MessageDigest

import android.annotation.TargetApi
import android.net.{Uri, Network, NetworkInfo}
import android.text.TextUtils
import android.util.Log
import org.json4s.ParserUtil.ParseException
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import tk.mygod.os.Build
import tk.mygod.portal.helper.nju.database.Notice
import tk.mygod.util.CloseUtils._
import tk.mygod.util.Conversions._
import tk.mygod.util.IOUtils

import scala.collection.mutable
import scala.util.Random

/**
  * Portal manager. Supports:
  *   Desktop v = 201510210840
  *   Mobile v = 201603011609
  *
  * To be supported:
  *   Hotel v = 201503170854
  *
  * @author Mygod
  */
object PortalManager {
  final val DOMAIN = "219.219.114.15"
  final val ROOT_URL = HTTP + "://" + DOMAIN
  private final val TAG = "PortalManager"
  private final val STATUS = "status"
  private final val POST_AUTH_BASE = "username=%s&password=%s"
  case class NetworkUnavailableException() extends IOException { }
  case class InvalidResponseException(response: String) extends IOException("Invalid response: " + response) { }

  def chap = app.pref.getBoolean("auth.chap", true)
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
  private def parseResult(conn: HttpURLConnection) = {
    //noinspection JavaAccessorMethodCalledAsEmptyParen
    val resultStr = IOUtils.readAllText(conn.getInputStream())
    if (DEBUG) Log.v(TAG, resultStr)
    val json = try parse(resultStr) catch {
      case e: ParseException => throw new InvalidResponseException(resultStr)
    }
    val code = json \ "reply_code" match {
      case i: JInt => i.values.toInt
      case _ => 0
    }
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
    if (code != 0 && (code != 1 && code != 6 && code != 101 || app.pref.getBoolean("notifications.login", true)))
      app.showToast("#%d: %s".format(code, (json \ "reply_msg").asInstanceOf[JString].values))
    (code, json)
  }

  /**
    * Based on: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java#640
    */
  def testConnectionCore(conn: URL => URLConnection): Boolean = {
    try autoDisconnect(conn(new URL(HTTP, "mygod.tk", "/generate_204")).asInstanceOf[HttpURLConnection]) { conn =>
      setup(conn, app.connectTimeout, false)
      conn.getInputStream
      if (conn.getResponseCode == 302) {
        val target = conn.getHeaderField("Location")
        if (DEBUG) Log.d(TAG, "Captive portal detected: " + target)
        return DOMAIN.equalsIgnoreCase(Uri.parse(target).getHost)
      }
    } catch {
      case _: SocketTimeoutException | _: UnknownHostException => // ignore
      case e: Exception =>
        app.showToast(e.getMessage)
        e.printStackTrace
    }
    false
  }
  @TargetApi(21)
  def testConnection(network: Network) = testConnectionCore(network.openConnection)
  def testConnectionLegacy(network: NetworkInfo) = testConnectionCore({
    NetworkMonitor.preferNetworkLegacy(network)
    _.openConnection
  })

  //noinspection ScalaDeprecation
  @TargetApi(21)
  def reportNetworkConnectivity(network: Network, hasConnectivity: Boolean) = if (Build.version >= 23)
    app.cm.reportNetworkConnectivity(network, hasConnectivity) else app.cm.reportBadNetwork(network)

  /**
    * Setup HttpURLConnection.
    *
    * @param conn HttpURLConnection.
    * @param timeout Connect/read timeout.
    * @param post Use HTTP post.
    */
  def setup(conn: HttpURLConnection, timeout: Int = 0, post: Boolean = true) {
    conn.setInstanceFollowRedirects(false)
    conn.setConnectTimeout(timeout)
    conn.setReadTimeout(timeout)
    conn.setUseCaches(false)
    if (post) conn.setRequestMethod("POST")
  }

  private def loginCore(conn: URL => URLConnection): (Int, Int) = {
    if (DEBUG) Log.d(TAG, "Logging in...")
    try {
      val chapPassword = if (chap)
        autoDisconnect(conn(new URL(HTTP, DOMAIN, "/portal_io/getchallenge")).asInstanceOf[HttpURLConnection]) { conn =>
          setup(conn, app.loginTimeout)
          val (code, json) = parseResult(conn)
          if (code != 0) return (1, 0)  // retry
          val challenge = (json \ "challenge").asInstanceOf[JString].values
          val passphrase = new Array[Byte](17)
          passphrase(0) = Random.nextInt.toByte
          val passphraseRaw = new mutable.ArrayBuffer[Byte]
          passphraseRaw += passphrase(0)
          passphraseRaw ++= password.getBytes
          passphraseRaw ++= challenge.sliding(2, 2).map(Integer.parseInt(_, 16).toByte)
          val digest = MessageDigest.getInstance("MD5")
          digest.update(passphraseRaw.toArray)
          digest.digest(passphrase, 1, 16)
          Some(passphrase.map("%02X".format(_)).mkString, challenge)
        } else None
      autoDisconnect(conn(new URL(HTTP, DOMAIN, "/portal_io/login")).asInstanceOf[HttpURLConnection]) { conn =>
        setup(conn, app.loginTimeout)
        conn.setDoOutput(true)
        //noinspection JavaAccessorMethodCalledAsEmptyParen
        autoClose(conn.getOutputStream())(os => IOUtils.writeAllText(os, chapPassword match {
          case Some((password, challenge)) => (POST_AUTH_BASE + "&challenge=%s").format(username, password, challenge)
          case None => POST_AUTH_BASE.format(username, password)
        }))
        conn.getResponseCode match {
          case 200 =>
            val (result, _) = parseResult(conn)
            (if (result == 3 || result == 8) 2 else 0, result)
          case 502 =>
            app.showToast("无可用服务器资源!")
            (1, 0)
          case 503 =>
            app.showToast("请求太频繁,请稍后再试!")
            (1, 0)
          case code =>
            if (DEBUG) Log.w(TAG, "Unknown response code: " + code)
            (2, 0)
        }
      }
    } catch {
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
      NoticeManager.pushUnreadNotices
    }
    result
  }
  def loginLegacy(n: NetworkInfo = null) = {
    var network = n
    val (result, code) = loginCore({
      network = NetworkMonitor.preferNetworkLegacy(n)
      _.openConnection
    })
    if (code == 1 || code == 6) {
      NetworkMonitor.listenerLegacy.onLogin(network, code)
      NoticeManager.pushUnreadNotices
    }
    result
  }
  def login: Int =
    if (NetworkMonitor.instance != null && app.boundConnectionsAvailable > 1) login(null) else loginLegacy()

  // AnyRef is a workaround for 4.x
  def openPortalConnection[T](file: String, explicit: Boolean = true)
                             (handler: (HttpURLConnection, AnyRef) => Option[T]) = try {
    val url = new URL(HTTP, DOMAIN, file)
    var n: AnyRef = null
    autoDisconnect((if (NetworkMonitor.instance != null && app.boundConnectionsAvailable > 1) {
      val network = NetworkMonitor.instance.listener.preferredNetwork
      if (network == null) throw new NetworkUnavailableException
      n = network
      network.openConnection(url)
    } else {
      NetworkMonitor.preferNetworkLegacy()
      url.openConnection
    }).asInstanceOf[HttpURLConnection])(handler(_, n))
  } catch {
    case e: NetworkUnavailableException =>
      if (explicit) app.showToast(app.getString(R.string.error_network_unavailable))
      None
    case e: ConnectException =>
      app.showToast(e.getMessage)
      None
    case e: Exception =>
      app.showToast(e.getMessage)
      e.printStackTrace
      None
  }

  def logout = openPortalConnection[Unit]("/portal_io/logout") { (conn, network) =>
    setup(conn)
    if (parseResult(conn)._1 == 101) {
      if (app.boundConnectionsAvailable > 1 && network != null)
        reportNetworkConnectivity(network.asInstanceOf[Network], false)
      NetworkMonitor.listenerLegacy.loginedNetwork = null
      if (NetworkMonitor.instance != null) {
        if (NetworkMonitor.instance.listener != null) NetworkMonitor.instance.listener.loginedNetwork = null
        NetworkMonitor.instance.reloginThread.synchronizedNotify()
      }
      Some()
    } else None
  }.nonEmpty

  def queryNotice(explicit: Boolean = true) = openPortalConnection[List[Notice]]("/portal_io/proxy/notice", explicit)
  { (conn, _) =>
    setup(conn)
    Some((parseResult(conn)._2 \ "notice").asInstanceOf[JArray].values
      .map(i => new Notice(i.asInstanceOf[Map[String, Any]])))
  }

  def queryVolume = openPortalConnection[JObject]("/portal_io/selfservice/volume/getlist") { (conn, _) =>
    setup(conn)
    val (code, json) = parseResult(conn)
    if (code == 0) {
      val total = (json \ "total").asInstanceOf[JInt].values
      if (total != BigInt(1)) throw new Exception("total = " + total)
      Some((json \ "rows")(0).asInstanceOf[JObject])
    } else None
  }
}
