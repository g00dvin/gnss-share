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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AndroidSystemStatsReaderTest {
    @Test
    public void parsesUtimePlusStime() {
        // Fields: 1=pid 2=(comm) 3=state ... 14=utime 15=stime ...
        String line = "1234 (my app) S 1 1234 1234 0 -1 0 0 0 0 0 500 250 0 0 20 0 1 0 999";
        assertEquals(750L, AndroidSystemStatsReader.parseProcSelfStatCpuJiffies(line));
    }

    @Test
    public void returnsMinusOneOnGarbage() {
        assertEquals(-1L, AndroidSystemStatsReader.parseProcSelfStatCpuJiffies("garbage"));
    }
}
