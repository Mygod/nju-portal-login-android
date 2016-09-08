package tk.mygod.portal.helper.nju.preference

import android.app.AlertDialog.Builder
import android.content.DialogInterface
import tk.mygod.os.Build
import tk.mygod.portal.helper.nju.R
import tk.mygod.preference.EditTextPreferenceDialogFragment

/**
  * @author Mygod
  */
class MacAddressPreferenceDialogFragment extends EditTextPreferenceDialogFragment {
  private lazy val preference = getPreference.asInstanceOf[MacAddressPreference]
  override def onPrepareDialogBuilder(builder: Builder) {
    super.onPrepareDialogBuilder(builder)
    builder.setNeutralButton(R.string.settings_misc_local_mac_address_auto_detect, ((_, _) => {
      val value = MacAddressPreference.default(false)
      if (preference.callChangeListener(value)) preference.setText(value)
    }): DialogInterface.OnClickListener)
  }
}
