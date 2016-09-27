package tk.mygod.portal.helper.nju

import java.io.IOException
import java.net._
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

import android.annotation.{SuppressLint, TargetApi}
import android.content.Intent
import android.net.{Network, NetworkInfo}
import android.support.v4.app.NotificationCompat
import android.text.style.URLSpan
import android.text.{SpannableStringBuilder, Spanned, TextUtils}
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
  *   Desktop v = 201606170633
  *   Mobile v = 201606170633
  *
  * To be supported:
  *   Hotel v = 201503170854
  *
  * @author Mygod
  */
object PortalManager {
  final val DOMAIN = "p.nju.edu.cn"
  private final val TAG = "PortalManager"
  private final val STATUS = "status"
  private final val AUTH_BASE = "username=%s&password=%s"
  case class NetworkUnavailableException() extends IOException { }
  case class InvalidResponseException(response: String) extends IOException("Invalid response: " + response) { }

  var currentUsername: String = _
  def username = app.pref.getString("account.username", "")
  def password = app.pref.getString("account.password", "")

  private val ipv4Matcher = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$".r
  def isValidHost(host: String) = host != null && (DOMAIN.equalsIgnoreCase(host) ||
    ipv4Matcher.findFirstIn(host).nonEmpty && host.startsWith("210.28.129.") || host.startsWith("219.219.114."))

  private var userInfoListener: JObject => Any = _
  def setUserInfoListener(listener: JObject => Any) {
    userInfoListener = listener
    if (listener != null) getUserInfo match {
      case Some(info) => listener(info)
      case _ =>
    }
  }
  def getUserInfo = {
    val info = app.pref.getString(STATUS, "")
    if (info.isEmpty) None else Some(parse(info).asInstanceOf[JObject])
  }
  def updateUserInfo(info: JObject) {
    app.editor.putString(STATUS, compact(render(info))).apply
    if (userInfoListener != null) {
      currentUsername = (info \ "username").asInstanceOf[JString].values
      app.handler.post(userInfoListener(info))
    }
  }

  private implicit val formats = Serialization.formats(NoTypeHints)
  private def parseResult(conn: HttpURLConnection, login: Boolean = false) = {
    //noinspection JavaAccessorMethodCalledAsEmptyParen
    val resultStr = IOUtils.readAllText(conn.getInputStream())
    if (BuildConfig.DEBUG) Log.v(TAG, resultStr)
    val json = try parse(resultStr) catch {
      case e: ParseException => throw InvalidResponseException(resultStr)
    }
    if (!json.isInstanceOf[JObject]) throw InvalidResponseException(resultStr)
    val code = json \ "reply_code" match {
      case i: JInt => i.values.toInt
      case _ => 0
    }
    json \ "userinfo" match {
      case info: JObject =>
        updateUserInfo(info)
        if (login && code == 1) app.handler.postDelayed(() => BalanceManager.check(info), 1000) // first login
        else app.handler.post(() => BalanceManager.check(info))
      case _ =>
    }
    if (code != 0 && code != 2 && code != 9 &&
      (code != 1 && code != 6 && code != 101 || app.pref.getBoolean("notifications.login", true)))
      app.showToast((json \ "reply_msg").asInstanceOf[JString].values)
    (code, json)
  }

  def parseTimeString(value: BigInt) =
    DateFormat.getDateTimeInstance.format(new Date(TimeUnit.SECONDS.toMillis(value.toLong)))
  def parseIpv4(value: BigInt) = {
    val bytes = value.asInstanceOf[BigInt].toInt
    InetAddress.getByAddress(Array[Byte]((bytes >>> 24 & 0xFF).toByte, (bytes >>> 16 & 0xFF).toByte,
      (bytes >>> 8 & 0xFF).toByte, (bytes & 0xFF).toByte)).getHostAddress
  }

  /**
    * Based on: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java#640
    *
    * @return 0 for success, 1 for failure, 2 for login required
    */
  def testConnectionCore(conn: URL => URLConnection): Int =
    try autoDisconnect(conn(new URL(HTTP, DOMAIN, "/portal_io/getinfo")).asInstanceOf[HttpURLConnection]) { conn =>
      setup(conn)
      val (code, json) = parseResult(conn)
      if (code == 0) updateUserInfo((json \ "userinfo").asInstanceOf[JObject])  // 操作成功
      code  // 2: 无用户portal信息
    } catch {
      case e: InvalidResponseException =>
        if (BuildConfig.DEBUG) Log.w(TAG, e.getMessage)
        1
      case _: SocketTimeoutException | _: UnknownHostException | _: ConnectException => 1 // ignore
      case e: Exception =>
        app.showToast(e.getMessage)
        e.printStackTrace
        1
    }
  @TargetApi(21)
  def testConnection(network: Network) = testConnectionCore(network.openConnection) match {
    case 0 =>
      reportNetworkConnectivity(network, true)
      false
    case 1 =>
      reportNetworkConnectivity(network, false)
      false
    case 2 => true
  }
  def testConnectionLegacy(network: NetworkInfo) = testConnectionCore({
    NetworkMonitor.preferNetworkLegacy(network)
    _.openConnection
  }) == 2

