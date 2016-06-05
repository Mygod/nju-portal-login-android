package tk.mygod.portal.helper.nju

import android.content.{BroadcastReceiver, Context, Intent}
import android.net.{ConnectivityManager, NetworkInfo}

final class NetworkMonitorListener extends BroadcastReceiver {
  override def onReceive(context: Context, intent: Intent) {
    //noinspection ScalaDeprecation
    val n = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO).asInstanceOf[NetworkInfo]
    if (NetworkMonitor.cares(n.getType) && n.isConnected)
      NetworkMonitor.listenerLegacy.onAvailable(n) else NetworkMonitor.listenerLegacy.onLost(n)
  }
}
