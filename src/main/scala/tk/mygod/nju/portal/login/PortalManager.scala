package tk.mygod.nju.portal.login

import java.net.{HttpURLConnection, URL}

import android.util.Log
import android.widget.Toast
import org.json4s.{JString, JInt, JObject, NoTypeHints}
import org.json4s.native.JsonMethods.{compact, parse, render}
import org.json4s.native.Serialization
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
  def login(retry: Boolean = false): Unit = App.bindNetwork(App.testingNetwork, network => {
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
  })

  def logout = App.bindNetwork(App.testingNetwork, network => {
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
  })
}
