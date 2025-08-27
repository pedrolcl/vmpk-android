/* SPDX-License-Identifier: GPL-3.0-or-later */
/* Copyright © 2013–2025 Pedro López-Cabanillas. */

package io.github.pedrolcl.vmpk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

public class SettingsFragment extends PreferenceFragment
		implements OnSharedPreferenceChangeListener {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
			initSummary(getPreferenceScreen().getPreference(i));
		}
		// Set up a listener whenever a key changes
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

		Preference button = (Preference) findPreference("prefs_reset");
		if (button != null) {
			button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					new AlertDialog.Builder(getActivity())
							.setTitle(R.string.resetdlg_title)
							.setMessage(R.string.resetdlg_message)
							.setNegativeButton(android.R.string.cancel, null)
							.setPositiveButton(android.R.string.ok, new OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									Activity a = getActivity();
									PreferenceManager.getDefaultSharedPreferences(a)
											.edit()
											.clear()
											.commit();
									PreferenceManager.setDefaultValues(a, R.xml.preferences, true);
									a.finish();
								}
							}).show();
					return true;
				}
			});
		}
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		Preference pref = findPreference(key);
		updatePrefSummary(pref);
	}

	private void initSummary(Preference p) {
		if (p instanceof PreferenceCategory) {
			PreferenceCategory pCat = (PreferenceCategory) p;
			for (int i = 0; i < pCat.getPreferenceCount(); i++) {
				initSummary(pCat.getPreference(i));
			}
		} else {
			updatePrefSummary(p);
		}
	}

	private void updatePrefSummary(Preference pref) {
		if (pref instanceof ListPreference) {
			ListPreference listPref = (ListPreference) pref;
			pref.setSummary(listPref.getEntry());
		} else if (pref instanceof EditTextPreference) {
			EditTextPreference editPref = (EditTextPreference) pref;
			pref.setSummary(editPref.getText());
		} else if (pref instanceof NumberPickerDialogPreference) {
			NumberPickerDialogPreference numPick = (NumberPickerDialogPreference) pref;
			pref.setSummary(String.valueOf(numPick.getValue()));
		}
	}
}
