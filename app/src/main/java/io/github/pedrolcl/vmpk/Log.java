/* SPDX-License-Identifier: GPL-3.0-or-later */
/* Copyright © 2013–2025 Pedro López-Cabanillas. */

package io.github.pedrolcl.vmpk;

public class Log {
    static final boolean LOG = false;

    public static void e(String tag, String msg) {
        if (LOG) android.util.Log.e(tag, msg);
    }
    public static void e(String tag, String msg, Exception e) {
        if (LOG)  android.util.Log.e(tag, msg, e);
    }
    public static void d(String tag, String msg) {
        if (LOG) android.util.Log.d(tag, msg);
    }
    public static void d(String tag, String msg, Exception e) {
        if (LOG) android.util.Log.d(tag, msg, e);
    }
}
