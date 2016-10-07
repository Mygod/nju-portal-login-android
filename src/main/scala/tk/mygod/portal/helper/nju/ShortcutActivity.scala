package tk.mygod.portal.helper.nju

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import be.mygod.app.ActivityPlus

/**
  * @author mygod
  */
final class ShortcutActivity extends ActivityPlus {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    getIntent.getAction match {
      case Intent.ACTION_CREATE_SHORTCUT => setResult(Activity.RESULT_OK, new Intent()
        .putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent[ShortcutActivity])
        .putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.login_logout))
        .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this, R.drawable.logo)))
      case _ => ThrowableFuture(if (NetworkMonitor.loginStatus == 0) PortalManager.login else PortalManager.logout)
    }
    finish
  }
}
