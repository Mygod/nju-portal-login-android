package tk.mygod.portal.helper.nju.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper
import com.j256.ormlite.dao.Dao
import com.j256.ormlite.support.ConnectionSource
import com.j256.ormlite.table.TableUtils
import tk.mygod.portal.helper.nju.app

/**
  * @author Mygod
  */
object DatabaseManager {
  private lazy val instance = new DatabaseManager(app)
  lazy val noticeDao: Dao[Notice, Int] = instance.getDao(classOf[Notice])
}

final class DatabaseManager(context: Context) extends OrmLiteSqliteOpenHelper(context, "data.db", null, 1) {
  def onCreate(database: SQLiteDatabase, connectionSource: ConnectionSource): Unit =
    TableUtils.createTable(connectionSource, classOf[Notice])
  def onUpgrade(database: SQLiteDatabase, connectionSource: ConnectionSource, oldVersion: Int, newVersion: Int): Unit = ()
}
