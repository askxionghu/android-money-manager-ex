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
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.money.manager.ex.core;

/**
 * Generate the number pattern for each currency.
 *
 * Created by Alen Siljak on 15/09/2015.
 */
public class NumericPatternGenerator {

//    public void setCurrency(TableCurrencyFormats currency) {
//
//    }

    public static String getPattern(int decimals) {
        //String pattern = "###G###G###G###D####";
//        String pattern = "###G###G###G###";
//        pattern = pattern.replace("G", groupSeparator);
        
        String pattern = "#,##0";

        if (decimals > 0) {
            pattern += ".";

            for (int i = 0; i < decimals; i++) {
                pattern += "0";
            }
        }

        return pattern;
    }
}