package org.linphone.settingsfragments;

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import org.pniei.portal.R;

public class AdvancedSettingsSubScreenFragment extends PreferenceFragmentCompat {
    public static final String PAGE_ID = "page_id";
    int index_next_key;

    public static AdvancedSettingsSubScreenFragment newInstance(String pageId) {
        AdvancedSettingsSubScreenFragment f = new AdvancedSettingsSubScreenFragment();
        Bundle args = new Bundle();
        args.putString(PAGE_ID, pageId);
        f.setArguments(args);
        return (f);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences);
        PreferenceCategory pCat = findPreference(getString(R.string.pref_key_compl_key));
        PreferenceScreen mPreferenceScreen;
        assert rootKey != null;
        if (rootKey.equals(getString(R.string.pref_key_working_compl_key))) {
            assert pCat != null;
            mPreferenceScreen = (PreferenceScreen) pCat.getPreference(0);
        } else /*if(root_key.equals(getString(R.string.pref_key_next_compl_key)))*/ {
            index_next_key = Integer.parseInt(rootKey.substring(rootKey.lastIndexOf("_") + 1));
            mPreferenceScreen = createPrefScreenNextKey();
            mPreferenceScreen.getPreference(0).setTitle(getString(R.string.key_next_settings_title) + " " + (index_next_key + 1));
        }
        getPreferenceManager().setPreferences(mPreferenceScreen);
    }

    private PreferenceScreen createPrefScreenNextKey() {
        PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(requireContext());

        Preference pref_key_n_name_komplect = new Preference(requireContext());
        pref_key_n_name_komplect.setKey(getString(R.string.pref_key_n_name_komplect_key));
        pref_key_n_name_komplect.setTitle(R.string.key_name_komplect_title);

        Preference pref_key_n_num_serial = new Preference(requireContext());
        pref_key_n_num_serial.setKey(getString(R.string.pref_key_n_num_serial_key));
        pref_key_n_num_serial.setTitle(R.string.key_num_serial_title);

        Preference pref_key_n_num_komplect = new Preference(requireContext());
        pref_key_n_num_komplect.setKey(getString(R.string.pref_key_n_num_komplect_key));
        pref_key_n_num_komplect.setTitle(R.string.key_num_komplect_title);

        /*Preference pref_key_n_num_of_keys = new Preference(getContext());
        pref_key_n_num_of_keys.setKey(getString(R.string.pref_key_n_num_of_keys_key));
        pref_key_n_num_of_keys.setTitle(R.string.key_num_of_keys_title);*/

        Preference pref_key_n_date_begin = new Preference(requireContext());
        pref_key_n_date_begin.setKey(getString(R.string.pref_key_n_date_begin_key));
        pref_key_n_date_begin.setTitle(R.string.key_date_begin_title);

        Preference pref_key_n_date_end = new Preference(requireContext());
        pref_key_n_date_end.setKey(getString(R.string.pref_key_n_date_end_key));
        pref_key_n_date_end.setTitle(R.string.key_date_end_title);

        PreferenceCategory preferenceCategory = new PreferenceCategory(requireContext());
        preferenceScreen.addPreference(preferenceCategory);
        preferenceCategory.addPreference(pref_key_n_name_komplect);
        preferenceCategory.addPreference(pref_key_n_num_serial);
        preferenceCategory.addPreference(pref_key_n_num_komplect);
        //preferenceCategory.addPreference(pref_key_n_num_of_keys);
        preferenceCategory.addPreference(pref_key_n_date_begin);
        preferenceCategory.addPreference(pref_key_n_date_end);

        return preferenceScreen;
    }
}
