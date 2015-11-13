package tk.mygod.nju.portal.login

import java.net.{SocketTimeoutException, HttpURLConnection, URL}

import android.content.{BroadcastReceiver, Context, Intent}
import android.net.{ConnectivityManager, NetworkInfo}
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import tk.mygod.util.CloseUtils._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object NetworkConditionsReceiver {
  private val TAG = "NetworkConditionsReceiver"

  private def isNotMobile(network: Int) = network > 5 || network == ConnectivityManager.TYPE_WIFI

  private def networkAvailable(context: Context, time: Long) = Toast.makeText(context, "Network available. Time=%dms"
    .format(time),Toast.LENGTH_SHORT).show
}

final class NetworkConditionsReceiver extends BroadcastReceiver {
  import NetworkConditionsReceiver._

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
          if (networkType != ConnectivityManager.TYPE_WIFI || App.instance.systemNetworkMonitorAvailable != 3)
            App.bindNetwork(networkInfo, network => Future {
              if (App.DEBUG) Log.d(TAG, "Testing connection manually...")
              try any2CloseAfterDisconnectable(() => {
                val url = new URL(App.http, "mygod.tk", "/generate_204")
                (if (network == null) url.openConnection else network.openConnection(url))
                  .asInstanceOf[HttpURLConnection]
              }) closeAfter { conn =>
                conn.setInstanceFollowRedirects(false)
                conn.setConnectTimeout(4000)  // TODO: custom timeout for testing
                conn.setReadTimeout(4000)
                conn.setUseCaches(false)
                val time = SystemClock.elapsedRealtime
                conn.getInputStream
                val code = conn.getResponseCode
                if (code == 204 || code == 200 && conn.getContentLength == 0)
                  networkAvailable(context, SystemClock.elapsedRealtime - time)
              } catch {
                case e: SocketTimeoutException =>
                  PortalManager.login(true)
                case e: Throwable =>
                  Toast.makeText(App.instance, e.getMessage, Toast.LENGTH_SHORT).show
                  e.printStackTrace
              }
            }) else App.setTimeout(networkInfo)
        }
      case "android.net.conn.NETWORK_CONDITIONS_MEASURED" => if (!intent.getBooleanExtra("extra_is_captive_portal",
        false) && isNotMobile(intent.getIntExtra("extra_connectivity_type", ConnectivityManager.TYPE_MOBILE))) {
        // drop all captive portal and mobile connections
        App.clearTimeout
        if (App.testingNetwork != null && App.testingNetwork.getType == ConnectivityManager.TYPE_WIFI)
          networkAvailable(context, intent.getLongExtra("extra_response_timestamp_ms", 0) -
            intent.getLongExtra("extra_request_timestamp_ms", 0))
      }
    }
  }
}
