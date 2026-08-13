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
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class MetricsCsvWriterTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void writesHeaderOnceThenRows() throws Exception {
        File dir = tmp.newFolder();
        MetricsCsvWriter w = new MetricsCsvWriter(dir, "client", 1_000_000);
        w.append("a,b", "1,2");
        w.append("a,b", "3,4");
        List<String> lines = Files.readAllLines(w.getFile().toPath());
        assertEquals(3, lines.size());
        assertEquals("a,b", lines.get(0));
        assertEquals("1,2", lines.get(1));
        assertEquals("3,4", lines.get(2));
    }

    @Test
    public void fileNameIncludesAppName() {
        File dir = tmp.getRoot();
        assertEquals("metrics-server.csv", MetricsCsvWriter.fileFor(dir, "server").getName());
    }

    @Test
    public void truncatesWhenOverCap() throws Exception {
        File dir = tmp.newFolder();
        MetricsCsvWriter w = new MetricsCsvWriter(dir, "client", 40); // tiny cap
        for (int i = 0; i < 50; i++) w.append("h1,h2", i + ",row");
        List<String> lines = Files.readAllLines(w.getFile().toPath());
        assertEquals("h1,h2", lines.get(0));                 // header preserved
        assertTrue(w.getFile().length() <= 200);             // bounded, not unbounded growth
    }
}
