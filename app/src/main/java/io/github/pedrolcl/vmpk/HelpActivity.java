/* SPDX-License-Identifier: GPL-3.0-or-later */
/* Copyright © 2013–2025 Pedro López-Cabanillas. */

package io.github.pedrolcl.vmpk;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

public class HelpActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SettingChangeHelper.onActivityCreateApplyTheme(this);
		setContentView(R.layout.activity_help);

		ActionBar actionBar = getActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);

		WebView view = (WebView) findViewById(R.id.webViewHelp);
		view.getSettings().setJavaScriptEnabled(false);
		String lang = getResources().getConfiguration().locale.getLanguage();
		view.loadUrl(String.format("file:///android_asset/help-%s.html", lang));
	}

}
