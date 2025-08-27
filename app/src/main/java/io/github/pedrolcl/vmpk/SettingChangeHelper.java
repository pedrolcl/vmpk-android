/* SPDX-License-Identifier: GPL-3.0-or-later */
/* Copyright © 2013–2025 Pedro López-Cabanillas. */

package io.github.pedrolcl.vmpk;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;

import java.util.Locale;

public class SettingChangeHelper {
	private static boolean mLastTheme = false;
	private static boolean mLastOutput = true;
	private static String mLastLang = null;
	// private static boolean mFullScreen = false;

	public static void changeSettingsCheck(Activity activity) {
		boolean newTheme = getCurrentTheme(activity);
		boolean newOutput = getCurrentOutput(activity);
		String newLang = getCurrentLanguage(activity);
		if (newTheme != mLastTheme || newOutput != mLastOutput || newLang != mLastLang) {
			Log.d("SettingChangeHelper", "changingSettings");
			// activity.recreate(); NO USAR, PODRIDO!
			activity.finish();
			activity.startActivity(new Intent(activity, activity.getClass()));
		}
	}

	public static void onMainActivityCreateApplySettings(Activity activity) {
		mLastTheme = getCurrentTheme(activity);
		mLastOutput = getCurrentOutput(activity);
		mLastLang = getCurrentLanguage(activity);
		Log.d("SettingChangeHelper", "onActivityCreateApplySettings");
		if (mLastTheme) {
			activity.setTheme(R.style.LightTheme);
		} else {
			activity.setTheme(R.style.DarkTheme);
		}
		Configuration config = new Configuration();
		Locale newLocale = new Locale(mLastLang);
		config.locale = newLocale;
		Locale.setDefault(newLocale);
		activity.getResources().updateConfiguration(config, null);
	}

	public static void onActivityCreateApplyTheme(Activity activity) {
		boolean theme = getCurrentTheme(activity);
		if (theme) {
			activity.setTheme(R.style.LightTheme);
		} else {
			activity.setTheme(R.style.DarkTheme);
		}
		Configuration config = new Configuration();
		config.locale = new Locale(getCurrentLanguage(activity));
		activity.getResources().updateConfiguration(config, null);
	}

	private static boolean getCurrentTheme(Activity activity) {
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
		return sharedPrefs.getBoolean("alternate_theme", false);
	}

	public static boolean getCurrentOutput(Activity activity) {
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
		return sharedPrefs.getBoolean("midi_output", true);
	}

	private static String getCurrentLanguage(Activity activity) {
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
		String defLang = activity.getResources().getString(R.string.default_language);
		if (mLastLang == null) {
			mLastLang = defLang;
		}
		return sharedPrefs.getString("lang", defLang);
	}

	// public static boolean getFullScreen(Activity activity) {
	// return mFullScreen;
	// }

}
