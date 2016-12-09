package tk.mygod.portal.helper.nju.util

/**
  * Format values like a (b).
  * @author Mygod
  */
class DualFormatter(format1: String = "%s", format2: String = "%s") {
  var value1: String = _
  var value2: String = _
  override def toString: String = if (value1 == null)
    if (value2 == null) null else "(%s)".format(format2.format(value2)) else {
    val formatted1 = format1.format(value1)
    if (value2 == null) formatted1 else "%s (%s)".format(formatted1, format2.format(value2))
  }
}
