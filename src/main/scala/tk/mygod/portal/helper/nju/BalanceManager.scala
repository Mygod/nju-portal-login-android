package tk.mygod.portal.helper.nju

import java.text.DecimalFormat
import java.util.concurrent.TimeUnit
import java.util.{Calendar, Date}

import android.content.{BroadcastReceiver, Context, Intent}
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationCompat.BigTextStyle
import android.support.v4.content.ContextCompat
import org.json4s.{JInt, JObject}

/**
  * This object takes care of your balance!
  *
  * @author Mygod
  */
object BalanceManager {
  class Usage(private val refer: Int) {
    override def toString: String = {
      if (refer <= 0) return formatCurrency(refer)
      if (refer <= 600) return "%1$s - %1$s = %2$s".format(formatCurrency(refer), formatCurrency(0))
      if (refer <= 2600) return "%s - %s = %s"
        .format(formatCurrency(refer), formatCurrency(600), formatCurrency(refer - 600))
      "%s - %s - %s = %s"
        .format(formatCurrency(refer), formatCurrency(600), formatCurrency(refer - 2600), formatCurrency(2000))
    }
    def monthChargeLimit: Int = {
      if (refer <= 600) return 2000
      if (refer >= 2600) return 0
      2600 - refer
    }
    def remainingTime(balance: Int): Int = {
      if (refer > 2600) return -1
      if (refer >= 600) return balance
      600 - refer + balance
    }
  }

  private final val ACTION_MUTE_MONTH = "tk.mygod.portal.helper.nju.BalanceManager.MUTE_MONTH"
  private final val ACTION_MUTE_FOREVER = "tk.mygod.portal.helper.nju.BalanceManager.MUTE_FOREVER"
  final val KEY_ACTIVITY_START_TIME = "acctstarttime"
  final val KEY_BALANCE = "balance"
  final val KEY_USAGE = "total_refer_ipv4"
  final val ENABLED = "notifications.alert.balance"
  private final val LAST_MONTH = "notifications.alert.balance.lastMonth"

  private val currencyFormat = new DecimalFormat("0.00")
  def formatCurrency(c: Int) = currencyFormat.format(c / 100.0)

  private def enabled = app.pref.getBoolean(ENABLED, true)
  private def enabled(value: Boolean) = app.editor.putBoolean(ENABLED, value).apply
  private def lastMonth = app.pref.getInt(LAST_MONTH, -1)
  private def lastMonth(month: Int) = app.editor.putInt(LAST_MONTH, month).apply

  private def currentMonth = {
    val calendar = Calendar.getInstance
    calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH)
  }
  private def needsChecking = lastMonth < currentMonth

  private def notify(balance: Int, summary: CharSequence = null) = {
    val builder = new NotificationCompat.Builder(app)
      .setAutoCancel(true)
      .setColor(ContextCompat.getColor(app, R.color.material_primary_500))
      .setLights(ContextCompat.getColor(app, R.color.material_purple_a700), app.lightOnMs, app.lightOffMs)
      .setSmallIcon(R.drawable.ic_action_credit_card)
      .setContentTitle(app.getString(R.string.alert_balance_insufficient))
      .setShowWhen(true)
    builder.setPublicVersion(builder.build)
      .setContentTitle(app.getString(R.string.alert_balance_insufficient_details, formatCurrency(balance)))
    val text = if (summary == null) app.getText(R.string.alert_balance_insufficient_soon) else {
      builder.addAction(R.drawable.ic_social_notifications_off,
        app.getString(R.string.alert_balance_insufficient_action_mute_month),
        app.pendingBroadcast(ACTION_MUTE_MONTH))
      summary
    }
    app.nm.notify(0, new BigTextStyle(builder.setContentText(text)
      .addAction(R.drawable.ic_social_notifications_off,
        app.getString(R.string.alert_balance_insufficient_action_mute_forever),
        app.pendingBroadcast(ACTION_MUTE_FOREVER))
    ).bigText(text).build())
  }

  def check(info: JObject): Unit = if (enabled && needsChecking)
    PortalManager.queryVolume match {
      case Some(result) => check((result \ KEY_USAGE).asInstanceOf[JInt].values, info, true, true)
      case _ =>
    }
  def check(refer: BigInt): Usage = {
    val enabled = this.enabled
    check(refer, PortalManager.getUserInfo.get, enabled, enabled && needsChecking)
  }
  private def check(refer: BigInt, info: JObject, enabled: Boolean, needsChecking: Boolean) = {
    val usage = new Usage(refer.toInt)
    val balance = (info \ KEY_BALANCE).asInstanceOf[JInt].values.toInt
    if (enabled)  // always check for negative balance
      if (balance < 0) notify(balance) else if (needsChecking)
        if (balance < usage.monthChargeLimit) {
          var length: String = null
          def prepend(s: String) = if (length == null) length = s else length = s + ' ' + length
          var time = ((info \ KEY_ACTIVITY_START_TIME).asInstanceOf[JInt].values.toLong + // total remaining seconds
            180 * usage.remainingTime(balance) - TimeUnit.MILLISECONDS.toSeconds(new Date().getTime)).toInt
          if (time > 0) {
            val sec = time % 60
            time /= 60
            if (sec != 0) prepend(sec + " " + app.getResources.getQuantityString(R.plurals.seconds, sec))
            val min = time % 60
            time /= 60
            if (min != 0) prepend(min + " " + app.getResources.getQuantityString(R.plurals.minutes, min))
            val hr = time % 24
            val days = time / 24
            if (hr != 0) prepend(hr + " " + app.getResources.getQuantityString(R.plurals.hours, hr))
            if (days != 0) prepend(days + " " + app.getResources.getQuantityString(R.plurals.days, days))
            notify(balance, app.getString(R.string.alert_balance_insufficient_later, length))
          } else notify(balance, app.getText(R.string.alert_balance_insufficient_soon))
        } else lastMonth(currentMonth)
    usage
  }
}

final class BalanceManager extends BroadcastReceiver {
  import BalanceManager._

  def onReceive(context: Context, intent: Intent) = intent.getAction match {
    case ACTION_MUTE_MONTH => lastMonth(currentMonth)
    case ACTION_MUTE_FOREVER => enabled(false)
  }
}
