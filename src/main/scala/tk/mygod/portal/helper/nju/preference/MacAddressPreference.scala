package tk.mygod.portal.helper.nju.preference

import java.net.NetworkInterface

import android.content.Context
import android.util.AttributeSet
import tk.mygod.preference.EditTextPreference

import scala.collection.JavaConversions._

/**
  * @author Mygod
  */
object MacAddressPreference {
  val default = enumerationAsScalaIterator(NetworkInterface.getNetworkInterfaces).map(interface => {
    val mac = interface.getHardwareAddress
    if (mac == null) null
    else "%02X:%02X:%02X:%02X:%02X:%02X".format(mac(0), mac(1), mac(2), mac(3), mac(4), mac(5))
  }).filter(_ != null).mkString("\n")
}

class MacAddressPreference(context: Context, attrs: AttributeSet) extends EditTextPreference(context, attrs) {
  setDefaultValue(MacAddressPreference.default)
}
