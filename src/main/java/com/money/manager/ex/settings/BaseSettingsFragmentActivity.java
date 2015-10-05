/*
 * Copyright (C) 2012-2015 The Android Money Manager Ex Project Team
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.money.manager.ex.settings;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.money.manager.ex.R;
import com.money.manager.ex.common.BaseFragmentActivity;

/**
 */
public class BaseSettingsFragmentActivity extends BaseFragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        setContentView(R.layout.settings_activity);
        setDisplayHomeAsUpEnabled(true);
    }

    protected void setSettingFragment(PreferenceFragment fragment) {
        getFragmentManager().beginTransaction()
                .replace(R.id.content, fragment)
                .commit();
    }
}