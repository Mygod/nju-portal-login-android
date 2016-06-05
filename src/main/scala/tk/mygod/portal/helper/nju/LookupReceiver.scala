package tk.mygod.portal.helper.nju

import android.content.{BroadcastReceiver, Context, Intent}
import android.net.Uri

/**
  * @author Mygod
  */
object LookupReceiver {
  final val EXTRA_ENTRY = "tk.mygod.portal.helper.nju.LookupReceiver.EXTRA_ENTRY"
  final val LOOKUP_IP = "tk.mygod.portal.helper.nju.LookupReceiver.LOOKUP_IP"
  final val LOOKUP_MAC = "tk.mygod.portal.helper.nju.LookupReceiver.LOOKUP_MAC"

  def getIpLookup(ip: CharSequence) = "https://ipinfo.io/" + ip
  def getMacLookup(mac: CharSequence) = "http://www.coffer.com/mac_find/?string=" + mac
}
class LookupReceiver extends BroadcastReceiver {
  import LookupReceiver._

  def onReceive(context: Context, intent: Intent) =
    context.startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(intent.getAction match {
      case LOOKUP_IP => getIpLookup(intent.getStringExtra(EXTRA_ENTRY))
      case LOOKUP_MAC => getMacLookup(intent.getStringExtra(EXTRA_ENTRY))
    })))
}
