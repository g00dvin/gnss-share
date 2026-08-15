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

/**
 * Fixed-capacity ring buffer of {@code float} samples backing {@link SparklineView}. Pure Java (no
 * Android types) so the windowing logic is unit-testable. {@link #values()} always returns samples
 * oldest→newest, which is the order the sparkline path is drawn in.
 */
public final class SparkBuffer {
    private final float[] ring;
    private int size;      // number of valid samples (<= capacity)
    private int head;      // index of the oldest sample when full; next write slot otherwise

    public SparkBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.ring = new float[capacity];
    }

    public int capacity() {
        return ring.length;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Append one sample, evicting the oldest once capacity is reached. */
    public void add(float v) {
        if (size < ring.length) {
            ring[(head + size) % ring.length] = v;
            size++;
        } else {
            ring[head] = v;
            head = (head + 1) % ring.length;
        }
    }

    public void clear() {
        size = 0;
        head = 0;
    }

    /** Samples in chronological order (oldest first). Length equals {@link #size()}. */
    public float[] values() {
        float[] out = new float[size];
        for (int i = 0; i < size; i++) {
            out[i] = ring[(head + i) % ring.length];
        }
        return out;
    }

    public float min() {
        if (size == 0) return 0f;
        float m = Float.POSITIVE_INFINITY;
        for (int i = 0; i < size; i++) {
            m = Math.min(m, ring[(head + i) % ring.length]);
        }
        return m;
    }

    public float max() {
        if (size == 0) return 0f;
        float m = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < size; i++) {
            m = Math.max(m, ring[(head + i) % ring.length]);
        }
        return m;
    }

    /** Most recent sample, or {@code NaN} when empty. */
    public float latest() {
        if (size == 0) return Float.NaN;
        return ring[(head + size - 1) % ring.length];
    }
}
