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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

/**
 * Schedules/cancels the persisted network job that auto-starts the client on WiFi connect.
 *
 * <p>Why JobScheduler instead of a manifest BroadcastReceiver: apps targeting API 26+ cannot
 * receive the WiFi/connectivity implicit broadcasts via the manifest (not on the exemption list),
 * so a receiver would never fire. A persisted network job is the supported replacement — and,
 * because {@code setPersisted(true)} makes the system re-register it across reboots, it does not
 * depend on {@code BOOT_COMPLETED} being delivered (which is itself unreliable on some head units).
 */
public final class AutostartScheduler {
    private static final String TAG = "AutostartScheduler";
    private static final int JOB_ID = 1001;

    private AutostartScheduler() {}

    /** Idempotent: scheduling with the same JOB_ID replaces any existing registration. */
    public static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            Log.w(TAG, "JobScheduler unavailable");
            return;
        }
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, AutostartJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // fires on any network; we filter WiFi in the job
                .setPersisted(true)                                // survives reboot (needs RECEIVE_BOOT_COMPLETED)
                .build();
        int result = scheduler.schedule(job);
        Log.d(TAG, "Scheduled autostart job: " + (result == JobScheduler.RESULT_SUCCESS ? "ok" : "failed"));
    }

    public static void cancel(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null) {
            scheduler.cancel(JOB_ID);
            Log.d(TAG, "Cancelled autostart job");
        }
    }
}
