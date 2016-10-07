package tk.mygod.portal.helper.nju.preference

import android.app.AlertDialog.Builder
import android.content.DialogInterface
import be.mygod.preference.EditTextPreferenceDialogFragment
import tk.mygod.portal.helper.nju.R

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
