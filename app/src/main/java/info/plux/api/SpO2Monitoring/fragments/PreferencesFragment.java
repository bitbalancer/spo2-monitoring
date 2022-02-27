package info.plux.api.SpO2Monitoring.fragments;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import info.plux.api.SpO2Monitoring.R;
import info.plux.api.SpO2Monitoring.activities.MainActivity;
import info.plux.api.SpO2Monitoring.ui.main.ColorFragment;


public class PreferencesFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener, Preference.OnPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings_pref);

        SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
        PreferenceScreen prefScreen = getPreferenceScreen();
        int count = prefScreen.getPreferenceCount();

        // Go through all of the preferences, and set up their preference summary.
        Preference preference; String value;
        for (int i = 0; i < count; i++) {
            preference = prefScreen.getPreference(i);
            value = sharedPreferences.getString(preference.getKey(), "");
            setPreferenceSummary(preference, value);
        }

        // Set up onChangeListener for preferences
        Preference preferenceLimit = findPreference(getString(R.string.pref_limit));
        preferenceLimit.setOnPreferenceChangeListener(this);
        Preference preferenceInstr = findPreference(getString(R.string.pref_instruction));
        preferenceInstr.setOnPreferenceChangeListener(this);

    }

    //Make sure limit is an int
    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
        Toast error = Toast.makeText(getContext(), "Please select a number.", Toast.LENGTH_SHORT);
        Toast limitOutOfRange = Toast.makeText(getContext(), "Please select a number in range of 0 and 100.", Toast.LENGTH_SHORT);


        String limitKey = getString(R.string.pref_limit);
        if (preference.getKey().equals(limitKey)) {
            String newLimit = (String) newValue;
            // check if limit is in range (0-99)

            try {
                //check if input is an int
                int limit = Integer.parseInt(newLimit);
            } catch (NumberFormatException nfe) {
                // if not an int show error and do not save value
                error.show();
                return false;
            }
            if (Integer.parseInt(newLimit) > 0 && Integer.parseInt(newLimit) < 100) {
                return true;
            } else {
                limitOutOfRange.show();
                return false;
            }

        }
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Figure out which preference was changed
        Preference preference = findPreference(key);
        if (null != preference) {
            //Update summary for preference
            if ((key.equals(getString(R.string.pref_limit)))) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                // Set summary for new limit
                String limit = sharedPreferences.getString(preference.getKey(), getString(R.string.pref_limit_default));
                setPreferenceSummary(preference, String.valueOf(limit));
                // Save limit as preference
                editor.putInt(String.valueOf(R.string.pref_limit), R.string.pref_limit_default);
                editor.commit();
                Toast.makeText(getContext(), "Limit saved", Toast.LENGTH_SHORT).show();
            } else if ((key.equals(getString(R.string.pref_instruction)))) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                // Set summary for new instruction
                String instruction = sharedPreferences.getString(preference.getKey(), String.valueOf(R.string.pref_instr_default));
                setPreferenceSummary(preference, instruction);
                // Save instruction as preference
                editor.putString(String.valueOf(R.string.pref_instruction), String.valueOf(R.string.pref_instr_default));
                editor.commit();
                Toast.makeText(getContext(), "Instruction saved", Toast.LENGTH_SHORT).show();

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
