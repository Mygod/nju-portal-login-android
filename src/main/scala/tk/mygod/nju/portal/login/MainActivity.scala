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
  final val TAG = "MainActivity"

  final val askedBindedConnection = "askedBindedConnection"
  final val askedNetworkMonitor = "askedNetworkMonitor"
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
    val result = SU.run(command + " && echo 1").asScala
    makeSnackbar(if (result.size == 1 && result.head == "1") "Operation completed. Reboot your device to apply changes."
      else if (result.isEmpty) "Operation failed." else "Operation failed: %s".format(result.mkString("\n"))).show
  }
  def testNetworkMonitor(requested: Boolean = false) = App.instance.systemNetworkMonitorAvailable match {
    case 2 => if (requested || !App.instance.pref.getBoolean(askedNetworkMonitor, false)) {
      new AlertDialog.Builder(this)
        .setTitle("Enable system NetworkMonitor?")
        .setMessage("System NetworkMonitor can be enabled to save battery via changing this app to a system privileged app. Do you want to do this now?")
        .setPositiveButton(android.R.string.yes, ((dialog: DialogInterface, which: Int) => su(
          "mount -o rw,remount /system && mkdir " + App.systemDir + " && chmod 755 " + App.systemDir + " && mv " +
          getApplicationInfo.sourceDir + ' ' + App.systemPath)): DialogInterface.OnClickListener)
        .setNegativeButton(android.R.string.no, null).create.show
      App.instance.editor.putBoolean(askedNetworkMonitor, true).apply
      true
    } else false
    case 3 => if (requested) {
      new AlertDialog.Builder(this).setTitle("Uninstall this app?")
        .setMessage("After doing this you might need to reboot then uninstall the app normally.")
        .setPositiveButton(android.R.string.yes,
          ((dialog: DialogInterface, which: Int) => su("rm " + App.systemDir)): DialogInterface.OnClickListener)
        .setNegativeButton(android.R.string.no, null).create.show
      true
    } else false
    case _ => false
  }

  private def manageWriteSettings(dialog: DialogInterface = null, which: Int = 0) = startActivity(
    new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).setData(Uri.parse("package:" + getPackageName)))
  def testBindedConnections(requested: Boolean = false) = App.instance.bindedConnectionsAvailable match {
    case 1 => if (requested || !App.instance.pref.getBoolean(askedBindedConnection, false)) {
      new AlertDialog.Builder(this)
        .setTitle("Enable binded connections?")
        .setMessage("Binded connections will enforce the connections to go on a specific network so that it wouldn't use any mobile data traffic.")
        .setPositiveButton(android.R.string.yes, manageWriteSettings: DialogInterface.OnClickListener)
        .setNegativeButton(android.R.string.no, null).create.show
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
