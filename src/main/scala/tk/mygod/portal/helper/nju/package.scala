package tk.mygod.portal.helper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * @author Mygod
  */
package object nju {
  var app: App = _
  val DEBUG = true

  val HTTP = "http"
  val PREF_NAME = "pref"
  val AUTO_LOGIN_ENABLED = "auth.autoLogin"
  val RELOGIN_DELAY = "auth.reloginDelay"

  def ThrowableFuture[T](f: => T) = Future(f) onFailure {
    case e: PortalManager.NetworkUnavailableException =>
      app.showToast(app.getString(R.string.error_network_unavailable))
    case e: Exception =>
      e.printStackTrace
      app.showToast(e.getMessage)
  }
}
