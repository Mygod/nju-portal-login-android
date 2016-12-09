package tk.mygod.portal.helper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * @author Mygod
  */
package object nju {
  var app: App = _

  final val HTTP = "http"
  final val PREF_NAME = "pref"
  final val SERVICE_STATUS = "auth.serviceStatus"

  def ThrowableFuture[T](f: => T): Unit = Future(f) onFailure {
    case e: PortalManager.NetworkUnavailableException =>
      app.showToast(app.getString(R.string.error_network_unavailable))
    case e: Exception =>
      e.printStackTrace()
      app.showToast(e.getMessage)
  }
}
