package tk.mygod.portal.helper.nju

import java.net.{HttpURLConnection, URL, URLEncoder}
import java.security.spec.PKCS8EncodedKeySpec
import java.security.{KeyFactory, Signature}
import java.text.SimpleDateFormat
import java.util.Date

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.support.v14.preference.SwitchPreference
import android.support.v4.app.ActivityOptionsCompat
import android.support.v7.preference.Preference
import android.util.{Base64, Log}
import be.mygod.app.CircularRevealActivity
import be.mygod.net.UpdateManager
import be.mygod.preference._
import be.mygod.util.CloseUtils._
import be.mygod.util.Conversions._
import be.mygod.util.{IOUtils, Logcat}
import org.json4s.ParserUtil.ParseException
import org.json4s.native.JsonMethods._
import org.json4s.{JArray, JInt, JObject}
import tk.mygod.portal.helper.nju.PortalManager.{InvalidResponseException, UnexpectedResponseCodeException}
import tk.mygod.portal.helper.nju.preference.{MacAddressPreference, MacAddressPreferenceDialogFragment}
import tk.mygod.portal.helper.nju.util.DualFormatter

object SettingsFragment {
  private final val TAG = "SettingsFragment"

  private final val SIGN_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss")
  private final val SIGN_DEVICEID = "6756"
  private final val SIGN_KEY = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(
    "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAIaEhtCJEL3oq50S5d1sLX5Lb4uLEUSI7u3rzgDIv/3cEpSoANrXwM3qgC4U66ygmohhDjMbjTFiZfLRm4nWHKNk3WANInimM99s5FHKKooC0d0I+rwUbk2WZr0uOdDnx9lGJxqdRolMtYBI/pZCpBDRj2ogEv5Oxr9f9tARg3LvAgMBAAECgYBTN2MrWM/ZnDmmZ016qHSQX9x2qCabjla5Kxp607YqNt3rxu8Yc0acXIjFeT2+wnA3FEuzhETZmzTUfaVKJQH7kRaOlRvksYwg/wSNB/irD8N2Lmw6XX7AvHRvzC5qMbNjUiO2vQwFUjorxES93Bpe5smzc0vOlL3UjPxQXvwZkQJBAMHC5OPcF05xZlob4I4yiFBzThq8WLHKp2Lzq9fYeBW2t90St659HHYqQc/E5FlOfZnCqh6k9/cEa8e2qpCpD9cCQQCxuf6e0y8HHv4iIsfMvpwNkc/MSgoXKFYL0EpLb68vxgWf0WSGhUQ13Hoyk/TzgVBn0O229rxxexsbujl/sDKpAkAixYfwAEJKeH1GtHQC8LyXu2mL0LsWBOkvD82J6bX7J5QtXzuJW7hs2D6BO7NC95wAqPeAklhRgwCYkYZgeYZ3AkEAjD2aH7XBDDt2iXUsd/GIrmR6tldOMwvPKi9IENKmSGpXkc7nJgcO1fmOK075IRTPX7xLd+6msF1V/MEsEgf1UQJAeYjz5SREy1ztwaKp2aKWr9kIRjqcvUm2E0CD8pccn+MI7PKjg1VFFEKHPGXixRGTSbbolxyP3CegdzJfr3hAVQ==",
    Base64.NO_WRAP)))

  private final val preferenceGetId = classOf[Preference].getDeclaredMethod("getId")
  preferenceGetId.setAccessible(true)
}

final class SettingsFragment extends PreferenceFragmentPlus with OnSharedPreferenceChangeListener {
  import SettingsFragment._

  private lazy val activity = getActivity.asInstanceOf[MainActivity]
  private var portalWeb: Preference = _
  private var useBoundConnections: Preference = _
  private var ignoreSystemConnectionValidation: Preference = _

  @SuppressLint(Array("NewApi"))
  def openSignConnection(file: String, data: String) = {
    val url = new URL(HTTP, "114.212.5.2", 8088, "/yktpre/services/conference/" + file)
    val conn = (if (NetworkMonitor.instance != null && app.boundConnectionsAvailable > 1) {
      val network = NetworkMonitor.instance.listener.preferredNetwork
      if (network == null) url.openConnection() else network.openConnection(url)
    } else {
      NetworkMonitor.preferNetworkLegacy()
      url.openConnection()
    }).asInstanceOf[HttpURLConnection]
    conn.setInstanceFollowRedirects(false)
    conn.setConnectTimeout(5000)
    conn.setReadTimeout(2000)
    conn.setUseCaches(false)
    conn.addRequestProperty("Content-Type", "text/plain")
    conn.addRequestProperty("accept", "*/*")
    conn.addRequestProperty("Connection", "Keep-Alive")
    conn.addRequestProperty("Charset", "UTF-8")
    conn.setRequestMethod("POST")
    conn.setDoOutput(true)
    //noinspection JavaAccessorMethodCalledAsEmptyParen
    autoClose(conn.getOutputStream())(os => IOUtils.writeAllText(os, data))
    if (conn.getResponseCode >= 400) throw new UnexpectedResponseCodeException(conn)
    //noinspection JavaAccessorMethodCalledAsEmptyParen
    val result = autoClose(conn.getInputStream())(IOUtils.readAllText)
    val json = try parse(result) catch {
      case e: ParseException => throw InvalidResponseException(url, result)
    }
    if (!json.isInstanceOf[JObject]) throw InvalidResponseException(url, result)
    json
  }

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
    findPreference("auth.signConf").setOnPreferenceClickListener(_ => {
      ThrowableFuture {
        try {
          val now = SIGN_DATE_FORMAT.format(new Date())
          val sign = Signature.getInstance("SHA1withRSA")
          sign.initSign(SIGN_KEY)
          sign.update((SIGN_DEVICEID + now).getBytes("GBK"))
          var json = openSignConnection("list", "deviceid=%s&timestamp=%s&sign=%s".format(SIGN_DEVICEID, now,
            URLEncoder.encode(Base64.encodeToString(sign.sign(), Base64.NO_WRAP), "UTF-8")))
          val code = (json \ "retcode").asInstanceOf[JInt].values.toInt
          if (code == 0) {
            (json \ "data").asInstanceOf[JArray].arr.headOption match {
              case Some(conf) =>
                Log.d(TAG, conf.toString)
                json = openSignConnection("sign", "conid=%s&signtype=2&signdata=%s"
                  .format(conf \ "con_id" values, PortalManager.username))
                app.showToast("#%d: %s\n%s %s %s".format((json \ "retcode").asInstanceOf[JInt].values.toInt,
                  json \ "retmsg" values, json \ "stuempno" values, json \ "custname" values, json \ "deptname" values))
              case None => app.showToast("No conferences available.")
            }
          } else app.showToast("#%d: %s".format(code, json \ "retmsg" values))
        } catch {
          case e: Exception =>
            app.showToast(e.getMessage)
            e.printStackTrace
        }
      }
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

  override def onDisplayPreferenceDialog(preference: Preference) = preference match {
    case _: MacAddressPreference => displayPreferenceDialog(preference.getKey, new MacAddressPreferenceDialogFragment)
    case _: EditTextPreference => displayPreferenceDialog(preference.getKey, new EditTextPreferenceDialogFragment)
    case _: NumberPickerPreference =>
      displayPreferenceDialog(preference.getKey, new NumberPickerPreferenceDialogFragment)
    case _ => super.onDisplayPreferenceDialog(preference)
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
