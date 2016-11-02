package tk.mygod.portal.helper.nju

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.support.v14.preference.SwitchPreference
import android.support.v4.app.ActivityOptionsCompat
import android.support.v7.preference.Preference
import android.util.Log
import be.mygod.app.CircularRevealActivity
import be.mygod.net.UpdateManager
import be.mygod.preference.PreferenceFragmentPlus
import be.mygod.util.Conversions._
import be.mygod.util.Logcat
import org.json4s.JObject
import tk.mygod.portal.helper.nju.preference.MacAddressPreference
import tk.mygod.portal.helper.nju.util.DualFormatter

object SettingsFragment {
  private final val TAG = "SettingsFragment"
  private final val KEY_SIGN_CONF = "auth.signConf"

  private final val preferenceGetId = classOf[Preference].getDeclaredMethod("getId")
  preferenceGetId.setAccessible(true)
}

final class SettingsFragment extends PreferenceFragmentPlus with OnSharedPreferenceChangeListener {
  import SettingsFragment._

  private lazy val activity = getActivity.asInstanceOf[MainActivity]
  private var portalWeb: Preference = _
  private var useBoundConnections: Preference = _
  private var ignoreSystemConnectionValidation: Preference = _

  override def onCreatePreferences(savedInstanceState: Bundle, rootKey: String) {
    getPreferenceManager.setSharedPreferencesName(PREF_NAME)
    addPreferencesFromResource(R.xml.settings)

    app.pref.registerOnSharedPreferenceChangeListener(this)

    portalWeb = findPreference("auth.portalWeb")
    portalWeb.setOnPreferenceClickListener(_ => {
      startActivity(CircularRevealActivity.putLocation(activity.intent[PortalActivity], activity.getLocationOnScreen),
        ActivityOptionsCompat.makeSceneTransitionAnimation(activity).toBundle)
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
    findPreference(KEY_SIGN_CONF).setOnPreferenceClickListener(_ => {
      displayPreferenceDialog(KEY_SIGN_CONF, new ConferenceSignFragment())
      true
    })

    PortalManager.setUserInfoListener(userInfoUpdated)
    findPreference("status.service_name").setOnPreferenceClickListener(humorous)
    findPreference("status.area_name").setOnPreferenceClickListener(humorous)
    findPreference("status.balance").setOnPreferenceClickListener(humorous)
    findPreference("status.useripv4").setOnPreferenceClickListener(displayIpInfo)
    findPreference("status.useripv6").setOnPreferenceClickListener(displayIpInfo)
    findPreference("status.mac").setOnPreferenceClickListener(p => {
      val mac = p.getSummary
      if (mac != null) activity.launchUrl(app.getMacLookup(mac))
      true
    })

    findPreference("notices.history").setOnPreferenceClickListener(_ => {
      startActivity(CircularRevealActivity.putLocation(activity.intent[NoticeActivity], activity.getLocationOnScreen),
        ActivityOptionsCompat.makeSceneTransitionAnimation(activity).toBundle)
      false
    })

    useBoundConnections = findPreference("misc.useBoundConnections")
    ignoreSystemConnectionValidation = findPreference(NetworkMonitor.IGNORE_SYSTEM_VALIDATION)
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
      case 0 =>
        ignoreSystemConnectionValidation.setEnabled(false)
        ignoreSystemConnectionValidation.setSummary(
          R.string.settings_misc_ignore_system_connection_validation_summary_na)
        R.string.bound_connections_summary_na
      case 1 =>
        ignoreSystemConnectionValidation.setEnabled(false)
        ignoreSystemConnectionValidation.setSummary(
          R.string.settings_misc_ignore_system_connection_validation_summary_permission_missing)
        R.string.bound_connections_summary_permission_missing
      case 2 =>
        ignoreSystemConnectionValidation.setEnabled(true)
        ignoreSystemConnectionValidation.setSummary(R.string.settings_misc_ignore_system_connection_validation_summary)
        R.string.bound_connections_summary_enabled_revokable
      case 3 =>
        ignoreSystemConnectionValidation.setEnabled(true)
        ignoreSystemConnectionValidation.setSummary(R.string.settings_misc_ignore_system_connection_validation_summary)
        R.string.bound_connections_summary_enabled
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

  override def onDestroy {
    PortalManager.setUserInfoListener(null)
    app.pref.unregisterOnSharedPreferenceChangeListener(this)
    super.onDestroy
  }

  def userInfoUpdated(info: JObject) {
    val name = new DualFormatter
    for ((key, value) <- info.values) {
      val preference = findPreference("status." + key)
      if (preference == null) key match {
        case "fullname" => name.value1 = value.asInstanceOf[String]
        case "username" => name.value2 = value.asInstanceOf[String]
        case "domain" => if (value.asInstanceOf[String] != "default") Log.e(TAG, "Unknown domain: " + value)
        case _ => Log.e(TAG, "Unknown key in user_info: " + key)
      } else preference.setSummary(key match {
        case BalanceManager.KEY_ACTIVITY_START_TIME => PortalManager.parseTimeString(value.asInstanceOf[BigInt])
        case BalanceManager.KEY_BALANCE => BalanceManager.formatCurrency(value.asInstanceOf[BigInt].toInt)
        case "useripv4" => PortalManager.parseIpv4(value.asInstanceOf[BigInt])
        case _ => if (value == null) null else value.toString
      })
    }
    findPreference("status.name").setSummary(name.toString)
  }

  private def displayIpInfo(preference: Preference) = {
    val ip = preference.getSummary
    if (ip != null) activity.launchUrl(app.getIpLookup(ip))
    true
  }

  private def humorous(preference: Preference) = {
    makeToast(R.string.coming_soon).show
    true
  }

  private lazy val localMac = findPreference(NetworkMonitor.LOCAL_MAC).asInstanceOf[MacAddressPreference]
  private lazy val alertBalanceEnabled = findPreference(BalanceManager.ENABLED).asInstanceOf[SwitchPreference]
  def onSharedPreferenceChanged(pref: SharedPreferences, key: String) = key match {
    case BalanceManager.ENABLED => alertBalanceEnabled.setChecked(pref.getBoolean(key, true))
    case NetworkMonitor.LOCAL_MAC => localMac.setText(pref.getString(key, null))
    case _ =>
  }
}
