package tk.mygod.portal.helper.nju

import java.text.DecimalFormat
import java.util.concurrent.TimeUnit
import java.util.{Calendar, Date}

import android.content.{BroadcastReceiver, Context, Intent}
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationCompat.BigTextStyle
import android.support.v4.content.ContextCompat
import android.util.Log
import org.json.JSONObject
import tk.mygod.portal.helper.nju.PortalManager.InvalidResponseException

/**
  * This object takes care of your balance!
  *
  * @author Mygod
  */
object BalanceManager {
  class Usage(private val refer: Long, private val limit: Int) {
    override def toString: String = {
      if (refer <= 0) return formatCurrency(refer)
      if (refer <= 600) return "%1$s - %1$s = %2$s".format(formatCurrency(refer), formatCurrency(0))
      if (refer <= limit + 600) return "%s - %s = %s"
        .format(formatCurrency(refer), formatCurrency(600), formatCurrency(refer - 600))
      "%s - %s - %s = %s"
        .format(formatCurrency(refer), formatCurrency(600), formatCurrency(refer - limit - 600), formatCurrency(limit))
    }
    def monthChargeLimit: Long = {
      if (refer <= 600) return limit
      if (refer >= limit + 600) return 0
      limit + 600 - refer
    }
    def remainingTime(balance: Long): Long = {
      if (refer > limit + 600) return -1
      if (refer >= 600) return balance
      600 - refer + balance
    }
  }

  private final val ACTION_MUTE_MONTH = "tk.mygod.portal.helper.nju.BalanceManager.MUTE_MONTH"
  private final val ACTION_MUTE_FOREVER = "tk.mygod.portal.helper.nju.BalanceManager.MUTE_FOREVER"
  final val KEY_ACTIVITY_START_TIME = "portal_acctsessionid"
  final val KEY_BALANCE = "balance"
  final val KEY_SERVICE_ID = "service_id"
  final val KEY_USAGE = "total_refer_ipv4"
  final val ENABLED = "notifications.alert.balance"
  final val TAG = "BalanceManager"
  private final val LAST_MONTH = "notifications.alert.balance.lastMonth"

  private val currencyFormat = new DecimalFormat("0.00")
  def formatCurrency(c: Long): String = currencyFormat.format(c / 100.0)

  private def enabled = app.pref.getBoolean(ENABLED, true)
  private def enabled(value: Boolean) = app.editor.putBoolean(ENABLED, value).apply()
  private def lastMonth = app.pref.getInt(LAST_MONTH, -1)
  private def lastMonth(month: Int) = app.editor.putInt(LAST_MONTH, month).apply()

  private def currentMonth = {
    val calendar = Calendar.getInstance
    calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH)
  }
  private def needsChecking = lastMonth < currentMonth

  def cancelNotification(): Unit = app.nm.cancel(0)
  def pushNotification(balance: Long, summary: CharSequence = null) {
    var text = app.getText(R.string.alert_balance_insufficient_soon)
    val builder = new NotificationCompat.Builder(app)
      .setAutoCancel(true)
      .setColor(ContextCompat.getColor(app, R.color.material_primary_500))
      .setLights(ContextCompat.getColor(app, R.color.material_purple_a700), app.lightOnMs, app.lightOffMs)
      .setSmallIcon(R.drawable.ic_action_credit_card)
      .setContentTitle(app.getString(R.string.alert_balance_insufficient))
      .setContentText(text)
      .setShowWhen(true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    builder.setPublicVersion(builder.build).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setContentTitle(app.getString(R.string.alert_balance_insufficient_details, formatCurrency(balance)))
    if (summary != null) {
      builder.setContentText(summary)
        .addAction(R.drawable.ic_social_notifications_off,
          app.getString(R.string.alert_balance_insufficient_action_mute_month),
          app.pendingBroadcast(ACTION_MUTE_MONTH))
      text = summary
    }
    app.nm.notify(0, new BigTextStyle(builder.addAction(R.drawable.ic_social_notifications_off,
      app.getString(R.string.alert_balance_insufficient_action_mute_forever),
      app.pendingBroadcast(ACTION_MUTE_FOREVER))).bigText(text).build())
  }

  def check(info: JSONObject): Unit = if (enabled && needsChecking)
    ThrowableFuture(try PortalManager.queryVolume match {
      case Some(result) =>
        check(result.getLong(KEY_USAGE), result.getString(KEY_SERVICE_ID), info, enabled = true, needsChecking = true)
      case _ =>
    } catch {
      case e: InvalidResponseException =>
        if (e.response.isEmpty) Log.w(TAG, "Nothing returned on querying usage!") else throw e
    })
  def check(refer: Long, serviceId: String): Usage = {
    val enabled = this.enabled
    check(refer, serviceId, PortalManager.getUserInfo.get, enabled, enabled && needsChecking)
  }
  private def check(refer: Long, serviceId: String, info: JSONObject, enabled: Boolean, needsChecking: Boolean) = {
    val usage = new Usage(refer, serviceId match {
      case "13455362142011" =>
        // 学生标准计时服务
        // 简介：同一时间只允许1个在线。
        // 资费：每月前30小时免费，超过30小时部分，0.2元/小时；每月20元封顶。
        2000
      case "14899909789418" =>
        // 学生2线程网络服务(测试)
        // 简介：同一时间最多2个在线。
        // 资费：每月前30小时免费，超过30小时部分，每线程0.2元/小时；每月40元封顶。
        4000
      case id =>
        Log.e(TAG, "Unknown service id \"%s\". Assuming 20 yuan as upper limit for now.".format(id))
        2000
    })
    val balance = info.getLong(KEY_BALANCE)
    if (enabled)  // always check for negative balance
      if (balance < 0) pushNotification(balance) else if (needsChecking)
        if (balance < usage.monthChargeLimit) {
          var length: String = null
          def prepend(s: String) = if (length == null) length = s else length = s + ' ' + length
          var time = info.getString(KEY_ACTIVITY_START_TIME).toLong * .0001 + // total remaining seconds
            180 * usage.remainingTime(balance) - TimeUnit.MILLISECONDS.toSeconds(new Date().getTime)
          if (time > 0) {
            val sec = time % 60
            time /= 60
            if (sec != 0) prepend(sec + " " + app.getResources.getQuantityString(R.plurals.seconds,
              if (Math.abs(sec - 1) < 1e-4) 1 else 0))  // TODO: workaround for English, Chinese only
            val min = (time % 60).toInt
            time /= 60
            if (min != 0) prepend(min + " " + app.getResources.getQuantityString(R.plurals.minutes, min))
            val hr = (time % 24).toInt
            val days = (time / 24).toInt
            if (hr != 0) prepend(hr + " " + app.getResources.getQuantityString(R.plurals.hours, hr))
            if (days != 0) prepend(days + " " + app.getResources.getQuantityString(R.plurals.days, quantityToInt(days)))
            pushNotification(balance, app.getString(R.string.alert_balance_insufficient_later, length))
          } else pushNotification(balance, app.getString(R.string.alert_balance_insufficient_soon))
        } else {
          lastMonth(currentMonth)
          cancelNotification()
        }
    usage
  }
}

final class BalanceManager extends BroadcastReceiver {
  import BalanceManager._

  def onReceive(context: Context, intent: Intent) {
    intent.getAction match {
      case ACTION_MUTE_MONTH => lastMonth(currentMonth)
      case ACTION_MUTE_FOREVER => enabled(false)
    }
    cancelNotification()
  }
}
