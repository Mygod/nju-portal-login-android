package tk.mygod.portal.helper

import java.text.DecimalFormat

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * @author Mygod
  */
package object nju {
  var app: App = _
  val DEBUG = false

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

  private val currencyFormat = new DecimalFormat("0.00")
  def formatCurrency(c: Int) = currencyFormat.format(c / 100.0)
}
