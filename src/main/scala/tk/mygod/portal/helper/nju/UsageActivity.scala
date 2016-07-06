package tk.mygod.portal.helper.nju

import java.text.DecimalFormat

import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener
import android.util.Log
import tk.mygod.app.{CircularRevealActivity, ToolbarActivity}
import tk.mygod.portal.helper.nju.util.DualFormatter

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
    swiper.setColorSchemeResources(R.color.material_accent_500, R.color.material_primary_500)
    swiper.setOnRefreshListener(this)
  }

  override protected def onResume {
    super.onResume
    refresh()
  }

  def onRefresh = refresh(true)

  def refresh(manual: Boolean = false) = {
    swiper.setRefreshing(true)
    ThrowableFuture((try {
      if (manual) {
        PortalManager.logout
        Thread.sleep(2000)
        PortalManager.login
        Thread.sleep(2000)
      }
      PortalManager.queryVolume
    } catch {
      case e: PortalManager.NetworkUnavailableException =>
        app.showToast(app.getString(R.string.error_network_unavailable))
        None
      case e: Exception =>
        e.printStackTrace
        app.showToast(e.getMessage)
        None
    }) match {
      case Some(result) => runOnUiThread(() => {
        val user = new DualFormatter(format2 = NUMBER_FORMAT)
        val monthId = new DualFormatter(format2 = NUMBER_FORMAT)
        val service = new DualFormatter(format2 = NUMBER_FORMAT)
        val time = new DualFormatter(format2 = "~%s")
        for ((key, value) <- result.values) {
          val preference = fragment.findPreference("status.usage." + key)
          if (preference == null) key match {
            case "username" => user.value1 = value.toString
            case "user_id" => user.value2 = value.toString
            case "month" => monthId.value1 = value.toString
            case "id" => monthId.value2 = value.toString
            case "service_name" => service.value1 = value.toString
            case "service_id" => service.value2 = value.toString
            case "total_time" => time.value1 = formatTime(value.asInstanceOf[BigInt].toInt)
            case "total_ipv4_volume" => time.value2 = formatTime(value.asInstanceOf[BigInt].toInt)
            case "ipv4_units" | "ipv6_units" => if (value.toString != "S") UNEXPECTED_PAIR.format(key, value)
            case "total_input_octets_ipv6" | "total_ipv6_volume" | "total_output_octets_ipv6" | "total_refer_ipv6" =>
              if (value.toString != "0") UNEXPECTED_PAIR.format(key, value)
            case _ => Log.e(TAG, "Unknown key in volume: " + key)
          } else preference.setSummary(key match {
            case "total_refer_ipv4" =>
              val refer = value.asInstanceOf[BigInt].toInt
              if (refer == 0) formatCurrency(0)
              else if (refer <= 600) "%1$s - %1$s = %2$s".format(formatCurrency(refer), formatCurrency(0))
              else if (refer <= 2600)
                "%s - %s = %s".format(formatCurrency(refer), formatCurrency(600), formatCurrency(refer - 600))
              else "%s - %s - %s = %s"
                .format(formatCurrency(refer), formatCurrency(600), formatCurrency(refer - 2600), formatCurrency(2000))
            case _ =>
              val size = value.asInstanceOf[BigInt]
              var n = size.toDouble
              var i = -1
              while (n >= 1000) {
                n /= 1024
                i = i + 1
              }
              val bytes = getResources.getQuantityString(R.plurals.bytes, size.toInt)
              if (i < 0) "%s %s".format(largeFormat.format(size), bytes)
              else "%s %s (%s %s)".format(smallFormat.format(n), units(i), largeFormat.format(size), bytes)
          })
        }
        fragment.findPreference("status.usage.user").setSummary(user.toString)
        fragment.findPreference("status.usage.monthId").setSummary(monthId.toString)
        fragment.findPreference("status.usage.service").setSummary(service.toString)
        fragment.findPreference("status.usage.time").setSummary(time.toString)
        swiper.setRefreshing(false)
      })
      case None => runOnUiThread(supportFinishAfterTransition)
    })
  }

  def formatTime(totalSecs: Int) = {
    var result: String = null
    def prepend(s: String) = if (result == null) result = s else result = s + ' ' + result
    val secs = totalSecs % 60
    if (secs != 0) prepend(secs + " " + getResources.getQuantityString(R.plurals.seconds, secs))
    var mins = totalSecs / 60
    val hrs = mins / 60
    mins %= 60
    if (mins != 0) prepend(mins + " " + getResources.getQuantityString(R.plurals.minutes, mins))
    if (hrs != 0) prepend(hrs + " " + getResources.getQuantityString(R.plurals.hours, hrs))
    result
  }
}
