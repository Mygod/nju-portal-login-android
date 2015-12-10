package tk.mygod.portal.helper.nju

import android.app.NotificationManager
import android.content.res.Resources
import android.content.{BroadcastReceiver, Context, Intent}
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
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
  private final val EXTRA_ID = "ID"

  private lazy val nm = app.systemService[NotificationManager]

  private def fetchNotice(id: Int) = noticeDao.queryForId(id)
  def fetchAllNotices = noticeDao.query(noticeDao.queryBuilder.orderBy("distributionTime", false).prepare).asScala

  def updateUnreadNotices = PortalManager.queryNotice match {
    case Some(notices) =>
      val unread = new ArrayBuffer[Notice]
      val active = new mutable.HashMap[Notice, Notice] ++ notices.map(n => n -> n)
      for (notice <- noticeDao.query(noticeDao.queryBuilder.where.eq("obsolete", false).prepare).asScala)
        if (active.remove(notice).isEmpty) {
          // archive obsolete notices
          notice.obsolete = true
          noticeDao.update(notice)
        } else if (!notice.read) unread += notice
      for ((_, notice) <- active) {
        var result = noticeDao.createIfNotExists(notice)
        if (result.obsolete) {
          result.obsolete = false
          result.read = false
          noticeDao.update(result)
        }
        if (!result.read) unread += result
      }
      unread
    case _ => ArrayBuffer.empty[Notice]  // error, ignore
  }

  def read(notice: Notice) {
    notice.read = true
    noticeDao.update(notice)
  }

  private def readSystemInteger(key: String) =
    app.getResources.getInteger(Resources.getSystem.getIdentifier(key, "integer", "android"))
  private lazy val lightOnMs = readSystemInteger("config_defaultNotificationLedOn")
  private lazy val lightOffMs = readSystemInteger("config_defaultNotificationLedOff")
  private val pushedNotices = new mutable.HashSet[Int]

  def pushUnreadNotices {
    val notices = updateUnreadNotices
    if (notices.nonEmpty) app.handler.post(for (notice <- notices) {
      val builder = new NotificationCompat.Builder(app).setAutoCancel(true)
        .setColor(ContextCompat.getColor(app, R.color.material_primary_500))
        .setLights(ContextCompat.getColor(app, R.color.material_purple_a700), lightOnMs, lightOffMs)
        .setSmallIcon(R.drawable.ic_action_announcement).setGroup("Notices").setContentText(notice.url)
        .setContentTitle(notice.formattedTitle).setWhen(notice.distributionTime * 1000)
        .setContentIntent(app.pendingBroadcast(new Intent(ACTION_VIEW).putExtra(EXTRA_ID, notice.id)))
        .setDeleteIntent(app.pendingBroadcast(new Intent(ACTION_MARK_AS_READ).putExtra(EXTRA_ID, notice.id)))
      if (pushedNotices.add(notice.id))
        builder.setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE)  // todo: customize
      nm.notify(notice.id, builder.build)
    })
  }
  def cancelAllNotices = for (notice <- pushedNotices) nm.cancel(notice)
}

final class NoticeManager extends BroadcastReceiver {
  import NoticeManager._

  def onReceive(context: Context, intent: Intent) {
    val notice = fetchNotice(intent.getIntExtra(EXTRA_ID, 0))
    if (intent.getAction == ACTION_VIEW)
      context.startActivity(new Intent(Intent.ACTION_VIEW).setData(notice.url).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    read(notice)
  }
}
