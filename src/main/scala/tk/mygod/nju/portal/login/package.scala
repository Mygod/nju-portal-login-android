package tk.mygod.nju.portal

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * @author Mygod
  */
package object login {
  var app: App = _
  val DEBUG = true

  val HTTP = "http"
  val PREF_NAME = "pref"
  val AUTO_CONNECT_ENABLED = "auth.autoConnect"
  val RELOGIN_DELAY = "auth.reloginDelay"

  def ThrowableFuture[T](f: => T) = Future(f) onFailure {
    case exc: Exception =>
      exc.printStackTrace
      app.showToast(exc.getMessage)
  }
}
