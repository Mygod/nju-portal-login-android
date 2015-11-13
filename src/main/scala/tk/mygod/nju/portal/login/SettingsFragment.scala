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
import tk.mygod.net.UpdateManager
import tk.mygod.preference._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SettingsFragment {
  private val TAG = "SettingsFragment"
}

final class SettingsFragment extends PreferenceFragmentPlus {
  import SettingsFragment._

  private lazy val activity = getActivity.asInstanceOf[MainActivity]
  private lazy val useSystemNetworkMonitor = findPreference("autoConnect.useSystemNetworkMonitor")
  private lazy val useBindedConnections = findPreference("autoConnect.useBindedConnections")

  def onCreatePreferences(savedInstanceState: Bundle, rootKey: String) {
    getPreferenceManager.setSharedPreferencesName(App.prefName)
    addPreferencesFromResource(R.xml.settings)

    findPreference(App.autoConnectEnabledKey)
      .setOnPreferenceChangeListener((preference: Preference, newValue: Any) => {
        App.instance.autoConnectEnabled(newValue.asInstanceOf[Boolean])
        true
      })

    findPreference("status.login").setOnPreferenceClickListener((preference: Preference) => {
      Future(PortalManager.login())
      true
    })
    findPreference("status.logout").setOnPreferenceClickListener((preference: Preference) => {
      Future(PortalManager.logout())
      true
    })
    PortalManager.setUserInfoListener(userInfoUpdated)
    findPreference("status.username").setOnPreferenceClickListener((preference: Preference) => {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://p.nju.edu.cn")))
      true
    })
    findPreference("status.fullname").setOnPreferenceClickListener(humorous)
    findPreference("status.service_name").setOnPreferenceClickListener(humorous)
    findPreference("status.area_name").setOnPreferenceClickListener(humorous)
    findPreference("status.balance").setOnPreferenceClickListener(humorous)

    findPreference("misc.update").setOnPreferenceClickListener((preference: Preference) => {
      UpdateManager.check(activity, "https://github.com/Mygod/nju-portal-login-android/releases", App.handler)
      true
    })
    findPreference("misc.support").setOnPreferenceClickListener((preference: Preference) => {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Mygod/nju-portal-login-android/wiki")))
      true
    })
  }

  override def onResume {
    super.onResume

    var available = App.instance.systemNetworkMonitorAvailable
    useSystemNetworkMonitor.setEnabled(available >= 2)
    useSystemNetworkMonitor.setOnPreferenceClickListener((preference: Preference) => activity.testNetworkMonitor(true))
    useSystemNetworkMonitor.setSummary(getString(R.string.networkmonitor_summary) + getString(available match {
      case 0 => R.string.networkmonitor_summary_na
      case 1 => R.string.networkmonitor_summary_no_root
      case 2 => R.string.networkmonitor_summary_priv_app
      case 3 => R.string.networkmonitor_summary_wifi_scanning_disabled
      case 4 => R.string.networkmonitor_summary_enabled
    }))

    available = App.instance.bindedConnectionsAvailable
    useBindedConnections.setEnabled(available == 1 || available == 2)
    useBindedConnections.setOnPreferenceClickListener((preference: Preference) => activity.testBindedConnections(true))
    useBindedConnections.setSummary(getString(R.string.binded_connections_summary) + getString(available match {
      case 0 => R.string.binded_connections_summary_na
      case 1 => R.string.binded_connections_summary_permission_missing
      case 2 => R.string.binded_connections_summary_enabled_revokable
      case 3 => R.string.binded_connections_summary_enabled
    }))
  }

  override def onDisplayPreferenceDialog(preference: Preference) = preference match {
    case _: EditTextPreference => displayPreferenceDialog(new EditTextPreferenceDialogFragment(preference.getKey))
    case _: NumberPickerPreference =>
      displayPreferenceDialog(new NumberPickerPreferenceDialogFragment(preference.getKey))
    case _ => super.onDisplayPreferenceDialog(preference)
  }

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

  private def humorous(preference: Preference) = {
    showToast(R.string.coming_soon)
    true
  }
}
