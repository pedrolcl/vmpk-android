/* SPDX-License-Identifier: GPL-3.0-or-later */
/* Copyright © 2013–2025 Pedro López-Cabanillas. */

package io.github.pedrolcl.vmpk;

import static android.content.Context.MIDI_SERVICE;
import static android.content.pm.PackageManager.FEATURE_MIDI;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.prefs.PreferenceChangeEvent;

public class SettingsFragment extends PreferenceFragment
		implements OnSharedPreferenceChangeListener {

	private static final int PICK_SOUNDFONT = 1001;
	private static final String KEY_SOUNDFONT = "soundfont_file";
	private static final String KEY_SOUNDFONT_CLEAR = "soundfont_clear";

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

		ListPreference protocol = (ListPreference) findPreference( "ip_protocol" );
		EditTextPreference address =  (EditTextPreference) findPreference( "ip_address" );

		if (protocol != null && address != null) {
			protocol.setOnPreferenceChangeListener((preference, newValue) -> {
				String ipproto = (String) newValue;
				if (ipproto == getString(R.string.default_protocol)) {
					address.setText(getString(R.string.default_address));
				} else {
					address.setText(getString(R.string.default_v6_address));
				}
				return true;
			});
		}

		// Soundfont preference: open document picker and allow clearing
		Preference sfPref = findPreference(KEY_SOUNDFONT);
		if (sfPref != null) {
			sfPref.setOnPreferenceClickListener(preference -> {
				Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
				intent.addCategory(Intent.CATEGORY_OPENABLE);
				// allow any file but we'll check extension on result
				intent.setType("*/*");
				startActivityForResult(Intent.createChooser(intent, getString(R.string.select_soundfont)), PICK_SOUNDFONT);
				return true;
			});
			// ensure summary is up to date
			updatePrefSummary(sfPref);
		}

		Preference sfClear = findPreference(KEY_SOUNDFONT_CLEAR);
		if (sfClear != null) {
			sfClear.setOnPreferenceClickListener(preference -> {
				// remove stored URI and release persisted permission if any
				SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
				String uriStr = sp.getString(KEY_SOUNDFONT, null);
				if (uriStr != null) {
					try {
						Uri uri = Uri.parse(uriStr);
						final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
						getActivity().getContentResolver().releasePersistableUriPermission(uri, takeFlags);
					} catch (Exception ex) {
						// ignore if we can't release
					}
				}
				sp.edit().remove(KEY_SOUNDFONT).commit();
				Preference p = findPreference(KEY_SOUNDFONT);
				updatePrefSummary(p);
				return true;
			});
		}

		maybeRemoveSystemMidiOption();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == PICK_SOUNDFONT && resultCode == Activity.RESULT_OK && data != null) {
			Uri uri = data.getData();
			if (uri != null) {
				try {
					final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
					// persist permission for future access
					getActivity().getContentResolver().takePersistableUriPermission(uri, takeFlags);
				} catch (Exception ex) {
					// ignore
				}
				SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
				sp.edit().putString(KEY_SOUNDFONT, uri.toString()).commit();
				Preference pref = findPreference(KEY_SOUNDFONT);
				updatePrefSummary(pref);
			}
		}
	}

	private void maybeRemoveSystemMidiOption() {
		if (getActivity().getPackageManager().hasSystemFeature(FEATURE_MIDI)
				&& getActivity().getSystemService(MIDI_SERVICE) != null) {
			return;
		}

		ListPreference midiOutputModePref = (ListPreference) findPreference("midi_output_mode");
		if (midiOutputModePref != null) {
			ArrayList<CharSequence> entries = new ArrayList<>(Arrays.asList(midiOutputModePref.getEntries()));
			ArrayList<CharSequence> values = new ArrayList<>(Arrays.asList(midiOutputModePref.getEntryValues()));

			int systemMidiIndex = -1;
			final String systemMidiValue = Integer.toString(SettingChangeHelper.MIDI_OUTPUT_MODE_SYSTEM);
			for (int i = 0; i < values.size(); i++) {
				if (values.get(i).equals(systemMidiValue)) {
					systemMidiIndex = i;
					break;
				}
			}

			if (systemMidiIndex >= 0) {
				entries.remove(systemMidiIndex);
				values.remove(systemMidiIndex);
				midiOutputModePref.setEntries(entries.toArray(new CharSequence[]{}));
				midiOutputModePref.setEntryValues(values.toArray(new CharSequence[]{}));
			}
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
		if (pref == null) return;

		if (pref instanceof ListPreference) {
			ListPreference listPref = (ListPreference) pref;
			pref.setSummary(listPref.getEntry());
		} else if (pref instanceof EditTextPreference) {
			EditTextPreference editPref = (EditTextPreference) pref;
			pref.setSummary(editPref.getText());
		} else if (pref instanceof NumberPickerDialogPreference) {
			NumberPickerDialogPreference numPick = (NumberPickerDialogPreference) pref;
			pref.setSummary(String.valueOf(numPick.getValue()));
		} else if (KEY_SOUNDFONT.equals(pref.getKey())) {
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
			String uriStr = sp.getString(KEY_SOUNDFONT, null);
			if (uriStr == null) {
				pref.setSummary(getString(R.string.summary_synth));
				return;
			}
			String display = uriStr;
			try {
				Uri uri = Uri.parse(uriStr);
				String name = queryDisplayName(getActivity(), uri);
				if (name != null) display = name;
			} catch (Exception ex) {
				// fall back to uri string
			}
			pref.setSummary(display);
		}
	}

	private static String queryDisplayName(Context context, Uri uri) {
		Cursor cursor = null;
		try {
			ContentResolver cr = context.getContentResolver();
			cursor = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
			if (cursor != null && cursor.moveToFirst()) {
				int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
				if (idx >= 0) return cursor.getString(idx);
			}
		} catch (Exception ex) {
			// ignore
		} finally {
			if (cursor != null) cursor.close();
		}
		return null;
	}
}
