package tk.mygod.portal.helper.nju

import java.io.IOException
import java.net._
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date

import android.app.Notification
import android.content.Intent
import android.net.Network
import android.support.v4.app.NotificationCompat
import android.text.style.URLSpan
import android.text.{SpannableStringBuilder, Spanned, TextUtils}
import android.util.Log
import be.mygod.util.CloseUtils._
import be.mygod.util.Conversions._
import be.mygod.util.IOUtils
import org.json.{JSONException, JSONObject}
import tk.mygod.portal.helper.nju.database.Notice

import scala.collection.mutable
import scala.util.Random

/**
  * Portal manager. Supports:
  *   Desktop v = 201703211016
  *   Mobile v = 201609011722
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
  case class BoundConnectionPermissionException() extends SecurityException("Need permission for bound connections!")
    { }
  case class NetworkUnavailableException() extends IOException { }
  case class InvalidResponseException(url: URL, response: String)
    extends IOException("Invalid response from: %s\n%s".format(url, response)) { }
  class UnexpectedResponseCodeException(conn: HttpURLConnection) extends IOException {
    val code: Int = conn.getResponseCode
    val url: URL = conn.getURL
    //noinspection JavaAccessorMethodCalledAsEmptyParen
    val response: String = autoClose(conn.getErrorStream())(IOUtils.readAllText)

    override def getMessage: String = "Unexpected response code %d from: %s\n%s".format(code, url, response)

    def handle(): Boolean = {
      Log.w(TAG, getMessage)
      code match {
        case 502 =>
          app.showToast("无可用服务器资源!")
          true
        case 503 =>
          app.showToast("请求太频繁,请稍后再试!")
          true
        case _ => false
      }
    }
  }

  var currentUsername: String = _
  def username: String = app.pref.getString("account.username", "")
  def password: String = app.pref.getString("account.password", "")

  private val ipv4Matcher = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$".r
  def isValidHost(host: String): Boolean = host != null && (DOMAIN.equalsIgnoreCase(host) ||
    ipv4Matcher.findFirstIn(host).nonEmpty && host.startsWith("210.28.129.") || host.startsWith("219.219.114."))

  private var userInfoListener: JSONObject => Any = _
  def setUserInfoListener(listener: JSONObject => Any) {
    userInfoListener = listener
    if (listener != null) getUserInfo match {
      case Some(info) => listener(info)
      case _ =>
    }
  }
  def getUserInfo: Option[JSONObject] = {
    val info = app.pref.getString(STATUS, "")
    if (info.isEmpty) None else Some(new JSONObject(info))
  }
  def updateUserInfo(info: JSONObject) {
    app.editor.putString(STATUS, info.toString).apply()
    if (userInfoListener != null) {
      currentUsername = info.getString("username")
      app.handler.post(userInfoListener(info))
    }
  }

  private def parseResult(conn: HttpURLConnection, login: Boolean = false) = {
    if (conn.getResponseCode >= 400) throw new UnexpectedResponseCodeException(conn)
    //noinspection JavaAccessorMethodCalledAsEmptyParen
    val resultStr = autoClose(conn.getInputStream())(IOUtils.readAllText)
    if (BuildConfig.DEBUG) Log.v(TAG, resultStr)
    val json = try new JSONObject(resultStr) catch {
      case _: JSONException => throw InvalidResponseException(conn.getURL, resultStr)
    }
    val code = json.optInt("reply_code")  // assuming #0: 操作成功 for missing results
    json.optJSONObject("userinfo") match {
      case null =>
      case info =>
        updateUserInfo(info)
        if (login && code == 1) app.handler.postDelayed(() => BalanceManager.check(info), 2000) // first login
        else BalanceManager.check(info)
    }
    if (code != 0 && code != 2 && code != 9 &&
      (code != 1 && code != 6 && code != 101 || app.pref.getBoolean("notifications.login", true)))
      app.showToast(json.optString("reply_msg"))
    (code, json)
  }

  def parseTimeString(value: Long): String =
    new SimpleDateFormat(app.getString(R.string.date_format_milliseconds)).format(new Date(value / 10))
  def parseIpv4(bytes: Int): String = InetAddress.getByAddress(Array[Byte]((bytes >>> 24 & 0xFF).toByte,
    (bytes >>> 16 & 0xFF).toByte, (bytes >>> 8 & 0xFF).toByte, (bytes & 0xFF).toByte)).getHostAddress

  /**
    * Based on: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java#640
    *
    * @return 0 for success, -1 for failure, 2 for login required
    */
  def testConnection(network: Network): Int =
    try {
      val conn = network.openConnection(new URL(HTTP, DOMAIN, "/portal_io/getinfo")).asInstanceOf[HttpURLConnection]
      setup(conn)
      val (code, _) = parseResult(conn)
      Log.d(TAG, "Testing connection finished with code %d.".format(code))
      code // 2: 无用户portal信息
    } catch {
      case e: InvalidResponseException =>
        if (BuildConfig.DEBUG) Log.w(TAG, e.getMessage)
        -1
      case e: UnexpectedResponseCodeException =>
        e.handle()
        -1
      case e: SocketTimeoutException =>
        Log.w(TAG, e.getMessage match {
          case "" | null => "SocketTimeoutException"
          case msg => msg
        })
        -1
      case e: IOException =>
        e.printStackTrace()
        -1
      case e: Exception =>
        app.showToast(e.getMessage)
        e.printStackTrace()
        -1
    }

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

  class OnlineEntry(obj: JSONObject) {
    val mac: String = obj.getString("mac")
    val ipv4: String = parseIpv4(obj.getInt("user_ipv4"))
    val ipv6: String = obj.getString("user_ipv6")
    val ipv6Valid: Boolean = !TextUtils.isEmpty(ipv6) && ipv6 != "::"
    def makeNotification(contentIntent: Intent): Notification = {
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
          case "%2$s" => summary.append(obj.optString("area_name", "未知区域"))
          case "%3$s" => summary.append(parseTimeString(obj.getString(BalanceManager.KEY_ACTIVITY_START_TIME).toLong))
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
      builder.setPublicVersion(builder.build).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      new NotificationCompat.BigTextStyle(builder.setContentText(summary)).bigText(summary).build
    }
  }
  def queryOnline(network: Network): IndexedSeq[OnlineEntry] =
    try {
      val conn = network.openConnection(new URL(HTTP, DOMAIN, "/portal_io/selfservice/bfonline/getlist"))
        .asInstanceOf[HttpURLConnection]
      setup(conn, AUTH_BASE.format(username, password))
      // TODO: Support CHAP encryption check
      val (_, json) = parseResult(conn)
      if (json.getInt("total") > 0) {
        val macs = NetworkMonitor.localMacs
        val rows = json.getJSONArray("rows")
        (0 until rows.length).map(i => new OnlineEntry(rows.get(i).asInstanceOf[JSONObject]))
          .filter(obj => !macs.contains(obj.mac.toLowerCase))
      } else IndexedSeq.empty[OnlineEntry]
    } catch {
      case e: SocketTimeoutException =>
        val msg = e.getMessage
        app.showToast(if (TextUtils.isEmpty(msg)) app.getString(R.string.error_socket_timeout) else msg)
        IndexedSeq.empty[OnlineEntry]
      case e: IOException =>
        app.showToast(e.getMessage)
        IndexedSeq.empty[OnlineEntry]
      case e: Exception =>
        app.showToast(e.getMessage)
        e.printStackTrace()
        IndexedSeq.empty[OnlineEntry]
    }

  // Returns: -1 retry, 0 success, 1 error (retry), 2 fatal, and code
  def login(n: Network): Int = {
    val network = if (n == null) NetworkMonitor.instance.listener.preferredNetwork else n
    if (network == null) throw new NetworkUnavailableException
    Log.d(TAG, "Logging in...")
    try {
      var conn = network.openConnection(new URL(HTTP, DOMAIN, "/portal_io/getchallenge"))
        .asInstanceOf[HttpURLConnection]
      setup(conn)
      val (code, json) = parseResult(conn)
      if (code != 0) return -1  // TODO: what to do after getchallenge failed?
      val challenge = json.getString("challenge")
      val passphrase = new Array[Byte](17)
      passphrase(0) = Random.nextInt.toByte
      val passphraseRaw = new mutable.ArrayBuffer[Byte]
      passphraseRaw += passphrase(0)
      passphraseRaw ++= password.getBytes
      passphraseRaw ++= challenge.sliding(2, 2).map(Integer.parseInt(_, 16).toByte)
      val digest = MessageDigest.getInstance("MD5")
      digest.update(passphraseRaw.toArray)
      digest.digest(passphrase, 1, 16)
      conn = network.openConnection(new URL(HTTP, DOMAIN, "/portal_io/login"))
        .asInstanceOf[HttpURLConnection]
      setup(conn, (AUTH_BASE + "&challenge=%s").format(username, passphrase.map("%02X".format(_)).mkString, challenge))
      val (result, obj) = parseResult(conn, login = true)
      result match {
        case 3 => // need manual actions
          obj.getString("reply_msg").substring(0, 4) match {
            case "E011" => BalanceManager.cancelNotification()  // no more balance
            case _ =>
          }
          2
        case 8 => 2
        case 1 | 6 =>
          app.reportNetworkConnectivity(network, hasConnectivity = true)
          NetworkMonitor.instance.listener.onLogin(network, code)
          0
        case 253 => -1  // processing request
        case _ => 1
      }
    } catch {
      case e: InvalidResponseException =>
        if (BuildConfig.DEBUG) Log.w(TAG, e.getMessage)
        2
      case e: UnexpectedResponseCodeException => if (e.handle()) 1 else 2
      case e: SocketTimeoutException =>
        val msg = e.getMessage
        app.showToast(if (TextUtils.isEmpty(msg)) app.getString(R.string.error_socket_timeout) else msg)
        1
      case e: IOException =>
        app.showToast(e.getMessage)
        1
      case e: Exception =>
        app.showToast(e.getMessage)
        e.printStackTrace()
        1
    }
  }
  def login(): Int = if (NetworkMonitor.instance != null && app.boundConnectionsAvailable) login(null)
    else throw BoundConnectionPermissionException()

  def openPortalConnection(file: String, explicit: Boolean = true) = try {
    val url = new URL(HTTP, DOMAIN, file)
    if (NetworkMonitor.instance == null || !app.boundConnectionsAvailable)
      throw BoundConnectionPermissionException()
    val network = NetworkMonitor.instance.listener.preferredNetwork
    if (network == null) throw new NetworkUnavailableException
    Some(network.openConnection(url).asInstanceOf[HttpURLConnection], network)
  } catch {
    case _: NetworkUnavailableException =>
      if (explicit) app.showToast(app.getString(R.string.error_network_unavailable))
      None
    case e: SocketTimeoutException =>
      val msg = e.getMessage
      app.showToast(if (TextUtils.isEmpty(msg)) app.getString(R.string.error_socket_timeout) else msg)
      None
    case e: IOException =>
      app.showToast(e.getMessage)
      None
    case e: Exception =>
      app.showToast(e.getMessage)
      e.printStackTrace()
      None
  }

  def logout(): Boolean = openPortalConnection("/portal_io/logout") match {
    case Some((conn, network)) => try {
      setup(conn)
      if (parseResult(conn)._1 == 101) {
        if (app.boundConnectionsAvailable && network != null) app.handler.postDelayed(() =>
          app.reportNetworkConnectivity(network, hasConnectivity = false), 4000)
        if (NetworkMonitor.instance != null && NetworkMonitor.instance.listener != null)
          NetworkMonitor.instance.listener.loginedNetwork = null
        true
      } else false
    } catch {
      case e: Exception =>
        e.printStackTrace()
        app.showToast(e.getMessage)
        false
    }
    case None => false
  }

  def queryNotice(explicit: Boolean = true): Option[IndexedSeq[Notice]] =
    openPortalConnection("/portal_io/proxy/notice", explicit) match {
      case Some((conn, _)) => try {
        setup(conn)
        parseResult(conn)._2.optJSONArray("notice") match {
          case null => Some(IndexedSeq.empty)
          case notices => Some((0 until notices.length).map(i => new Notice(notices.getJSONObject(i))))
        }
      } catch {
        case e: Exception =>
          e.printStackTrace()
          app.showToast(e.getMessage)
          None
      }
      case None => None
    }

  /**
    * Warning: This method throws exception.
    *
    * @return Result if successful, else None.
    */
  def queryVolume: Option[JSONObject] = openPortalConnection("/portal_io/selfservice/volume/getlist") match {
    case Some((conn, _)) =>
      setup(conn)
      val (code, json) = parseResult(conn)
      if (code == 0) {
        val total = json.getInt("total")
        if (total != 1) throw new Exception("total = " + total)
        Some(json.getJSONArray("rows").getJSONObject(0))
      } else None
    case None => None
  }
}
