package tk.mygod.portal.helper.nju

import android.app.NotificationManager
import android.content.res.Resources
import android.content.{Context, BroadcastReceiver, Intent}
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.text.Html
import android.util.Log
import tk.mygod.portal.helper.nju.database.Notice
import tk.mygod.util.Conversions._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * @author Mygod
  */
object NoticeManager {
  import tk.mygod.portal.helper.nju.database.DatabaseManager.noticeDao

  private final val ACTION_MARK_AS_READ = "tk.mygod.portal.helper.nju.NoticeManager.MARK_AS_READ"
  private final val ACTION_VIEW = "tk.mygod.portal.helper.nju.NoticeManager.VIEW"
  private final val EXTRA_DISTRIBUTION_TIME = "DISTRIBUTION_TIME"

  private lazy val nm = app.systemService[NotificationManager]

  private def fetchNotice(distributionTime: Long) = noticeDao.queryForId(distributionTime)
  private def fetchAllNotices = noticeDao.query(noticeDao.queryBuilder.orderBy("distributionTime", false).prepare)

  private def updateUnreadNotices = PortalManager.queryNotice match {
    case Some(notices) =>
      val unreadNotices = new ArrayBuffer[Notice]
      val activeNotices = new mutable.HashMap ++ notices.map(notice => notice.distributionTime -> notice)
      for (activeNotice <- noticeDao.query(noticeDao.queryBuilder.where.eq("obsolete", false).prepare).asScala)
        activeNotices.get(activeNotice.distributionTime) match {
          case Some(notice) =>
            activeNotices.remove(activeNotice.distributionTime)
            if (activeNotice.title != notice.title || activeNotice.url != notice.url) { // updated
              notice.read = false
              noticeDao.update(notice)
            }
            if (!notice.read) unreadNotices += notice
          case None =>
            // archive obsolete notices
            activeNotice.obsolete = true
            noticeDao.update(activeNotice)
        }
      for ((time, notice) <- activeNotices) {
        var result = noticeDao.createIfNotExists(notice)
        if (result.obsolete) {
          noticeDao.update(notice)
          result = notice
        }
        if (!result.read) unreadNotices += result
      }
      unreadNotices
    case _ => ArrayBuffer.empty[Notice]  // error, ignore
  }

  private def read(notice: Notice) {
    notice.read = true
    noticeDao.update(notice)
  }

  private def readSystemInteger(key: String) =
    app.getResources.getInteger(Resources.getSystem.getIdentifier(key, "integer", "android"))
  private lazy val lightOnMs = readSystemInteger("config_defaultNotificationLedOn")
  private lazy val lightOffMs = readSystemInteger("config_defaultNotificationLedOff")

  def pushUnreadNotices = {
    val notices = updateUnreadNotices
    // todo: reuse Builder if possible
    val accent = ContextCompat.getColor(app, R.color.material_primary_500)
    Log.v("NoticeManager", "%s, %s".format(lightOnMs, lightOffMs))
    if (notices.nonEmpty) app.handler.post(for (notice <- notices) nm.notify(notice.distributionTime.hashCode,
      new NotificationCompat.Builder(app).setColor(accent).setLights(accent, lightOnMs, lightOffMs)
        .setSmallIcon(R.drawable.ic_action_announcement).setGroup("Notices").setAutoCancel(true)
        .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE) // todo: customize
        .setContentTitle(Html.fromHtml(notice.title)).setWhen(notice.distributionTime * 1000).setContentText(notice.url)
        .setContentIntent(app.pendingBroadcast(new Intent(ACTION_VIEW)
          .putExtra(EXTRA_DISTRIBUTION_TIME, notice.distributionTime)))
        .setDeleteIntent(app.pendingBroadcast(new Intent(ACTION_MARK_AS_READ)
          .putExtra(EXTRA_DISTRIBUTION_TIME, notice.distributionTime))).build))
  }
}

final class NoticeManager extends BroadcastReceiver {
  import NoticeManager._

  def onReceive(context: Context, intent: Intent) {
    val notice = fetchNotice(intent.getLongExtra(EXTRA_DISTRIBUTION_TIME, 0))
    if (intent.getAction == ACTION_VIEW)
      context.startActivity(new Intent(Intent.ACTION_VIEW).setData(notice.url).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    read(notice)
  }
}
