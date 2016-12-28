package com.nakedape.mixmaticlooppad;



import android.content.SharedPreferences;
import android.os.Bundle;
import android.app.Fragment;
import android.preference.Preference;
import android.preference.PreferenceFragment;


/**
 * A simple {@link Fragment} subclass.
 *
 */
public class LaunchPadPreferencesFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String PREF_BPM = "pref_bpm";
    public static final String PREF_TIME_SIG = "pref_time_signature";
    public static final String PREF_LAUNCH_MODE = "pref_launch_mode";
    public static final String PREF_LOOP_MODE = "pref_loop_mode";
    public static final String PREF_QUANTIZATION = "pref_quantization_mode";

    private SharedPreferences sharedPrefs;

    public LaunchPadPreferencesFragment() {
        // Required empty public constructor
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.launchpad_preferences);
        sharedPrefs = getPreferenceScreen().getSharedPreferences();
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);

        // Set initial summaries
        NumberPickerPreference bpmPref = (NumberPickerPreference) findPreference(PREF_BPM);
        bpmPref.setMax(200);
        bpmPref.setSummary(getString(R.string.pref_bpm_summary, sharedPrefs.getInt(PREF_BPM, 120)));

        Preference timeSigPref = findPreference(PREF_TIME_SIG);
        timeSigPref.setSummary(getString(R.string.pref_time_sig_summary, sharedPrefs.getString(PREF_TIME_SIG, "4")));

        Preference launchPref = findPreference(PREF_LAUNCH_MODE);
        switch (sharedPrefs.getString(PREF_LAUNCH_MODE, "0")) {
            case "0":
                launchPref.setSummary(R.string.gate_summary);
                break;
            case "1":
                launchPref.setSummary(R.string.trigger_summary);
                break;
        }

        setQuantizationPrefSummary();
    }


    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (this.isDetached()) return;
        Preference pref = findPreference(key);
        if (key.equals(PREF_BPM)) {
            // Set summary to be the user-description for the selected value
            pref.setSummary(getString(R.string.pref_bpm_summary, sharedPrefs.getInt(PREF_BPM, 120)));
        }
        else if (key.equals(PREF_TIME_SIG)){
            pref.setSummary(getString(R.string.pref_time_sig_summary, sharedPrefs.getString(PREF_TIME_SIG, "4")));
        }
        else if (key.equals(PREF_LAUNCH_MODE)){
            switch (sharedPrefs.getString(PREF_LAUNCH_MODE, "0")) {
                case "0":
                    pref.setSummary(R.string.gate_summary);
                    setQuantizationPrefSummary();
                    break;
                case "1":
                    pref.setSummary(R.string.trigger_summary);
                    setQuantizationPrefSummary();
                    break;
            }
        }
        else if (key.equals(PREF_QUANTIZATION)){
            setQuantizationPrefSummary();
        }
    }

    private void setQuantizationPrefSummary(){
        Preference quantPref = findPreference(PREF_QUANTIZATION);
        if (sharedPrefs.getString(PREF_LAUNCH_MODE, "0").equals("0")) {
            quantPref.setSummary(R.string.quant_gate_mode_summary);
            quantPref.setEnabled(false);
        } else {
            quantPref.setEnabled(true);
            switch (sharedPrefs.getString(PREF_QUANTIZATION, "0")) {
                case "0":
                    quantPref.setSummary(R.string.quant_none_summary);
                    break;
                case "1":
                    quantPref.setSummary(R.string.quant_beat_summary);
                    break;
                case "2":
                    quantPref.setSummary(R.string.quant_half_summary);
                    break;
                case "4":
                    quantPref.setSummary(R.string.quant_quarter_summary);
                    break;
                case "5":
                    quantPref.setSummary(R.string.quant_bar_summary);
                    break;
            }
        }
    }
}
