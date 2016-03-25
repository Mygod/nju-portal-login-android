package tk.mygod.portal.helper.nju

import android.content.{BroadcastReceiver, Context, Intent}
import android.net.{ConnectivityManager, NetworkInfo}
import android.util.Log

object NetworkMonitorListener {
  private val TAG = "NetworkMonitorListener"
}

final class NetworkMonitorListener extends BroadcastReceiver {
  import NetworkMonitorListener._

  override def onReceive(context: Context, intent: Intent) {
    if (DEBUG) Log.d(TAG, intent.getAction)
    intent.getAction match {
      //noinspection ScalaDeprecation
      case ConnectivityManager.CONNECTIVITY_ACTION =>
        val n = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO).asInstanceOf[NetworkInfo]
        if (NetworkMonitor.cares(n.getType))
          if (n.isConnected) NetworkMonitor.listenerLegacy.onAvailable(n) else NetworkMonitor.listenerLegacy.onLost(n)
      case Intent.ACTION_BOOT_COMPLETED =>
        if (app.serviceStatus > 0) context.startService(new Intent(context, classOf[NetworkMonitor]))
    }
  }
}
