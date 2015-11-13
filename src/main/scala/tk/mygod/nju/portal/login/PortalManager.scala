package tk.mygod.nju.portal.login

import java.net.{HttpURLConnection, SocketTimeoutException, URL}

import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager.NetworkCallback
import android.net._
import android.os.SystemClock
import android.util.Log
import org.json4s.native.JsonMethods.{compact, parse, render}
import org.json4s.native.Serialization
import org.json4s.{JInt, JObject, JString, NoTypeHints}
import tk.mygod.concurrent.StoppableFuture
import tk.mygod.util.CloseUtils._
import tk.mygod.util.IOUtils

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//noinspection JavaAccessorMethodCalledAsEmptyParen
object PortalManager {
  private val TAG = "PortalManager"
  val START = "tk.mygod.nju.portal.login.PortalManager.START"
  val NETWORK_CONDITIONS_MEASURED = "android.net.conn.NETWORK_CONDITIONS_MEASURED"
  val STOP = "tk.mygod.nju.portal.login.PortalManager.STOP"

  private val http = "http"
  private val portalDomain = "p.nju.edu.cn"
  private val post = "POST"

  private val replyCode = "reply_code"
  private val replyMsg = "reply_msg"
  private val userInfo = "userinfo"

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
    val code = (json \ replyCode).asInstanceOf[JInt].values.toInt
    val info = json \ userInfo
    if (App.DEBUG) Log.d(TAG, info.getClass.getName + " - " + info.toString)
    info match {
      case obj: JObject =>
        App.instance.editor.putString(status, compact(render(info))).apply
        if (userInfoListener != null) App.handler.post(() => userInfoListener(obj))
      case _ =>
    }
    if (App.instance.pref.getBoolean("notifications.login", true))
      App.instance.showToast("#%d: %s".format(code, (json \ replyMsg).asInstanceOf[JString].values))
    code
  }

  private def networkTransportType(network: NetworkInfo) = network.getType match {
    case ConnectivityManager.TYPE_WIFI | ConnectivityManager.TYPE_WIMAX => NetworkCapabilities.TRANSPORT_WIFI
    case ConnectivityManager.TYPE_BLUETOOTH => NetworkCapabilities.TRANSPORT_BLUETOOTH
    case ConnectivityManager.TYPE_ETHERNET => NetworkCapabilities.TRANSPORT_ETHERNET
    case ConnectivityManager.TYPE_VPN => NetworkCapabilities.TRANSPORT_VPN
    case _ => NetworkCapabilities.TRANSPORT_CELLULAR  // should probably never hit
  }
  //noinspection ScalaDeprecation
  private def bindNetwork[T](network: NetworkInfo, callback: Network => T): Unit = if (network == null) {
    App.instance.connectivityManager.setNetworkPreference(ConnectivityManager.TYPE_WIFI)  // a random guess
    callback(null)
  } else network.synchronized {
    if (App.instance.bindedConnectionsAvailable > 1) {
      if (App.DEBUG) Log.d(TAG, "Binding to network with type: " + networkTransportType(network))
      App.instance.connectivityManager.requestNetwork(
        new NetworkRequest.Builder().addTransportType(networkTransportType(network)).build, new NetworkCallback {
          override def onAvailable(network: Network) = callback(network)
        })
    } else {
      App.instance.connectivityManager.setNetworkPreference(network.getType)
      if (App.DEBUG) Log.d(TAG, "Setting network preference: " + network.getType)
      callback(null)
    }
  }

  /**
    * Based on: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java#640
    */
  def login[T](network: NetworkInfo = null, onResult: Option[Int] => T = null) = bindNetwork(network, network => {
    if (App.DEBUG) Log.d(TAG, "Logging in...")
    try any2CloseAfterDisconnectable(() => {
      val url = new URL(http, portalDomain, "/portal_io/login")
      (if (network == null) url.openConnection else network.openConnection(url)).asInstanceOf[HttpURLConnection]
    }) closeAfter { conn =>
      conn.setInstanceFollowRedirects(false)
      conn.setConnectTimeout(App.instance.loginTimeout)
      conn.setReadTimeout(App.instance.loginTimeout)
      conn.setRequestMethod(post)
      conn.setUseCaches(false)
      conn.setDoOutput(true)
      (() => conn.getOutputStream).closeAfter(os => IOUtils.writeAllText(os, "username=%s&password=%s".format(
        App.instance.pref.getString("account.username", ""), App.instance.pref.getString("account.password", ""))))
      if (onResult != null) onResult(Some(processResult(IOUtils.readAllText(conn.getInputStream()))))
    } catch {
      case e: Exception =>
        App.instance.showToast(e.getMessage)
        e.printStackTrace
        if (onResult != null) onResult(None)
    }
  })

  def logout(network: NetworkInfo = null) = bindNetwork(network, network => {
    try {
      (() => {
        val url = new URL(http, portalDomain, "/portal_io/logout")
        (if (network == null) url.openConnection else network.openConnection(url)).asInstanceOf[HttpURLConnection]
      }) closeAfter { conn =>
        conn.setInstanceFollowRedirects(false)
        conn.setConnectTimeout(App.instance.loginTimeout)
        conn.setReadTimeout(App.instance.loginTimeout)
        conn.setRequestMethod(post)
        conn.setUseCaches(false)
        conn.setDoOutput(true)
        processResult(IOUtils.readAllText(conn.getInputStream()))
      }
    } catch {
      case e: Exception =>
        App.instance.showToast(e.getMessage)
        e.printStackTrace
    }
  })
}

