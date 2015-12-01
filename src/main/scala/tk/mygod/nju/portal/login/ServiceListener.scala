package tk.mygod.nju.portal.login

import android.content.{BroadcastReceiver, Context, Intent}
import android.net.{ConnectivityManager, NetworkInfo}
import android.util.Log
import tk.mygod.content.ContextPlus

object ServiceListener {
  private val TAG = "ServiceListener"
}

final class ServiceListener extends BroadcastReceiver with ContextPlus {
  import ServiceListener._

  override def onReceive(context: Context, intent: Intent) {
    if (App.DEBUG) Log.d(TAG, intent.getAction)
    intent.getAction match {
      //noinspection ScalaDeprecation
      case ConnectivityManager.CONNECTIVITY_ACTION =>
        val n = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO).asInstanceOf[NetworkInfo]
        if (PortalManager.cares(n.getType))
          if (n.isConnected) PortalManager.listenerLegacy.onAvailable(n) else PortalManager.listenerLegacy.onLost(n)
      case Intent.ACTION_BOOT_COMPLETED =>
        if (App.instance.autoConnectEnabled) context.startService(intentService[PortalManager])
    }
  }
}
