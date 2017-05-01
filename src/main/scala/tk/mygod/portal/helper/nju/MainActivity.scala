package tk.mygod.portal.helper.nju

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content._
import android.os.Bundle
import android.provider.Settings
import android.support.v14.preference.PreferenceFragment
import android.support.v14.preference.PreferenceFragment.OnPreferenceStartScreenCallback
import android.support.v4.app.ActivityOptionsCompat
import android.support.v7.app.AlertDialog
import android.support.v7.preference.PreferenceScreen
import android.text.TextUtils
import be.mygod.app.{CircularRevealActivity, ToolbarActivity}
import be.mygod.util.Conversions._

final class MainActivity extends ToolbarActivity with OnSharedPreferenceChangeListener
  with OnPreferenceStartScreenCallback {
  private lazy val serviceIntent = intent[NetworkMonitor]

  override protected def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    configureToolbar()
    if (!app.boundConnectionsAvailable) new AlertDialog.Builder(this)
      .setTitle(R.string.bound_connections_title)
      .setPositiveButton(android.R.string.ok, null)
      .setOnDismissListener(_ => startActivity(
        new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).setData("package:" + getPackageName)))
      .setMessage(R.string.bound_connections_message)
      .create().show()
    startNetworkMonitor()
    app.pref.registerOnSharedPreferenceChangeListener(this)
    if (TextUtils.isEmpty(PortalManager.username) || TextUtils.isEmpty(PortalManager.password))
      makeSnackbar(R.string.settings_account_missing).show()
  }

  override protected def onDestroy() {
    app.pref.unregisterOnSharedPreferenceChangeListener(this)
    super.onDestroy()
  }

  override def onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String): Unit = key match {
    case SERVICE_STATUS => if (app.serviceStatus > 0) {
      app.setEnabled[BootReceiver](true)
      startNetworkMonitor()
    } else {
      app.setEnabled[BootReceiver](false)
      stopService(serviceIntent)
    }
    case NoticeManager.SYNC_INTERVAL => NoticeManager.updatePeriodicSync()
    case _ => // ignore
  }

  def startNetworkMonitor(): Unit = if (app.serviceStatus > 0) startService(serviceIntent)

  def onPreferenceStartScreen(fragment: PreferenceFragment, screen: PreferenceScreen): Boolean = {
    startActivity(CircularRevealActivity.putLocation(intent[UsageActivity], getLocationOnScreen),
      ActivityOptionsCompat.makeSceneTransitionAnimation(this).toBundle)
    true
  }
}
