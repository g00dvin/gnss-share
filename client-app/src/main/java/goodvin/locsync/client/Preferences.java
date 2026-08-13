/*
 * Copyright © 2025-2026 Dezz (https://github.com/DezzK)
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

package goodvin.locsync.client;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {
    private static final String PREF_IS_SERVICE_ENABLED = "isServiceEnabled";
    private static final String PREF_AUTO_DISCOVER = "autoDiscover";
    private static final String LEGACY_USE_GATEWAY_IP = "useGatewayIp";
    private static final String PREF_SERVER_ADDRESS = "serverAddress";
    private static final String PREF_STATIC_JITTER_ENABLED = "staticJitterEnabled";
    private static final String PREF_LAST_AUTOSTART_SOURCE = "lastAutostartSource";
    private static final String PREF_LAST_AUTOSTART_RESULT = "lastAutostartResult";
    private static final String PREF_LAST_AUTOSTART_TIME = "lastAutostartTime";

    // SharedPreferences helper methods
    public static void setServiceEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(PREF_IS_SERVICE_ENABLED, enabled).apply();
    }

    public static boolean serviceEnabled(Context context) {
        return getPrefs(context).getBoolean(PREF_IS_SERVICE_ENABLED, false);
    }

    public static void setAutoDiscover(Context context, boolean value) {
        getPrefs(context).edit().putBoolean(PREF_AUTO_DISCOVER, value).apply();
    }

    public static boolean autoDiscover(Context context) {
        SharedPreferences prefs = getPrefs(context);
        // One-time migration from the old "useGatewayIp" key (same semantics: both true = auto/gateway).
        if (!prefs.contains(PREF_AUTO_DISCOVER) && prefs.contains(LEGACY_USE_GATEWAY_IP)) {
            boolean legacy = prefs.getBoolean(LEGACY_USE_GATEWAY_IP, true);
            prefs.edit().putBoolean(PREF_AUTO_DISCOVER, legacy).remove(LEGACY_USE_GATEWAY_IP).apply();
            return legacy;
        }
        return prefs.getBoolean(PREF_AUTO_DISCOVER, true);
    }

    public static void setServerAddress(Context context, String value) {
        getPrefs(context).edit().putString(PREF_SERVER_ADDRESS, value).apply();
    }

    public static String serverAddress(Context context) {
        return getPrefs(context).getString(PREF_SERVER_ADDRESS, "192.168.43.1");
    }

    public static void setStaticJitterEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(PREF_STATIC_JITTER_ENABLED, enabled).apply();
    }

    public static boolean staticJitterEnabled(Context context) {
        return getPrefs(context).getBoolean(PREF_STATIC_JITTER_ENABLED, false);
    }

    /**
     * Records the outcome of an autostart trigger so it survives a reboot (device-protected
     * storage) and can be shown in the UI — useful for diagnosing whether/what fired on head
     * units where logcat rolls across reboots.
     */
    public static void setLastAutostart(Context context, String source, String result) {
        getPrefs(context).edit()
                .putString(PREF_LAST_AUTOSTART_SOURCE, source)
                .putString(PREF_LAST_AUTOSTART_RESULT, result)
                .putLong(PREF_LAST_AUTOSTART_TIME, System.currentTimeMillis())
                .apply();
    }

    public static String lastAutostartSource(Context context) {
        return getPrefs(context).getString(PREF_LAST_AUTOSTART_SOURCE, null);
    }

    public static String lastAutostartResult(Context context) {
        return getPrefs(context).getString(PREF_LAST_AUTOSTART_RESULT, null);
    }

    public static long lastAutostartTime(Context context) {
        return getPrefs(context).getLong(PREF_LAST_AUTOSTART_TIME, 0);
    }

    private static final String PREF_DEBUG_LOGGING = "debugLoggingEnabled";

    public static void setDebugLoggingEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(PREF_DEBUG_LOGGING, enabled).apply();
    }

    public static boolean debugLoggingEnabled(Context context) {
        return getPrefs(context).getBoolean(PREF_DEBUG_LOGGING, false);
    }

    private static final String PREF_METRICS_ENABLED = "metricsEnabled";

    public static void setMetricsEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(PREF_METRICS_ENABLED, enabled).apply();
    }

    public static boolean metricsEnabled(Context context) {
        return getPrefs(context).getBoolean(PREF_METRICS_ENABLED, false);
    }

    private static SharedPreferences getPrefs(Context context) {
        final Context deviceContext = context.getApplicationContext().createDeviceProtectedStorageContext();
        return deviceContext.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }
}
