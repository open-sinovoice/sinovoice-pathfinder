package com.sinovoice.pathfinder;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class ImePreferences extends PreferenceActivity {
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We overwrite the title of the activity, as the default one is "Voice Search".
        setTitle(R.string.settings_name);
        
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.ime_preferences);
    }
}