  //noinspection ScalaDeprecation
  @TargetApi(21)
  def reportNetworkConnectivity(network: Network, hasConnectivity: Boolean) = if (Build.version >= 23)
    app.cm.reportNetworkConnectivity(network, hasConnectivity) else app.cm.reportBadNetwork(network)

  /**
    * Setup HttpURLConnection.
    *
    * @param conn HttpURLConnection.
    * @param post Set this to null to do a get. Otherwise post what you want to post.
    */
  def setup(conn: HttpURLConnection, post: String = "") {
    conn.setInstanceFollowRedirects(false)
    conn.setConnectTimeout(4000)
    conn.setReadTimeout(4000)
    conn.setUseCaches(false)
    if (post == null) return
    conn.setRequestMethod("POST")
    if (post.isEmpty) return
    conn.setDoOutput(true)
    //noinspection JavaAccessorMethodCalledAsEmptyParen
    autoClose(conn.getOutputStream())(os => IOUtils.writeAllText(os, post))
  }

  class OnlineEntry(obj: JObject) {
    val mac = (obj \ "mac").asInstanceOf[JString].values
    val ipv4 = parseIpv4((obj \ "user_ipv4").asInstanceOf[JInt].values)
    val ipv6 = (obj \ "user_ipv6").asInstanceOf[JString].values
    val ipv6Valid = !TextUtils.isEmpty(ipv6) && ipv6 != "::"
    def makeNotification(contentIntent: Intent) = {
      val summary = new SpannableStringBuilder()
      val rawSummary = app.getString(R.string.network_available_sign_in_conflict)
      var start = 0
      var i = rawSummary.indexOf('%')
      while (i >= 0) {
        summary.append(rawSummary.substring(start, i))
        start = i + 4
        rawSummary.substring(i, start) match {
          case "%1$s" =>
            val from = summary.length
            summary.append(mac)
            summary.setSpan(new URLSpan(app.getMacLookup(mac)), from, summary.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
          case "%2$s" => summary.append(obj \ "area_name" match {
            case str: JString => str.values
            case _ => "未知区域"
          })
          case "%3$s" => summary.append(parseTimeString(
            (obj \ BalanceManager.KEY_ACTIVITY_START_TIME).asInstanceOf[JInt].values))
        }
        i = rawSummary.indexOf('%', start)
      }
      if (start < rawSummary.length) summary.append(rawSummary.substring(start))
      summary.append("\nIP: ")
      val from = summary.length
      summary.append(ipv4)
      summary.setSpan(new URLSpan(app.getIpLookup(ipv4)), from, summary.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      if (ipv6Valid) {
        summary.append(", ")
        val from = summary.length
        summary.append(ipv6)
        summary.setSpan(new URLSpan(app.getIpLookup(ipv6)), from, summary.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }

      val builder = NetworkMonitor.loginNotificationBuilder.setContentIntent(app.pendingActivity(contentIntent
        .putExtra(OnlineEntryActivity.EXTRA_MAC, mac)
        .putExtra(OnlineEntryActivity.EXTRA_TEXT, summary)))
      if (Build.version >= 21)
        builder.setPublicVersion(builder.build).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      new NotificationCompat.BigTextStyle(builder.setContentText(summary)).bigText(summary).build
    }
  }
  private def queryOnlineCore(conn: URL => URLConnection): List[OnlineEntry] =
    try autoDisconnect(conn(new URL(HTTP, DOMAIN, "/portal_io/selfservice/bfonline/getlist"))
      .asInstanceOf[HttpURLConnection]) { conn =>
      setup(conn, AUTH_BASE.format(username, password))
      // TODO: Support CHAP encryption check
      val (_, json) = parseResult(conn)
      if ((json \ "total").asInstanceOf[JInt].values > 0) {
        val macs = NetworkMonitor.localMacs
        (json \ "rows").asInstanceOf[JArray].arr.map(obj => new OnlineEntry(obj.asInstanceOf[JObject]))
          .filter(obj => !macs.contains(obj.mac.toLowerCase))
      } else List.empty[OnlineEntry]
    } catch {
      case e: SocketTimeoutException =>
        val msg = e.getMessage
        app.showToast(if (TextUtils.isEmpty(msg)) app.getString(R.string.error_socket_timeout) else msg)
        List.empty[OnlineEntry]
      case e: ConnectException =>
        app.showToast(e.getMessage)
        List.empty[OnlineEntry]
      case e: UnknownHostException =>
        app.showToast(e.getMessage)
        List.empty[OnlineEntry]
      case e: Exception =>
        app.showToast(e.getMessage)
        e.printStackTrace
        List.empty[OnlineEntry]
    }
  @TargetApi(21)
  def queryOnline(network: Network) = queryOnlineCore(network.openConnection)
  def queryOnlineLegacy(network: NetworkInfo) = queryOnlineCore({
    NetworkMonitor.preferNetworkLegacy(network)
    _.openConnection
  })

  // Returns: 0 success, 1 error (retry), 2 fatal, and code
  private def loginCore(conn: URL => URLConnection): (Int, Int) = {
    Log.d(TAG, "Logging in...")
    try {
      val chapPassword = autoDisconnect(
        conn(new URL(HTTP, DOMAIN, "/portal_io/getchallenge")).asInstanceOf[HttpURLConnection]) { conn =>
        setup(conn)
        val (code, json) = parseResult(conn)
        if (code != 0) return null
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
        (AUTH_BASE + "&challenge=%s").format(username, passphrase.map("%02X".format(_)).mkString, challenge)
      }
      if (chapPassword == null) return (1, 0)
      autoDisconnect(conn(new URL(HTTP, DOMAIN, "/portal_io/login")).asInstanceOf[HttpURLConnection]) { conn =>
        setup(conn, chapPassword)
        conn.getResponseCode match {
          case 200 =>
            val (result, obj) = parseResult(conn, true)
            result match {
              case 3 => // need manual actions
                if ((obj \ "reply_msg").toString.startsWith("E011 ")) BalanceManager.cancelNotification() // no more balance
                (2, result)
              case 8 =>
                (2, result)
              case 1 | 6 =>
                (0, result)
              case _ =>
                (1, result)
            }
          case 502 =>
            app.showToast("无可用服务器资源!")
            (1, 0)
          case 503 =>
            app.showToast("请求太频繁,请稍后再试!")
            (1, 0)
          case code =>
            Log.w(TAG, "Unknown response code: " + code)
            (2, 0)
        }
      }
    } catch {
      case e: InvalidResponseException =>
        if (BuildConfig.DEBUG) Log.w(TAG, e.getMessage)
        (2, 0)
      case e: SocketException =>
        app.showToast(e.getMessage)
        e.printStackTrace
        (2, 0)
      case e: SocketTimeoutException =>
        val msg = e.getMessage
        app.showToast(if (TextUtils.isEmpty(msg)) app.getString(R.string.error_socket_timeout) else msg)
        (1, 0)
      case e: ConnectException =>
        app.showToast(e.getMessage)
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
    if (result == 0) {
      reportNetworkConnectivity(network, true)
      NetworkMonitor.instance.listener.onLogin(network, code)
      false
    } else result == 1
  }
  def loginLegacy(n: NetworkInfo = null) = {
    var network = n
    val (result, code) = loginCore({
      network = NetworkMonitor.preferNetworkLegacy(n)
      _.openConnection
    })
    if (result == 0) {
      NetworkMonitor.listenerLegacy.onLogin(network, code)
      false
    } else result == 1
  }
  def login: Boolean =
    if (NetworkMonitor.instance != null && app.boundConnectionsAvailable > 1) login(null) else loginLegacy()

  // AnyRef is a workaround for 4.x
  @SuppressLint(Array("NewApi"))
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

  @SuppressLint(Array("NewApi"))
  def logout = openPortalConnection[Unit]("/portal_io/logout") { (conn, network) =>
    setup(conn)
    if (parseResult(conn)._1 == 101) {
      if (app.boundConnectionsAvailable > 1 && network != null)
        app.handler.postDelayed(() => reportNetworkConnectivity(network.asInstanceOf[Network], false), 4000)
      NetworkMonitor.listenerLegacy.loginedNetwork = null
      if (NetworkMonitor.instance != null && NetworkMonitor.instance.listener != null)
        NetworkMonitor.instance.listener.loginedNetwork = null
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
