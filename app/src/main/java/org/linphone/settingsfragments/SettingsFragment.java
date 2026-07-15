package org.linphone.settingsfragments;

import java.io.File;
import org.linphone.LinphoneUtils;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.PreferenceScreen;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import androidx.preference.PreferenceFragmentCompat;

import org.pniei.dwface.biometry.BiometryPrefs;
import org.pniei.portal.R;
import org.pniei.portal.utils.PrefsUtils;

public class SettingsFragment extends PreferenceFragmentCompat {
	private static Context mContext;

	public static SettingsFragment newInstance(Context context) {
		mContext = context;
		return new SettingsFragment();
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
		getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        return super.onCreateView(inflater, container,savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.preferences);
		removePreviousPreferencesFile();

		if (!BiometryPrefs.ins().isInitBiometryLib()) {
			PreferenceScreen screen = findPreference(getString(R.string.pref_biometry_settings_key));
			screen.setVisible(false);
		}

		if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
			PreferenceScreen screen = findPreference(getString(R.string.pref_vpn_info_key));
			screen.setVisible(false);
		}
	}

	@Override
	public void onNavigateToScreen(PreferenceScreen preferenceScreen){
		AllSettingsScreenFragment fragment = AllSettingsScreenFragment.newInstance(mContext);
		Bundle args = new Bundle();
		args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, preferenceScreen.getKey());
		fragment.setArguments(args);
		getParentFragmentManager()
				.beginTransaction()
				.replace(getId(), fragment)
				.addToBackStack(null)
				.commit();
	}

	private void removePreviousPreferencesFile() {
		SharedPreferences.Editor editor = getPreferenceManager().getSharedPreferences().edit();
        editor.clear();
        editor.apply();

		File dir = new File(getActivity().getFilesDir().getAbsolutePath() + "shared_prefs");
		LinphoneUtils.recursiveFileRemoval(dir);
	}

}
