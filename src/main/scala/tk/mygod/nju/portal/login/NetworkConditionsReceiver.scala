package tk.mygod.nju.portal.login

import java.net.{URL, HttpURLConnection}

import android.content.{BroadcastReceiver, Context, Intent}
import android.net.{ConnectivityManager, NetworkInfo}
import android.os.SystemClock
import android.util.Log
import android.widget.Toast

import scala.collection.JavaConverters._

object NetworkConditionsReceiver {
  val TAG = "NetworkConditionsReceiver"
}

final class NetworkConditionsReceiver extends BroadcastReceiver {
  import NetworkConditionsReceiver._

  private def isNotMobile(network: Int) = network > 5 || network == ConnectivityManager.TYPE_WIFI

  override def onReceive(context: Context, intent: Intent) {
    if (App.DEBUG) {
      val bundle = intent.getExtras
      for (key <- bundle.keySet.asScala)
        Log.d(TAG, "%s: %s => %s".format(intent.getAction, key.toString, bundle.get(key).toString))
    }
    intent.getAction match {
      case ConnectivityManager.CONNECTIVITY_ACTION =>
        //noinspection ScalaDeprecation
        val networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO).asInstanceOf[NetworkInfo]
        val networkType = networkInfo.getType
        if (isNotMobile(networkType) && networkInfo.isConnected) {  // TODO: disable tests for custom hotspots
          if (App.DEBUG)
            Log.d(TAG, "Network connected: %s, %s".format(networkInfo.getTypeName, networkInfo.getSubtypeName))
          if (networkType != ConnectivityManager.TYPE_WIFI || App.instance.systemNetworkMonitorAvailable != 3) {
            if (App.DEBUG) Log.d(TAG, "Testing connection manually...")
            var conn: HttpURLConnection = null
            try {
              val url = new URL(App.http, "mygod.tk", "/generate_204")  // TODO: custom domain
              // TODO: complete
              var time = SystemClock.elapsedRealtime
              time = SystemClock.elapsedRealtime - time
            } catch {
              case e: Throwable => e.printStackTrace
            }
          }
          App.setTimeout(networkInfo)
        }
      case "android.net.conn.NETWORK_CONDITIONS_MEASURED" => if (!intent.getBooleanExtra("extra_is_captive_portal",
        false) && isNotMobile(intent.getIntExtra("extra_connectivity_type", ConnectivityManager.TYPE_MOBILE))) {
        // drop all captive portal and mobile connections
        App.clearTimeout
        if (App.testingNetwork != null && App.testingNetwork.getType == ConnectivityManager.TYPE_WIFI)
          Toast.makeText(context, "Network available. Time=%dms".format(intent.getLongExtra("extra_response_timestamp_ms",
            0) - intent.getLongExtra("extra_request_timestamp_ms", 0)), Toast.LENGTH_SHORT).show
      }
    }
  }
}
