package tk.mygod.portal.helper.nju

import android.content.{BroadcastReceiver, Context, Intent}

/**
  * @author Mygod
  */
object IgnoreMacListener {
  final val ACTION_IGNORE = "tk.mygod.portal.helper.nju.IgnoreMacListener.ACTION_IGNORE"
  final val ACTION_IGNORE_LEGACY = "tk.mygod.portal.helper.nju.IgnoreMacListener.ACTION_IGNORE_LEGACY"
  final val EXTRA_NOTIFICATION_ID = "tk.mygod.portal.helper.nju.IgnoreMacListener.EXTRA_NOTIFICATION_ID"
  final val EXTRA_MAC = "tk.mygod.portal.helper.nju.IgnoreMacListener.EXTRA_MAC"
}

class IgnoreMacListener extends BroadcastReceiver {
  override def onReceive(context: Context, intent: Intent) {
    app.nm.cancel(intent.getIntExtra(IgnoreMacListener.EXTRA_NOTIFICATION_ID, -1))
    app.editor.putString(NetworkMonitor.LOCAL_MAC,
      (NetworkMonitor.localMacs + intent.getStringExtra(IgnoreMacListener.EXTRA_MAC)).mkString("\n")).apply
  }
}
