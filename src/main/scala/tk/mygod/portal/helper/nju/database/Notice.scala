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

  @DatabaseField(generatedId = true)
  var id: Int = _

  @DatabaseField(uniqueCombo = true)
  var title: String = _

  @DatabaseField(uniqueCombo = true)
  var distributionTime: Long = _

  @DatabaseField(uniqueCombo = true)
  var url: String = _

  @DatabaseField
  var obsolete: Boolean = _

  @DatabaseField
  var read: Boolean = _

  override def equals(o: Any) = o match {
    case that: Notice => distributionTime == that.distributionTime && title == that.title && url == that.url
    case _ => false
  }
  override def hashCode = title.toUpperCase.hashCode ^ distributionTime.hashCode ^ url.hashCode
}
