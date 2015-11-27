package tk.mygod.nju.portal.login

import java.net.InetAddress
import java.text.{DateFormat, DecimalFormat}
import java.util.Date

import android.net.Uri
import android.os.Bundle
import android.support.customtabs.CustomTabsIntent
import android.support.v4.content.ContextCompat
import android.support.v7.preference.Preference
import android.util.Log
import org.json4s.JObject
import tk.mygod.net.UpdateManager
import tk.mygod.preference._
import tk.mygod.util.Logcat

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SettingsFragment {
  private val TAG = "SettingsFragment"
}

final class SettingsFragment extends PreferenceFragmentPlus {
  import SettingsFragment._

  private lazy val activity = getActivity.asInstanceOf[MainActivity]
  private lazy val useBoundConnections = findPreference("autoConnect.useBoundConnections")

  private lazy val customTabsIntent = new CustomTabsIntent.Builder()
    .setToolbarColor(ContextCompat.getColor(activity, R.color.material_accent_500)).build
  private def launchUrl(url: String) = customTabsIntent.launchUrl(activity, Uri.parse(url))

  def onCreatePreferences(savedInstanceState: Bundle, rootKey: String) {
    getPreferenceManager.setSharedPreferencesName(App.prefName)
    addPreferencesFromResource(R.xml.settings)

    findPreference("status.login").setOnPreferenceClickListener((preference: Preference) => {
      Future(PortalManager.login)
      true
    })
    findPreference("status.logout").setOnPreferenceClickListener((preference: Preference) => {
      Future(PortalManager.logout)
      true
    })
    PortalManager.setUserInfoListener(userInfoUpdated)
    findPreference("status.username").setOnPreferenceClickListener((preference: Preference) => {
      launchUrl("http://p.nju.edu.cn")
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
      launchUrl(R.string.settings_misc_support_url)
      true
    })
    findPreference("misc.logcat")  .setOnPreferenceClickListener((preference: Preference) => {
      activity.share(Logcat.fetch)
      true
    })
  }

  override def onResume {
    super.onResume

    val available = App.instance.boundConnectionsAvailable
    useBoundConnections.setEnabled(available == 1 || available == 2)
    useBoundConnections.setOnPreferenceClickListener((preference: Preference) => activity.testBoundConnections(true))
    useBoundConnections.setSummary(getString(R.string.bound_connections_summary) + getString(available match {
      case 0 => R.string.bound_connections_summary_na
      case 1 => R.string.bound_connections_summary_permission_missing
      case 2 => R.string.bound_connections_summary_enabled_revokable
      case 3 => R.string.bound_connections_summary_enabled
    }))
    if (available > 1 && activity.service != null) activity.service.initBoundConnections
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
