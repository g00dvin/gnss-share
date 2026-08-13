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

package goodvin.locsync.shared;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;

/** Appends metrics rows to dir/metrics-<app>.csv; writes the header once; caps file size. */
public final class MetricsCsvWriter {
    private static final String TAG = "MetricsCsvWriter";
    private final File file;
    private final long maxBytes;

    public MetricsCsvWriter(File dir, String appName, long maxBytes) {
        this.file = fileFor(dir, appName);
        this.maxBytes = maxBytes;
    }

    public static File fileFor(File dir, String appName) {
        return new File(dir, "metrics-" + appName + ".csv");
    }

    public File getFile() {
        return file;
    }

    public synchronized void append(String header, String row) {
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            boolean needHeader = !file.exists() || file.length() == 0;
            if (file.exists() && file.length() > maxBytes) {
                try (FileWriter fw = new FileWriter(file, false)) {  // truncate
                    fw.append(header).append('\n');
                }
                needHeader = false;
            }
            try (FileWriter fw = new FileWriter(file, true)) {       // append
                if (needHeader) fw.append(header).append('\n');
                fw.append(row).append('\n');
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to write metrics CSV", e);
        }
    }
}
