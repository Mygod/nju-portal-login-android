package tk.mygod.portal.helper.nju

import android.accounts.Account
import android.app.Service
import android.content._
import android.os.Bundle
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import me.leolin.shortcutbadger.ShortcutBadger
import tk.mygod.os.Build
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

  final val SYNC_INTERVAL = "notifications.notices.sync.interval"
  private final val ACTION_MARK_AS_READ = "tk.mygod.portal.helper.nju.NoticeManager.MARK_AS_READ"
  private final val ACTION_VIEW = "tk.mygod.portal.helper.nju.NoticeManager.VIEW"
  private final val EXTRA_ID = "ID"
  private final val AUTHORITY = "tk.mygod.portal.helper.nju.provider"

  private final class SyncAdapter(context: Context, autoInitialize: Boolean, allowParallelSyncs: Boolean)
    extends AbstractThreadedSyncAdapter(context, autoInitialize, allowParallelSyncs) {
    def onPerformSync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient,
                      syncResult: SyncResult) = updateUnreadNotices(syncResult)
  }

  val account = new Account(app.getString(R.string.notice_sync), app.getString(R.string.portal_activity_url))
  def updatePeriodicSync = app.pref.getString(SYNC_INTERVAL, "0").toLong match {
    case 0 =>
      ContentResolver.removePeriodicSync(account, AUTHORITY, Bundle.EMPTY)
      ContentResolver.setSyncAutomatically(account, AUTHORITY, false)
    case mins =>
      if (!ContentResolver.getMasterSyncAutomatically) app.showToast(app.getString(R.string.notice_sync_off))
      ContentResolver.setSyncAutomatically(account, AUTHORITY, true)
      ContentResolver.addPeriodicSync(account, AUTHORITY, Bundle.EMPTY, mins * 60)
  }

  private def fetchNotice(id: Int) = noticeDao.queryForId(id)
  def fetchAllNotices = noticeDao.query(noticeDao.queryBuilder.orderBy(Notice.DISTRIBUTION_TIME, false).prepare).asScala

  def updateUnreadCount = ShortcutBadger.applyCount(app, noticeDao.queryForEq("read", false).size)

  def updateUnreadNotices(syncResult: SyncResult = null) =
    synchronized(PortalManager.queryNotice(syncResult == null) match {
      case Some(notices) =>
        val unread = new ArrayBuffer[Notice]
        val active = new mutable.HashMap[Notice, Notice] ++ notices.map(n => n -> n)
        for (notice <- noticeDao.queryForEq("obsolete", false).asScala)
          if (active.remove(notice).isEmpty) {
            // archive obsolete notices
            notice.obsolete = true
            noticeDao.update(notice)
            if (syncResult != null) syncResult.stats.numUpdates += 1
          } else if (!notice.read) unread += notice
        var newItem = false
        for ((_, notice) <- active) {
          val duplicate = noticeDao.query(noticeDao.queryBuilder.where.eq(Notice.DISTRIBUTION_TIME,
            notice.distributionTime).and.eq("title", notice.title).and.eq("url", notice.url).prepare)
          if (duplicate.size > 0) {
            val result = duplicate.get(0)
            if (result.obsolete) {
              result.obsolete = false
              noticeDao.update(result)
              if (syncResult != null) syncResult.stats.numUpdates += 1
            }
            if (!result.read) unread += result
          } else {
            noticeDao.create(notice)
            if (syncResult != null) syncResult.stats.numInserts += 1
            unread += notice
            newItem = true
          }
        }
        if (newItem) updateUnreadCount
        unread
      case _ => // error, ignore
        if (syncResult != null) syncResult.stats.numIoExceptions += 1
        ArrayBuffer.empty[Notice]
    })

  def read(notice: Notice) {
    notice.read = true
    noticeDao.update(notice)
    updateUnreadCount
  }

  private val pushedNotices = new mutable.HashSet[Int]
  private var receiverRegistered: Boolean = _
  private def pending(action: String, id: Int) =
    app.pendingBroadcast(new Intent(action).putExtra(EXTRA_ID, id).setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY))
  def pushUnreadNotices = if (app.pref.getBoolean("notifications.notices.sync.login", true)) {
    val notices = updateUnreadNotices()
    if (notices.nonEmpty) app.handler.post(() => {
      synchronized(if (!receiverRegistered) {
        val filter = new IntentFilter(ACTION_MARK_AS_READ)
        filter.addAction(ACTION_VIEW)
        app.registerReceiver((context, intent) => {
          val notice = fetchNotice(intent.getIntExtra(EXTRA_ID, 0))
          if (intent.getAction == ACTION_VIEW) context.startActivity(new Intent(Intent.ACTION_VIEW)
            .setData(notice.url).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
          read(notice)
        }, filter)
        receiverRegistered = true
      })
      for (notice <- notices) {
        val builder = new NotificationCompat.Builder(app).setAutoCancel(true)
          .setColor(ContextCompat.getColor(app, R.color.material_primary_500))
          .setLights(ContextCompat.getColor(app, R.color.material_purple_a700), app.lightOnMs, app.lightOffMs)
          .setSmallIcon(R.drawable.ic_action_announcement).setGroup(ACTION_VIEW).setContentText(notice.url)
          .setContentTitle(notice.formattedTitle).setWhen(notice.distributionTime * 1000)
          .setContentIntent(pending(ACTION_VIEW, notice.id)).setDeleteIntent(pending(ACTION_MARK_AS_READ, notice.id))
        if (pushedNotices.add(notice.id))
          builder.setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE)
        if (Build.version >= 21) builder.setPriority(NotificationCompat.PRIORITY_HIGH)
        app.nm.notify(notice.id, builder.build)
      }
    })
  }
  def cancelAllNotices = for (notice <- pushedNotices) app.nm.cancel(notice)
}

final class NoticeManager extends Service {
  import NoticeManager._

  private var syncAdapter: SyncAdapter = _

  override def onCreate {
    super.onCreate
    syncAdapter = new SyncAdapter(app, true, false)
  }

  def onBind(intent: Intent) = syncAdapter.getSyncAdapterBinder
}
