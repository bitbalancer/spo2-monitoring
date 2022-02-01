package info.plux.api.SpO2Monitoring.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import info.plux.api.SpO2Monitoring.R;
import info.plux.api.SpO2Monitoring.activities.MainActivity;


public class PreferencesFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener, Preference.OnPreferenceChangeListener {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings_pref);
        SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
        PreferenceScreen prefScreen = getPreferenceScreen();

        int count = prefScreen.getPreferenceCount();

       // Go through all of the preferences, and set up their preference summary.
        for (int i = 0; i < count; i++) {
            Preference p = prefScreen.getPreference(i);
        }
        Preference preference = findPreference(getString(R.string.pref_limit));
        preference.setOnPreferenceChangeListener(this);

    }


    //Make sure limit is an int
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Toast error = Toast.makeText(getContext(), "Please select a number.", Toast.LENGTH_SHORT);

        String limitKey = getString(R.string.pref_limit);
        if (preference.getKey().equals(limitKey)) {
            String stringLimit = (String) newValue;
            try {
                float limit = Float.parseFloat(stringLimit);

            } catch (NumberFormatException nfe) {
                error.show();
                return false;
            }
        }
        return true;
    }
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Figure out which preference was changed
        Preference preference = findPreference(key);
        if (null!= preference) {
            //Update summary for preference
            if((key == getString(R.string.pref_limit))){
               // SharedPreferences.Editor editor = sharedPreferences.edit();
                String limit = sharedPreferences.getString(preference.getKey(), String.valueOf(R.string.pref_limit_default));
                setPreferenceSummary(preference, limit);
                /*editor.putInt(String.valueOf(R.string.pref_limit),R.string.pref_limit_default);
                editor.commit();*/
                Toast.makeText(getContext(),"Limit saved",Toast.LENGTH_LONG).show();
            }
            else if((key == getString(R.string.pref_instruction))){
                SharedPreferences.Editor editor = sharedPreferences.edit();
                String instruction = sharedPreferences.getString(preference.getKey(), String.valueOf(R.string.pref_limit_default));
                setPreferenceSummary(preference, instruction);
                Toast.makeText(getContext(),"Instruction saved",Toast.LENGTH_LONG).show();
            }
        }
    }



    private void setPreferenceSummary(Preference preference, String value) {
        if (preference instanceof EditTextPreference) {
            // For EditTextPreferences, set the summary to the value's simple string representation.
            preference.setSummary(value);
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }
}
