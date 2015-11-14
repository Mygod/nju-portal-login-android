package tk.mygod.nju.portal.login

import android.content.{DialogInterface, Intent}
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.support.v7.app.AlertDialog
import android.util.Log
import eu.chainfire.libsuperuser.Shell.SU
import tk.mygod.app.ToolbarActivity

import scala.collection.JavaConverters._

object MainActivity {
  private val TAG = "MainActivity"

  private val systemId = "NJUPortalLogin"
  private val systemDir = "/system/priv-app/" + systemId
  private val systemPath = systemDir + "/" + systemId + ".apk"

  private val askedBindedConnection = "askedBindedConnection"
  private val askedNetworkMonitor = "askedNetworkMonitor"
}

final class MainActivity extends ToolbarActivity {
  import MainActivity._

  override protected def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    configureToolbar()
    testNetworkMonitor()
    testBindedConnections()
  }

  private def su(command: String) {
    if (App.DEBUG) Log.d(TAG, "Executing su: " + command)
    val result = SU.run("mount -o rw,remount /system && " + command + " && echo 1").asScala
    makeSnackbar(if (result != null && result.size == 1 && result.head == "1") R.string.su_success
      else if (result.isEmpty) R.string.su_fail else getString(R.string.su_fail_msg, result.mkString("\n"))).show
  }
  def testNetworkMonitor(requested: Boolean = false) = App.instance.systemNetworkMonitorAvailable match {
    case 2 => if (requested || !App.instance.pref.getBoolean(askedNetworkMonitor, false)) {
      new AlertDialog.Builder(this).setTitle(R.string.networkmonitor_install_title)
        .setPositiveButton(android.R.string.yes, ((dialog: DialogInterface, which: Int) => su(
          "mkdir %1$s && chmod 755 %1$s && mv %2$s %3$s".format(systemDir, getApplicationInfo.sourceDir, systemPath)))
            : DialogInterface.OnClickListener).setMessage(R.string.networkmonitor_install_message)
        .setNegativeButton(android.R.string.no, null).create.show
      App.instance.editor.putBoolean(askedNetworkMonitor, true).apply
      true
    } else false
    case 3 => if (requested) {
      startActivity(new Intent(Settings.ACTION_WIFI_IP_SETTINGS))
      true
    } else false
    case 4 => if (requested) {
      new AlertDialog.Builder(this).setTitle(R.string.networkmonitor_uninstall_title)
        .setMessage(R.string.networkmonitor_uninstall_message).setPositiveButton(android.R.string.yes,
          ((dialog: DialogInterface, which: Int) => su("rm -r " + systemDir)): DialogInterface.OnClickListener)
        .setNegativeButton(android.R.string.no, null).create.show
      true
    } else false
    case _ => false
  }

  private def manageWriteSettings(dialog: DialogInterface = null, which: Int = 0) = startActivity(
    new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).setData(Uri.parse("package:" + getPackageName)))
  def testBindedConnections(requested: Boolean = false) = App.instance.bindedConnectionsAvailable match {
    case 1 => if (requested || !App.instance.pref.getBoolean(askedBindedConnection, false)) {
      new AlertDialog.Builder(this).setTitle(R.string.binded_connections_title)
        .setPositiveButton(android.R.string.yes, manageWriteSettings: DialogInterface.OnClickListener)
        .setMessage(R.string.binded_connections_message).setNegativeButton(android.R.string.no, null).create.show
      App.instance.editor.putBoolean(askedBindedConnection, true)
      true
    } else false
    case 2 => if (requested) {
      manageWriteSettings()
      true
    } else false
    case _ => false
  }
}
