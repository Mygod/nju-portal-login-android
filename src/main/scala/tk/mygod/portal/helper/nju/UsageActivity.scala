package tk.mygod.portal.helper.nju

import java.text.DecimalFormat

import android.content.pm.ShortcutManager
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener
import android.util.Log
import be.mygod.app.{CircularRevealActivity, ToolbarActivity}
import be.mygod.os.Build
import tk.mygod.portal.helper.nju.PortalManager.InvalidResponseException
import tk.mygod.portal.helper.nju.util.DualFormatter

import scala.collection.JavaConversions._

/**
  * @author Mygod
  */
object UsageActivity {
  private final val TAG = "SettingsUsageFragment"
  private final val NUMBER_FORMAT = "#%s"
  private final val UNEXPECTED_PAIR = "Unexpected pair found in volume: (%s, %s)"
  private val units = Array("KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB", "BB", "NB", "DB", "CB")
  private val smallFormat = new DecimalFormat("@@@")
  private val largeFormat = new DecimalFormat(",###")
}

class UsageActivity extends ToolbarActivity with CircularRevealActivity with OnRefreshListener with TypedFindView {
  import UsageActivity._

  private var fragment: UsageFragment = _
  private var swiper: SwipeRefreshLayout = _

  override protected def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_usage)
    configureToolbar()
    setNavigationIcon()
    fragment = getFragmentManager.findFragmentById(android.R.id.content).asInstanceOf[UsageFragment]
    swiper = findView(TR.swiper)
    swiper.setColorSchemeResources(R.color.material_accent_200, R.color.material_primary_500)
    swiper.setOnRefreshListener(this)
    if (Build.version >= 25) getSystemService(classOf[ShortcutManager]).reportShortcutUsed("usage")
  }

  override protected def onResume() {
    super.onResume()
    refresh()
  }

  def onRefresh(): Unit = refresh(true)

  def refresh(manual: Boolean = false) {
    swiper.setRefreshing(true)
    ThrowableFuture((try {
      if (manual) {
        PortalManager.logout()
        Thread.sleep(2000)
        PortalManager.login()
        Thread.sleep(2000)
      }
      PortalManager.queryVolume
    } catch {
      case e: InvalidResponseException =>
        if (!manual && e.response == "") {
          refresh(true)
          return
        }
        e.printStackTrace()
        app.showToast(e.getMessage)
        None
      case e: Exception =>
        e.printStackTrace()
        app.showToast(e.getMessage)
        None
    }) match {
      case Some(result) => runOnUiThread(() => {
        val user = new DualFormatter(format2 = NUMBER_FORMAT)
        val monthId = new DualFormatter(format2 = NUMBER_FORMAT)
        val service = new DualFormatter(format2 = NUMBER_FORMAT)
        val time = new DualFormatter(format2 = "~%s")
        var usage = 0L
        for (key <- result.keys) {
          val preference = fragment.findPreference("status.usage." + key)
          if (preference == null) key match {
            case "username" => user.value1 = result.getString(key)
            case "user_id" => user.value2 = result.getString(key)
            case "month" => monthId.value1 = result.getString(key)
            case "id" => monthId.value2 = result.getString(key)
            case "service_name" => service.value1 = result.getString(key)
            case BalanceManager.KEY_SERVICE_ID => service.value2 = result.getString(key)
            case "total_time" => time.value1 = formatTime(result.getLong(key))
            case "total_ipv4_volume" => time.value2 = formatTime(result.getLong(key))
            case "ipv4_units" | "ipv6_units" => result.getString(key) match {
              case "S" =>
              case value => UNEXPECTED_PAIR.format(key, value)
            }
            case "total_input_octets_ipv6" | "total_ipv6_volume" | "total_output_octets_ipv6" | "total_refer_ipv6" =>
              result.getLong(key) match {
                case 0 =>
                case value => UNEXPECTED_PAIR.format(key, value)
              }
            case BalanceManager.KEY_USAGE => usage = result.getLong(key)
            case _ => Log.e(TAG, "Unknown key in volume: " + key)
          } else preference.setSummary(key match {
            case _ =>
              val size = result.getLong(key)
              var n = size.toDouble
              var i = -1
              while (n >= 1000) {
                n /= 1024
                i = i + 1
              }
              val bytes = getResources.getQuantityString(R.plurals.bytes, quantityToInt(size))
              if (i < 0) "%s %s".format(largeFormat.format(size), bytes)
              else "%s %s (%s %s)".format(smallFormat.format(n), units(i), largeFormat.format(size), bytes)
          })
        }
        fragment.findPreference("status.usage.user").setSummary(user.toString)
        fragment.findPreference("status.usage.monthId").setSummary(monthId.toString)
        fragment.findPreference("status.usage.service").setSummary(service.toString)
        fragment.findPreference("status.usage.time").setSummary(time.toString)
        fragment.findPreference("status.usage.refer").setSummary(BalanceManager.check(usage, service.value2).toString)
        swiper.setRefreshing(false)
      })
      case None => runOnUiThread(() => finish(null))
    })
  }

  private def formatTime(totalSecs: Long) = {
    var result: String = null
    def prepend(s: String) = if (result == null) result = s else result = s + ' ' + result
    val secs = (totalSecs % 60).toInt
    if (secs != 0) prepend(secs + " " + getResources.getQuantityString(R.plurals.seconds, secs))
    var mins = totalSecs / 60
    val hrs = mins / 60
    mins %= 60
    if (mins != 0) prepend(mins + " " + getResources.getQuantityString(R.plurals.minutes, mins.toInt))
    if (hrs != 0) prepend(hrs + " " + getResources.getQuantityString(R.plurals.hours, quantityToInt(hrs)))
    result
  }
}
