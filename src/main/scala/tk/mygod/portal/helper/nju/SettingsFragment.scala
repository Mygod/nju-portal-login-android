package tk.mygod.portal.helper.nju

import android.app.ActivityOptions
import android.os.Bundle
import android.support.v7.preference.Preference
import android.util.Log
import org.json4s.JObject
import tk.mygod.app.CircularRevealActivity
import tk.mygod.net.UpdateManager
import tk.mygod.portal.helper.nju.preference.{MacAddressPreference, MacAddressPreferenceDialogFragment}
import tk.mygod.portal.helper.nju.util.DualFormatter
import tk.mygod.preference._
import tk.mygod.util.Conversions._
import tk.mygod.util.Logcat

object SettingsFragment {
  private val TAG = "SettingsFragment"
  private val SUPPORT_TIP = "misc.support.tip"

  private val preferenceGetId = classOf[Preference].getDeclaredMethod("getId")
  preferenceGetId.setAccessible(true)
}

final class SettingsFragment extends PreferenceFragmentPlus {
  import SettingsFragment._

  private lazy val activity = getActivity.asInstanceOf[MainActivity]
  private lazy val portalWeb = findPreference("auth.portalWeb")
  private lazy val useBoundConnections = findPreference("misc.useBoundConnections")

  override def onCreatePreferences(savedInstanceState: Bundle, rootKey: String) {
    getPreferenceManager.setSharedPreferencesName(PREF_NAME)
    addPreferencesFromResource(R.xml.settings)

    portalWeb.setOnPreferenceClickListener(_ => {
      startActivity(CircularRevealActivity.putLocation(activity.intent[PortalActivity], activity.getLocationOnScreen),
        ActivityOptions.makeSceneTransitionAnimation(activity).toBundle)
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
    findPreference("status.service_name").setOnPreferenceClickListener(humorous)
    findPreference("status.area_name").setOnPreferenceClickListener(humorous)
    findPreference("status.balance").setOnPreferenceClickListener(humorous)
    findPreference("status.useripv4").setOnPreferenceClickListener(displayIpInfo)
    findPreference("status.useripv6").setOnPreferenceClickListener(displayIpInfo)
    findPreference("status.mac").setOnPreferenceClickListener(p => {
      activity.launchUrl(app.getMacLookup(p.getSummary))
      true
    })

    findPreference("notifications.notices").setOnPreferenceClickListener(_ => {
      startActivity(CircularRevealActivity.putLocation(activity.intent[NoticeActivity], activity.getLocationOnScreen),
        ActivityOptions.makeSceneTransitionAnimation(activity).toBundle)
      false
    })

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
      app.setEnabled[NetworkMonitorListener](false)
    } else {
      portalWeb.setSummary(null)
      app.setEnabled[NetworkMonitorListener](app.serviceStatus > 0)
    }
  }

  override def onDisplayPreferenceDialog(preference: Preference) = preference match {
    case _: MacAddressPreference => displayPreferenceDialog(preference.getKey, new MacAddressPreferenceDialogFragment)
    case _: EditTextPreference => displayPreferenceDialog(preference.getKey, new EditTextPreferenceDialogFragment)
    case _: NumberPickerPreference =>
      displayPreferenceDialog(preference.getKey, new NumberPickerPreferenceDialogFragment)
    case _ => super.onDisplayPreferenceDialog(preference)
  }

  override def onDestroy {
    PortalManager.setUserInfoListener(null)
    super.onDestroy
  }

  def userInfoUpdated(info: JObject) {
    val name = new DualFormatter
    for ((key, value) <- info.values) {
      val preference = findPreference("status." + key)
      if (preference == null) key match {
        case "fullname" => name.value1 = value.asInstanceOf[String]
        case "username" => name.value2 = value.asInstanceOf[String]
        case _ => Log.e(TAG, "Unknown key in user_info: " + key)
      } else preference.setSummary(key match {
        case "acctstarttime" => PortalManager.parseTime(value.asInstanceOf[BigInt])
        case "balance" => formatCurrency(value.asInstanceOf[BigInt].toInt)
        case "useripv4" => PortalManager.parseIpv4(value.asInstanceOf[BigInt])
        case _ => value.toString
      })
    }
    findPreference("status.name").setSummary(name.toString)
  }

  private def displayIpInfo(preference: Preference) = {
    activity.launchUrl(app.getIpLookup(preference.getSummary))
    true
  }

  private def humorous(preference: Preference) = {
    makeToast(R.string.coming_soon).show
    true
  }
}
