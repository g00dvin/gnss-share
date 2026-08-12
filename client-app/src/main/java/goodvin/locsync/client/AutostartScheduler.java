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

    // Cooldown applied when the job re-arms itself. NETWORK_TYPE_ANY is level-triggered: rescheduling
    // with no latency while WiFi is still connected would make the job eligible again immediately and
    // loop (wasting JobScheduler quota + battery). A minimum latency bounds re-fires to periodic
    // no-op checks while connected, without delaying the prompt fire on a genuine connect (the
    // initial schedule() has no latency). It also gives a ~15-min self-heal if the service is killed.
    private static final long REARM_COOLDOWN_MS = 15 * 60 * 1000L;

    private AutostartScheduler() {}

    /** Prompt schedule (no latency) — fires as soon as a network is available. Idempotent. */
    public static void schedule(Context context) {
        scheduleInternal(context, 0);
    }

    /** Re-arm from inside the job with a cooldown to prevent a level-triggered reschedule loop. */
    public static void rearm(Context context) {
        scheduleInternal(context, REARM_COOLDOWN_MS);
    }

    // Scheduling with the same JOB_ID replaces any existing registration (idempotent).
    // Note: Android cancels all of an app's persisted jobs on force-stop; recovery then relies on
    // the app being reopened or BootReceiver re-arming — an inherent OS limitation, not fixable here.
    private static void scheduleInternal(Context context, long minLatencyMs) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            Log.w(TAG, "JobScheduler unavailable");
            return;
        }
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, new ComponentName(context, AutostartJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // fires on any network; we filter WiFi in the job
                .setPersisted(true);                               // survives reboot (needs RECEIVE_BOOT_COMPLETED)
        if (minLatencyMs > 0) {
            builder.setMinimumLatency(minLatencyMs);
        }
        int result = scheduler.schedule(builder.build());
        Log.d(TAG, "Scheduled autostart job (latency=" + minLatencyMs + "ms): "
                + (result == JobScheduler.RESULT_SUCCESS ? "ok" : "failed"));
    }

    public static void cancel(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null) {
            scheduler.cancel(JOB_ID);
            Log.d(TAG, "Cancelled autostart job");
        }
    }
}
