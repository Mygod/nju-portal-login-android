package tk.mygod.nju.portal.login

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.provider.Settings
import android.widget.Toast
import tk.mygod.content.ContextPlus
import tk.mygod.os.Build

object App {
  var instance: App = _
  var handler: Handler = _
  val DEBUG = true

  val http = "http"
  val prefName = "pref"
  val autoConnectEnabledKey = "auth.autoConnect"
}

class App extends Application with ContextPlus {
  import App._

  override def onCreate {
    instance = this
    super.onCreate
    handler = new Handler
  }

  /**
    * 0-3: Not available, permission missing, yes (revoke available), yes.
    */
  def boundConnectionsAvailable = if (Build.version >= 21) if (Build.version < 23) 3
    else if (Settings.System.canWrite(this)) 2 else 1 else 0

  lazy val cm = systemService[ConnectivityManager]
  lazy val pref = getSharedPreferences(prefName, Context.MODE_PRIVATE)
  lazy val editor = pref.edit

  def autoConnectEnabled = pref.getBoolean(autoConnectEnabledKey, true)

  def skipConnect = pref.getBoolean("speed.skipConnect", false)
  def connectTimeout = pref.getInt("speed.connectTimeout", 4000)
  def loginTimeout = pref.getInt("speed.loginTimeout", 4000)

  def showToast(msg: String) = handler.post(() => Toast.makeText(this, msg, Toast.LENGTH_SHORT).show)
}
