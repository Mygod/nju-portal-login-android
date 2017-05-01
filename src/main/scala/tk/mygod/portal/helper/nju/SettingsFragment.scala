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
import org.json.JSONObject
import tk.mygod.portal.helper.nju.preference.MacAddressPreference
import tk.mygod.portal.helper.nju.util.DualFormatter

import scala.collection.JavaConversions._

object SettingsFragment {
  private final val TAG = "SettingsFragment"

  private final val preferenceGetId = classOf[Preference].getDeclaredMethod("getId")
  preferenceGetId.setAccessible(true)
}

final class SettingsFragment extends PreferenceFragmentPlus with OnSharedPreferenceChangeListener {
  import SettingsFragment._

  private lazy val activity = getActivity.asInstanceOf[MainActivity]

  override def onCreatePreferences(savedInstanceState: Bundle, rootKey: String) {
    getPreferenceManager.setSharedPreferencesName(PREF_NAME)
    addPreferencesFromResource(R.xml.settings)

    app.pref.registerOnSharedPreferenceChangeListener(this)

    findPreference("auth.portalWeb").setOnPreferenceClickListener(_ => {
      startActivity(CircularRevealActivity.putLocation(activity.intent[PortalActivity], activity.getLocationOnScreen),
        ActivityOptionsCompat.makeSceneTransitionAnimation(activity).toBundle)
      true
    })

    findPreference("auth.login").setOnPreferenceClickListener(_ => {
      ThrowableFuture(PortalManager.login())
      LogInOutShortcut.reportUsed()
      true
    })
    findPreference("auth.logout").setOnPreferenceClickListener(_ => {
      ThrowableFuture(PortalManager.logout())
      LogInOutShortcut.reportUsed()
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
    findPreference("status.portal_server_ip").setOnPreferenceClickListener(displayIpInfo)

    findPreference("notices.history").setOnPreferenceClickListener(_ => {
      startActivity(CircularRevealActivity.putLocation(activity.intent[NoticeActivity], activity.getLocationOnScreen),
        ActivityOptionsCompat.makeSceneTransitionAnimation(activity).toBundle)
      false
    })

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

  override def onResume() {
    super.onResume()
    if (app.boundConnectionsAvailable && NetworkMonitor.instance != null) NetworkMonitor.instance.initBoundConnections()
  }

  override def onDestroy() {
    PortalManager.setUserInfoListener(null)
    app.pref.unregisterOnSharedPreferenceChangeListener(this)
    super.onDestroy()
  }

  def userInfoUpdated(info: JSONObject) {
    val name = new DualFormatter()
    val activity = new DualFormatter()
    for (key <- info.keys) {
      val preference = findPreference("status." + key)
      if (preference == null) key match {
        case "fullname" => name.value1 = info.getString(key)
        case "username" => name.value2 = info.getString(key)
        case BalanceManager.KEY_ACTIVITY_START_TIME =>
          activity.value1 = PortalManager.parseTimeString(info.getLong(key))
        case "portal_acctsessionid" => activity.value2 = info.getString(key)
        case "domain" => info.getString(key) match {
          case "" =>
          case value => Log.e(TAG, "Unknown domain: " + value)
        }
        case _ => Log.e(TAG, "Unknown key in user_info: " + key)
      } else preference.setSummary(key match {
        case BalanceManager.KEY_BALANCE => BalanceManager.formatCurrency(info.getLong(key))
        case "useripv4" => PortalManager.parseIpv4(info.getInt(key))
        case "portal_server_ip" => PortalManager.parseIpv4(info.getInt(key))
        case _ => info.optString(key) match {
          case null => null
          case value => value.toString
        }
      })
    }
    findPreference("status.name").setSummary(name.toString)
    findPreference("status.activity").setSummary(activity.toString)
  }

  private def displayIpInfo(preference: Preference) = {
    val ip = preference.getSummary
    if (ip != null) activity.launchUrl(app.getIpLookup(ip))
    true
  }

  private def humorous(preference: Preference) = {
    makeToast(R.string.coming_soon).show()
    true
  }

  private lazy val localMac = findPreference(NetworkMonitor.LOCAL_MAC).asInstanceOf[MacAddressPreference]
  private lazy val alertBalanceEnabled = findPreference(BalanceManager.ENABLED).asInstanceOf[SwitchPreference]
  def onSharedPreferenceChanged(pref: SharedPreferences, key: String): Unit = key match {
    case BalanceManager.ENABLED => alertBalanceEnabled.setChecked(pref.getBoolean(key, true))
    case NetworkMonitor.LOCAL_MAC => localMac.setText(pref.getString(key, null))
    case _ =>
  }
}
