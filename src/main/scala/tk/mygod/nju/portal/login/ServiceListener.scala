package tk.mygod.nju.portal.login

import android.content.{BroadcastReceiver, Context, Intent}
import android.net.{ConnectivityManager, NetworkInfo}
import android.util.Log

object ServiceListener {
  private val TAG = "NetworkConditionsReceiver"
}

// TODO: disable tests for custom hotspots
final class ServiceListener extends BroadcastReceiver {
  import ServiceListener._

  override def onReceive(context: Context, intent: Intent) {
    if (App.DEBUG) Log.d(TAG, intent.getAction)
    intent.getAction match {
      //noinspection ScalaDeprecation
      case ConnectivityManager.CONNECTIVITY_ACTION =>
        if (App.instance.boundConnectionsAvailable < 2 && PortalManager.cares(intent
          .getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO).asInstanceOf[NetworkInfo].getType))
          context.startService(intent.setClass(context, classOf[PortalManager]))
          /*TODO: if (networkInfo.isConnected) {
            if (App.DEBUG)
              Log.d(TAG, "Network %s[%s] connected.".format(networkInfo.getTypeName, networkInfo.getSubtypeName))

          } else {
            if (App.DEBUG)
              Log.d(TAG, "Network %s[%s] disconnected.".format(networkInfo.getTypeName, networkInfo.getSubtypeName))
            if (PortalManager.running) context.startService(new Intent(context, classOf[PortalManager])
              .setAction(PortalManager.STOP).putExtra(ConnectivityManager.EXTRA_NETWORK_INFO, networkInfo))
          }*/
      case Intent.ACTION_BOOT_COMPLETED => context.startService(new Intent(context, classOf[PortalManager]))
    }
  }
}
