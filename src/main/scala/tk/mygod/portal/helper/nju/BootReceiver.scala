package tk.mygod.portal.helper.nju

import android.content.{BroadcastReceiver, Context, Intent}

/**
  * @author Mygod
  */
class BootReceiver extends BroadcastReceiver {
  def onReceive(context: Context, intent: Intent): Unit =
    if (app.serviceStatus > 0) context.startService(new Intent(context, classOf[NetworkMonitor]))
    else app.setEnabled[BootReceiver](false)
}
