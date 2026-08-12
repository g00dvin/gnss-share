/*
 * Copyright © 2026 Dezz (https://github.com/DezzK)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package goodvin.locsync.shared;

/**
 * Log gate: debug/info/verbose are emitted only when debug logging is enabled (default off, so
 * normal runs produce warnings + errors only). Warnings and errors always go through android.util.Log
 * directly at their call sites.
 */
public final class AppLog {
    private static volatile boolean debug = false;

    private AppLog() {}

    public static void setDebug(boolean enabled) {
        debug = enabled;
    }

    public static boolean isDebug() {
        return debug;
    }

    public static void d(String tag, String msg) {
        if (debug) android.util.Log.d(tag, msg);
    }

    public static void i(String tag, String msg) {
        if (debug) android.util.Log.i(tag, msg);
    }

    public static void v(String tag, String msg) {
        if (debug) android.util.Log.v(tag, msg);
    }
}
