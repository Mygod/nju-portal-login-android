package tk.mygod.nju.portal.login

import android.content.{BroadcastReceiver, Context, Intent}
import android.net.{ConnectivityManager, NetworkInfo}
import android.util.Log

object ServiceListener {
  private val TAG = "ServiceListener"
}

final class ServiceListener extends BroadcastReceiver {
  import ServiceListener._

  override def onReceive(context: Context, intent: Intent) {
    if (App.DEBUG) Log.d(TAG, intent.getAction)
    intent.getAction match {
      //noinspection ScalaDeprecation
      case ConnectivityManager.CONNECTIVITY_ACTION => if (App.instance.autoConnectEnabled &&
        App.instance.boundConnectionsAvailable < 2 && PortalManager.cares(intent.getParcelableExtra(
          ConnectivityManager.EXTRA_NETWORK_INFO).asInstanceOf[NetworkInfo].getType)) {
        val n = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO).asInstanceOf[NetworkInfo]
        if (n.isConnected) PortalManager.listenerLegacy.onAvailable(n) else PortalManager.listenerLegacy.onLost(n)
      }
      case Intent.ACTION_BOOT_COMPLETED =>
        if (App.instance.autoConnectEnabled) context.startService(new Intent(context, classOf[PortalManager]))
    }
  }
}
