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

package dezz.gnssshare.client;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * Fires when a network becomes available (scheduled by {@link AutostartScheduler}). Starts the
 * client service if the head unit is on WiFi, the service is enabled, and it isn't already
 * running — then re-arms itself (while still enabled) so it keeps catching future connects and
 * survives a service kill. Repeat fires are cheap no-ops thanks to the already-running guard.
 */
public class AutostartJobService extends JobService {
    private static final String TAG = "AutostartJobService";

    @Override
    public boolean onStartJob(JobParameters params) {
        boolean wifiConnected = isWifiConnected(this);
        boolean serviceEnabled = GNSSClientService.isServiceEnabled(this);
        boolean serviceRunning = GNSSClientService.isServiceRunning();

        AutostartPolicy.Decision decision =
                AutostartPolicy.decide(wifiConnected, serviceEnabled, serviceRunning);
        Preferences.setLastAutostart(this, "wifi_job", decision.name());
        Log.d(TAG, "Network job fired: " + decision.name()
                + " (wifi=" + wifiConnected + ", enabled=" + serviceEnabled + ", running=" + serviceRunning + ")");

        if (decision == AutostartPolicy.Decision.START) {
            Log.i(TAG, "Auto-starting GNSS client service from network job");
            ContextCompat.startForegroundService(this, new Intent(this, GNSSClientService.class));
        }

        // Re-arm while still enabled so future WiFi connects (or a service kill) are caught again.
        // A one-shot network job completes after this run; without re-scheduling it would not fire
        // again. If the user disabled the service, leave it cancelled.
        if (serviceEnabled) {
            AutostartScheduler.schedule(this);
        }

        return false; // work finished synchronously; no background thread needed
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // ask the system to reschedule if it stopped us before completion
    }

    private static boolean isWifiConnected(Context context) {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        if (cm == null) {
            return false;
        }
        Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }
}
