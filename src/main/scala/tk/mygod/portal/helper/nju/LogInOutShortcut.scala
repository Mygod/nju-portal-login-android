package tk.mygod.portal.helper.nju

import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Bundle
import be.mygod.app.ActivityPlus
import be.mygod.os.Build

object LogInOutShortcut {
  def reportUsed(): Unit =
    if (Build.version >= 25) app.getSystemService(classOf[ShortcutManager]).reportShortcutUsed("login_logout")
}

final class LogInOutShortcut extends ActivityPlus {
  import LogInOutShortcut._

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    getIntent.getAction match {
      case Intent.ACTION_CREATE_SHORTCUT => setResult(Activity.RESULT_OK, new Intent()
        .putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent[LogInOutShortcut])
        .putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.login_logout))
        .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
          Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher)))
      case _ =>
        ThrowableFuture(if (NetworkMonitor.loginStatus == 0) PortalManager.login else PortalManager.logout)
        reportUsed()
    }
    finish()
  }
}
