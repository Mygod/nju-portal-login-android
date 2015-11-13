package tk.mygod.nju.portal.login

import android.content.{BroadcastReceiver, Context, Intent}
import android.net.{ConnectivityManager, NetworkInfo}
import android.util.Log

object NetworkConditionsReceiver {
  private val TAG = "NetworkConditionsReceiver"

  private def isNotMobile(network: Int) = network > 5 || network == ConnectivityManager.TYPE_WIFI
}

final class NetworkConditionsReceiver extends BroadcastReceiver {
  import NetworkConditionsReceiver._

  override def onReceive(context: Context, intent: Intent) {
    intent.getAction match {
      //noinspection ScalaDeprecation
      case ConnectivityManager.CONNECTIVITY_ACTION =>
        val networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO).asInstanceOf[NetworkInfo]
        if (isNotMobile(networkInfo.getType)) // TODO: disable tests for custom hotspots
          if (networkInfo.isConnected) {
            if (App.DEBUG)
              Log.d(TAG, "Network %s[%s] connected.".format(networkInfo.getTypeName, networkInfo.getSubtypeName))
            context.startService(new Intent(context, classOf[PortalManager]).setAction(PortalManager.START)
              .putExtra(ConnectivityManager.EXTRA_NETWORK_INFO, networkInfo))
          } else {
            if (App.DEBUG)
              Log.d(TAG, "Network %s[%s] disconnected.".format(networkInfo.getTypeName, networkInfo.getSubtypeName))
            context.startService(new Intent(context, classOf[PortalManager]).setAction(PortalManager.STOP)
              .putExtra(ConnectivityManager.EXTRA_NETWORK_INFO, networkInfo))
          }
      case "android.net.conn.NETWORK_CONDITIONS_MEASURED" => if (!intent.getBooleanExtra("extra_is_captive_portal",
        false) && isNotMobile(intent.getIntExtra("extra_connectivity_type", ConnectivityManager.TYPE_MOBILE))) {
        // drop all captive portal and mobile connections
        intent.setClass(context, classOf[PortalManager])
        context.startService(intent)  // redirect
      }
    }
  }
}
