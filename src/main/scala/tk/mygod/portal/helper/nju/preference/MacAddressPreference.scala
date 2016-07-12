package tk.mygod.portal.helper.nju.preference

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import eu.chainfire.libsuperuser.Shell
import tk.mygod.os.Build
import tk.mygod.preference.EditTextPreference

import scala.collection.JavaConversions._

/**
  * @author Mygod
  */
object MacAddressPreference {
  private final val COMMAND = "for if in /sys/class/net/*; do cat $if/address; done"
  private final val MAC_ADDRESS_MATCHER = "([0-9A-Fa-f]{2}:){5}([0-9A-Fa-f]{2})".r  // filter invalid mac addresses
  private var _default: String = _
  def default(quiet: Boolean = true) = {
    if (TextUtils.isEmpty(_default)) {
      val result = if (quiet || Build.version < 24) Shell.SH.run(COMMAND) else Shell.SU.run(COMMAND)
      if (result != null)
        _default = result.map(line => MAC_ADDRESS_MATCHER.findFirstIn(line).orNull).filter(_ != null).mkString("\n")
    }
    _default
  }
}

class MacAddressPreference(context: Context, attrs: AttributeSet) extends EditTextPreference(context, attrs) {
  setDefaultValue(MacAddressPreference.default())
}
