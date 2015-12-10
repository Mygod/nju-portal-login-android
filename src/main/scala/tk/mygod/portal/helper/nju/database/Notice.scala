package tk.mygod.portal.helper.nju.database

import com.j256.ormlite.field.DatabaseField

/**
  * @author Mygod
  */
class Notice {
  def this(o: Map[String, Any]) {
    this()
    title = o("title").toString
    distributionTime = o("disttime").asInstanceOf[BigInt].toLong
    url = o("url").toString
  }

  @DatabaseField
  var title: String = _

  @DatabaseField(id = true)
  var distributionTime: Long = _

  @DatabaseField
  var url: String = _

  @DatabaseField
  var obsolete: Boolean = _

  @DatabaseField
  var read: Boolean = _
}
