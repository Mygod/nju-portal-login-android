package tk.mygod.portal.helper.nju.database

import android.text.{Html, Spanned}
import be.mygod.os.Build
import com.j256.ormlite.field.DatabaseField
import org.json.JSONObject

/**
  * @author Mygod
  */
object Notice {
  final val DISTRIBUTION_TIME = "distributionTime"
}

//noinspection HashCodeUsesVar
final class Notice {
  def this(obj: JSONObject) {
    this()
    title = obj.getString("title")
    distributionTime = obj.getLong("disttime")
    url = obj.optString("url", null)
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

  //noinspection ScalaDeprecation
  def formattedTitle: Spanned = if (Build.version >= 24)
    Html.fromHtml(title, Html.FROM_HTML_OPTION_USE_CSS_COLORS | Html.FROM_HTML_MODE_COMPACT) else Html.fromHtml(title)

  override def equals(o: Any): Boolean = o match {
    case that: Notice => distributionTime == that.distributionTime && title == that.title && url == that.url
    case _ => false
  }
  override def hashCode: Int = {
    var result = distributionTime.hashCode
    if (title != null) result ^= title.hashCode
    if (url != null) result ^= url.hashCode
    result
  }
}
