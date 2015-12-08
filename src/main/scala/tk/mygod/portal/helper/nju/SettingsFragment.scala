package tk.mygod.portal.helper.nju

import java.net.InetAddress
import java.text.{DateFormat, DecimalFormat}
import java.util.Date

import android.os.Bundle
import android.support.v7.preference.Preference
import android.util.Log
import org.json4s.JObject
import tk.mygod.net.UpdateManager
import tk.mygod.preference._
import tk.mygod.util.Conversions._
import tk.mygod.util.Logcat

object SettingsFragment {
  private val TAG = "SettingsFragment"
  private val SUPPORT_TIP = "misc.support.tip"
}

final class SettingsFragment extends PreferenceFragmentPlus {
  import SettingsFragment._

  private lazy val activity = getActivity.asInstanceOf[MainActivity]
  private lazy val portalWeb = findPreference("auth.portalWeb")
  private lazy val useBoundConnections = findPreference("misc.useBoundConnections")

  def onCreatePreferences(savedInstanceState: Bundle, rootKey: String) {
    getPreferenceManager.setSharedPreferencesName(PREF_NAME)
    addPreferencesFromResource(R.xml.settings)

    portalWeb.setOnPreferenceClickListener(_ => {
      startActivity(activity.intent[PortalActivity])
      true
    })
    findPreference("auth.login").setOnPreferenceClickListener(_ => {
      ThrowableFuture(PortalManager.login)
      true
    })
    findPreference("auth.logout").setOnPreferenceClickListener(_ => {
      ThrowableFuture(PortalManager.logout)
      true
    })
    findPreference("auth.reloginDelay").setOnPreferenceClickListener(_ => {
      if (app.pref.getBoolean(SUPPORT_TIP, true)) {
        makeToast(R.string.settings_misc_support_tip).show
        app.editor.putBoolean(SUPPORT_TIP, false).commit
      }
      false
    })

    PortalManager.setUserInfoListener(userInfoUpdated)
    findPreference("status.fullname").setOnPreferenceClickListener(humorous)
    findPreference("status.service_name").setOnPreferenceClickListener(humorous)
    findPreference("status.area_name").setOnPreferenceClickListener(humorous)
    findPreference("status.balance").setOnPreferenceClickListener(humorous)

    useBoundConnections.setOnPreferenceClickListener(_ => activity.testBoundConnections(true))
    findPreference("misc.update").setOnPreferenceClickListener(_ => {
      UpdateManager.check(activity, "https://github.com/Mygod/nju-portal-login-android/releases", app.handler)
      true
    })
    findPreference("misc.support").setOnPreferenceClickListener(_ => {
      activity.launchUrl(R.string.settings_misc_support_url)
      true
    })
    findPreference("misc.logcat").setOnPreferenceClickListener(_ => {
      activity.share(Logcat.fetch)
      true
    })
  }

  override def onResume {
    super.onResume
    val available = app.boundConnectionsAvailable
    useBoundConnections.setEnabled(available == 1 || available == 2)
    useBoundConnections.setSummary(getString(R.string.bound_connections_summary) + getString(available match {
      case 0 => R.string.bound_connections_summary_na
      case 1 => R.string.bound_connections_summary_permission_missing
      case 2 => R.string.bound_connections_summary_enabled_revokable
      case 3 => R.string.bound_connections_summary_enabled
    }))
    if (available > 1) {
      portalWeb.setSummary(R.string.settings_auth_portal_web_summary)
      if (NetworkMonitor.instance != null) NetworkMonitor.instance.initBoundConnections
    } else portalWeb.setSummary(null)
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
    makeToast(R.string.coming_soon).show
    true
  }
}