final class PortalManager extends Service {
  import PortalManager._

  private def onNetworkAvailable(time: Long) = if (App.instance.pref.getBoolean("notifications.connection", true))
    App.instance.showToast(getString(R.string.toast_network_available, time))

  private final class NetworkTester(val networkInfo: NetworkInfo) extends StoppableFuture {
    @volatile var networkAvailable: Boolean = _

    def work =
      if (networkInfo.getType != ConnectivityManager.TYPE_WIFI || App.instance.systemNetworkMonitorAvailable != 3)
        bindNetwork(networkInfo, network => Future {
          if (App.DEBUG) Log.d(TAG, "Testing connection manually...")
          try any2CloseAfterDisconnectable(() => {
            val url = new URL(http, "mygod.tk", "/generate_204")  // TODO: custom domain
            (if (network == null) url.openConnection else network.openConnection(url))
              .asInstanceOf[HttpURLConnection]
          }) closeAfter { conn =>
            conn.setInstanceFollowRedirects(false)
            conn.setConnectTimeout(App.instance.connectTimeout)
            conn.setReadTimeout(App.instance.connectTimeout)
            conn.setUseCaches(false)
            val time = SystemClock.elapsedRealtime
            conn.getInputStream
            val code = conn.getResponseCode
            if (code == 204 || code == 200 && conn.getContentLength == 0)
              onNetworkAvailable(SystemClock.elapsedRealtime - time)
          } catch {
            case e: SocketTimeoutException =>
              login(networkInfo, onLoginResult)
            case e: Exception =>
              App.instance.showToast(e.getMessage)
              e.printStackTrace
              taskEnded
          }
        })
      else {
        Thread.sleep(App.instance.connectTimeout)
        if (isStopped) taskEnded else if (!networkAvailable) login(networkInfo, onLoginResult)
      }

    def onLoginResult(code: Option[Int]): Unit = if (code.contains(1) || code.contains(6) || isStopped) taskEnded else {
      Thread.sleep(App.instance.pref.getInt("speed.retryDelay", 4000))
      login(networkInfo, onLoginResult)
    }

    def taskEnded = if (isTesting(networkInfo)) {
      tester = null
      stopSelf
    }
  }

  private var tester: NetworkTester = _
  private def isTesting(info: NetworkInfo) = tester != null && info != null &&
    tester.networkInfo.getType == info.getType && tester.networkInfo.getSubtype == info.getSubtype

  def onBind(intent: Intent) = null
  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    if (App.DEBUG) {
      val bundle = intent.getExtras
      for (key <- bundle.keySet.asScala)
        Log.d(TAG, "%s: %s => %s".format(intent.getAction, key.toString, bundle.get(key).toString))
    }
    //noinspection ScalaDeprecation
    intent.getAction match {
      case START =>
        val info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO).asInstanceOf[NetworkInfo]
        if (tester != null) if (isTesting(info)) return Service.START_STICKY else tester.stop
        tester = new NetworkTester(info)
      case NETWORK_CONDITIONS_MEASURED =>
        if (tester == null) stopSelf else if (tester.networkInfo.getType == ConnectivityManager.TYPE_WIFI) {
          tester.networkAvailable = true
          onNetworkAvailable(intent.getLongExtra("extra_response_timestamp_ms", 0) -
            intent.getLongExtra("extra_request_timestamp_ms", 0))
        }
      case _ =>
        if (tester == null) stopSelf else if (isTesting(intent
          .getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO).asInstanceOf[NetworkInfo])) tester.stop
    }
    Service.START_STICKY
  }

  override def onCreate = {
    super.onCreate
    if (App.DEBUG) Log.d(TAG, "Service created.")
  }

  override def onDestroy = {
    super.onDestroy
    if (App.DEBUG) Log.d(TAG, "Service destroyed.")
  }
}
