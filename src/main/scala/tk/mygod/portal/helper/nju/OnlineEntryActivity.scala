package tk.mygod.portal.helper.nju

import android.content.{BroadcastReceiver, IntentFilter}
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.text.method.LinkMovementMethod
import be.mygod.app.ToolbarActivity

object OnlineEntryActivity {
  final val ACTION_CANCEL = "tk.mygod.portal.helper.nju.OnlineEntryActivity.ACTION_CANCEL"
  final val ACTION_SHOW = "tk.mygod.portal.helper.nju.OnlineEntryActivity.ACTION_SHOW"
  final val ACTION_SHOW_LEGACY = "tk.mygod.portal.helper.nju.OnlineEntryActivity.ACTION_SHOW_LEGACY"
  final val EXTRA_MAC = "tk.mygod.portal.helper.nju.OnlineEntryActivity.EXTRA_MAC"
  final val EXTRA_NETWORK_ID = "tk.mygod.portal.helper.nju.OnlineEntryActivity.EXTRA_NETWORK_ID"
  final val EXTRA_NOTIFICATION_ID = "tk.mygod.portal.helper.nju.OnlineEntryActivity.EXTRA_NOTIFICATION_ID"
  final val EXTRA_TEXT = "tk.mygod.portal.helper.nju.OnlineEntryActivity.EXTRA_TEXT"
}

class OnlineEntryActivity extends ToolbarActivity with TypedFindView {
  import OnlineEntryActivity._

  private var legacy: Boolean = _

  private var receiverRegistered: Boolean = _
  private val receiver: BroadcastReceiver = (_, intent) =>
    if (intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1) == getIntent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)) finish()

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    val intent = getIntent
    intent.getAction match {
      case ACTION_SHOW =>
      case ACTION_SHOW_LEGACY => legacy = true
      case _ =>
        finish()
        return
    }
    LocalBroadcastManager.getInstance(this).registerReceiver(receiver, new IntentFilter(ACTION_CANCEL))
    receiverRegistered = true
    setContentView(R.layout.activity_online_entry)
    configureToolbar()
    val details = findView(TR.entryDetails)
    details.setText(intent.getCharSequenceExtra(EXTRA_TEXT))
    details.setMovementMethod(LinkMovementMethod.getInstance)
    findView(TR.signInButton).setOnClickListener(_ => {
      if (legacy) NetworkMonitor.listenerLegacy.doLogin(intent.getLongExtra(EXTRA_NETWORK_ID, -1))
      else if (NetworkMonitor.instance != null && NetworkMonitor.instance.listener != null)
        NetworkMonitor.instance.listener.doLogin(intent.getIntExtra(EXTRA_NETWORK_ID, -1))
      app.nm.cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1))
      finish()
    })
    findView(TR.ignoreMacButton).setOnClickListener(_ => {
      app.editor.putString(NetworkMonitor.LOCAL_MAC,
        (NetworkMonitor.localMacs + intent.getStringExtra(EXTRA_MAC)).mkString("\n")).apply()
      app.nm.cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1))
      finish()
    })
  }

  override protected def onDestroy() {
    if (receiverRegistered) LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    super.onDestroy()
  }
}
