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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SparkBufferTest {
    @Test
    public void fillsInOrderUntilCapacity() {
        SparkBuffer b = new SparkBuffer(3);
        b.add(1f);
        b.add(2f);
        assertEquals(2, b.size());
        assertArrayEquals(new float[]{1f, 2f}, b.values(), 0f);
        assertEquals(2f, b.latest(), 0f);
    }

    @Test
    public void evictsOldestOnceFull_chronologicalOrderPreserved() {
        SparkBuffer b = new SparkBuffer(3);
        for (int i = 1; i <= 5; i++) {
            b.add(i);
        }
        assertEquals(3, b.size());
        // 1 and 2 evicted; oldest→newest is 3,4,5
        assertArrayEquals(new float[]{3f, 4f, 5f}, b.values(), 0f);
        assertEquals(5f, b.latest(), 0f);
        assertEquals(3f, b.min(), 0f);
        assertEquals(5f, b.max(), 0f);
    }

    @Test
    public void wrapsRepeatedlyWithoutDrift() {
        SparkBuffer b = new SparkBuffer(46);
        for (int i = 0; i < 1000; i++) {
            b.add(i);
        }
        float[] v = b.values();
        assertEquals(46, v.length);
        assertEquals(954f, v[0], 0f);   // 1000 - 46
        assertEquals(999f, v[45], 0f);
    }

    @Test
    public void clearResets() {
        SparkBuffer b = new SparkBuffer(4);
        b.add(7f);
        b.clear();
        assertTrue(b.isEmpty());
        assertEquals(0, b.values().length);
        assertTrue(Float.isNaN(b.latest()));
    }
}
