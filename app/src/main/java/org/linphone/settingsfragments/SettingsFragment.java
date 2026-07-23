package org.linphone.settingsfragments;

import java.io.File;
import java.util.Objects;

import org.linphone.LinphoneUtils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import org.pniei.portal.R;

public class SettingsFragment extends PreferenceFragmentCompat {
	@SuppressLint("StaticFieldLeak")
    private static Context mContext;

	public static SettingsFragment newInstance(Context context) {
		mContext = context;
		return new SettingsFragment();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@NonNull
    @Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
		requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.preferences);
		removePreviousPreferencesFile();
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
		SharedPreferences.Editor editor = Objects.requireNonNull(getPreferenceManager().getSharedPreferences()).edit();
		editor.clear();
		editor.apply();

		File dir = new File(requireActivity().getFilesDir().getAbsolutePath() + "shared_prefs");
		LinphoneUtils.recursiveFileRemoval(dir);
	}

}