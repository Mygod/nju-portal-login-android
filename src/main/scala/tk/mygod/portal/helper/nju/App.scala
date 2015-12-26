package tk.mygod.portal.helper.nju

import android.accounts.AccountManager
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.provider.Settings
import android.widget.Toast
import com.j256.ormlite.logger.LocalLog
import tk.mygod.content.ContextPlus
import tk.mygod.os.Build

class App extends Application with ContextPlus {
  val handler = new Handler

  override def onCreate {
    app = this
    if (!DEBUG) System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "WARNING")
    super.onCreate
    systemService[AccountManager].addAccountExplicitly(NoticeManager.account, null, null)
    NoticeManager.updatePeriodicSync
  }

  /**
    * 0-3: Not available, permission missing, yes (revoke available), yes.
    */
  def boundConnectionsAvailable = if (Build.version >= 21) if (Build.version < 23) 3
    else if (Settings.System.canWrite(this)) 2 else 1 else 0

  lazy val cm = systemService[ConnectivityManager]
  lazy val pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
  lazy val editor = pref.edit

  def autoLoginEnabled = pref.getBoolean(AUTO_LOGIN_ENABLED, true)
  def reloginDelay = pref.getInt(RELOGIN_DELAY, 0)

  def skipConnect = pref.getBoolean("speed.skipConnect", false)
  def connectTimeout = pref.getInt("speed.connectTimeout", 4000)
  def loginTimeout = pref.getInt("speed.loginTimeout", 4000)

  def showToast(msg: String) = handler.post(() => makeToast(msg, Toast.LENGTH_SHORT).show)
}
