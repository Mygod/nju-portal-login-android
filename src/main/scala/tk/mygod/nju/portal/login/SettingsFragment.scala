package tk.mygod.nju.portal.login

import java.net.InetAddress
import java.text.{DateFormat, DecimalFormat}
import java.util.Date

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.preference.Preference
import android.util.Log
import org.json4s.JObject
import tk.mygod.preference.{EditTextPreference, EditTextPreferenceDialogFragment, PreferenceFragmentPlus}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SettingsFragment {
  private val TAG = "SettingsFragment"
}

final class SettingsFragment extends PreferenceFragmentPlus {
  import SettingsFragment._

  private lazy val activity = getActivity.asInstanceOf[MainActivity]

  def onCreatePreferences(savedInstanceState: Bundle, rootKey: String) {
    getPreferenceManager.setSharedPreferencesName(App.prefName)
    addPreferencesFromResource(R.xml.settings)

    findPreference(App.autoConnectEnabledKey)
      .setOnPreferenceChangeListener((preference: Preference, newValue: Any) => {
        App.instance.autoConnectEnabled(newValue.asInstanceOf[Boolean])
        true
      })

    var preference = findPreference("autoConnect.useSystemNetworkMonitor")
    preference.setEnabled(App.instance.systemNetworkMonitorAvailable >= 2)
    preference.setOnPreferenceClickListener((preference: Preference) => activity.testNetworkMonitor(true))
    preference.setSummary(getString(R.string.networkmonitor_summary) +
      getString(App.instance.systemNetworkMonitorAvailable match {
        case 0 => R.string.networkmonitor_summary_na
        case 1 => R.string.networkmonitor_summary_no_root
        case 2 => R.string.networkmonitor_summary_priv_app
        case 3 => R.string.networkmonitor_summary_enabled
      }))

    preference = findPreference("autoConnect.useBindedConnections")
    val available = App.instance.bindedConnectionsAvailable
    preference.setEnabled(available == 1 || available == 2)
    preference.setOnPreferenceClickListener((preference: Preference) => activity.testBindedConnections(true))
    preference.setSummary(getString(R.string.binded_connections_summary) +
      getString(App.instance.bindedConnectionsAvailable match {
        case 0 => R.string.binded_connections_summary_na
        case 1 => R.string.binded_connections_summary_permission_missing
        case 2 => R.string.binded_connections_summary_enabled_revokable
        case 3 => R.string.binded_connections_summary_enabled
      }))

    findPreference("status.login").setOnPreferenceClickListener((preference: Preference) => {
      Future(PortalManager.login())
      true
    })
    findPreference("status.logout").setOnPreferenceClickListener((preference: Preference) => {
      Future(PortalManager.logout())
      true
    })
    PortalManager.setUserInfoListener(userInfoUpdated)

    findPreference("misc.support").setOnPreferenceClickListener((preference: Preference) => {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Mygod/nju-portal-login-android/wiki")))
      true
    })
  }

  override def onDisplayPreferenceDialog(preference: Preference) =
    if (preference.isInstanceOf[EditTextPreference])
      displayPreferenceDialog(new EditTextPreferenceDialogFragment(preference.getKey))
    else super.onDisplayPreferenceDialog(preference)

  override def onDestroy {
    PortalManager.setUserInfoListener(null)
    super.onDestroy
  }

  def userInfoUpdated(info: JObject) = for ((key, value) <- info.values) {
    val preference = findPreference("status." + key)
    if (preference == null) Log.e(TAG, "Unknown key in user_info: " + key) else preference.setSummary(key match {
      case "acctstarttime" => DateFormat.getDateTimeInstance.format(new Date(value.asInstanceOf[BigInt].toLong * 1000))
      case "balance" => new DecimalFormat("0.00").format(value.asInstanceOf[BigInt].toInt / 100.0)
      case "useripv4" =>
        val bytes = value.asInstanceOf[BigInt].toInt
        InetAddress.getByAddress(Array[Byte]((bytes >>> 24 & 0xFF).toByte, (bytes >>> 16 & 0xFF).toByte,
          (bytes >>> 8 & 0xFF).toByte, (bytes & 0xFF).toByte)).getHostAddress
      case _ => value.toString
    })
  }
}
