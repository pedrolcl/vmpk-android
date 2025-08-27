/* SPDX-License-Identifier: GPL-3.0-or-later */
/* Copyright © 2013–2025 Pedro López-Cabanillas. */

package io.github.pedrolcl.vmpk;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;

public class SettingsActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SettingChangeHelper.onActivityCreateApplyTheme(this);
        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

}
