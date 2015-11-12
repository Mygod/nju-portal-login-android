package tk.mygod.nju.portal.login

import java.net.{HttpURLConnection, URL}

import android.net.ConnectivityManager.NetworkCallback
import android.net.{ConnectivityManager, Network, NetworkCapabilities, NetworkRequest}
import android.util.Log
import android.widget.Toast
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.JsonMethods.{compact, render, parse}
import tk.mygod.util.CloseUtils._
import tk.mygod.util.IOUtils

//noinspection JavaAccessorMethodCalledAsEmptyParen
object PortalManager {
  private val TAG = "PortalManager"
  private implicit val formats = Serialization.formats(NoTypeHints)

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

  private def networkTransportType: Int = App.testingNetwork.getType match {
    case ConnectivityManager.TYPE_WIFI | ConnectivityManager.TYPE_WIMAX => NetworkCapabilities.TRANSPORT_WIFI
    case ConnectivityManager.TYPE_BLUETOOTH => NetworkCapabilities.TRANSPORT_BLUETOOTH
    case ConnectivityManager.TYPE_ETHERNET => NetworkCapabilities.TRANSPORT_ETHERNET
    case ConnectivityManager.TYPE_VPN => NetworkCapabilities.TRANSPORT_VPN
    case _ => NetworkCapabilities.TRANSPORT_CELLULAR  // should probably never hit
  }

  //noinspection ScalaDeprecation
  private def bindNetwork[T](callback: Network => T) = if (App.testingNetwork == null) {
    App.instance.connectivityManager.setNetworkPreference(ConnectivityManager.TYPE_WIFI)  // a random guess
    callback(null)
  } else App.testingNetwork.synchronized {
    if (App.instance.bindedConnectionsAvailable > 1) {
      if (App.DEBUG) Log.d(TAG, "Binding to network with type: " + networkTransportType)
      App.instance.connectivityManager.requestNetwork(
        new NetworkRequest.Builder().addTransportType(networkTransportType).build, new NetworkCallback {
          override def onAvailable(network: Network) = callback(network)
        })
    } else {
      App.instance.connectivityManager.setNetworkPreference(App.testingNetwork.getType)
      if (App.DEBUG) Log.d(TAG, "Setting network preference: " + App.testingNetwork.getType)
      callback(null)
    }
  }

  private def processResult(resultStr: String) = {
    if (App.DEBUG) Log.d(TAG, resultStr)
    val json = parse(resultStr)
    val code = (json \ replyCode).asInstanceOf[JInt].values.toInt
    val info = json \ userInfo
    if (App.DEBUG) Log.d(TAG, info.getClass.getName + " - " + info.toString)
    info match {
      case obj: JObject =>
        App.instance.editor.putString(status, compact(render(info))).apply
        if (userInfoListener != null) userInfoListener(obj)
      case _ =>
    }
    // TODO: disable toast option (quiet mode)
    Toast.makeText(App.instance, "#%d: %s".format(code, (json \ replyMsg).asInstanceOf[JString].values),
      Toast.LENGTH_SHORT).show
    code
  }

  /**
    * Based on: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java#640
    */
  def login(retry: Boolean = false): Unit = bindNetwork { network =>
    if (App.DEBUG) Log.d(TAG, "Logging in...")
    try any2CloseAfterDisconnectable(() => {
      val url = new URL(App.http, portalDomain, "/portal_io/login")
      (if (network == null) url.openConnection else network.openConnection(url)).asInstanceOf[HttpURLConnection]
    }) closeAfter { conn =>
      conn.setInstanceFollowRedirects(false)
      conn.setConnectTimeout(4000) // TODO: custom timeout for login/logout
      conn.setReadTimeout(4000)
      conn.setRequestMethod(post)
      conn.setUseCaches(false)
      conn.setDoOutput(true)
      (() => conn.getOutputStream).closeAfter(os => IOUtils.writeAllText(os, "username=%s&password=%s".format(
        App.instance.pref.getString("account.username", ""), App.instance.pref.getString("account.password", ""))))
      if (processResult(IOUtils.readAllText(conn.getInputStream())) == 1) return
    } catch {
      case e: Throwable =>
        Toast.makeText(App.instance, e.getMessage, Toast.LENGTH_SHORT).show
        e.printStackTrace
    }
    if (retry) App.setTimeout()
  }

  def logout = bindNetwork { network =>
    try {
      (() => {
        val url = new URL(App.http, portalDomain, "/portal_io/logout")
        (if (network == null) url.openConnection else network.openConnection(url)).asInstanceOf[HttpURLConnection]
      }) closeAfter { conn =>
        conn.setInstanceFollowRedirects(false)
        conn.setConnectTimeout(4000)  // TODO: custom timeout for login/logout
        conn.setReadTimeout(4000)
        conn.setRequestMethod(post)
        conn.setUseCaches(false)
        conn.setDoOutput(true)
        processResult(IOUtils.readAllText(conn.getInputStream()))
      }
    } catch {
      case e: Throwable =>
        Toast.makeText(App.instance, e.getMessage, Toast.LENGTH_SHORT).show
        e.printStackTrace
    }
  }
}
