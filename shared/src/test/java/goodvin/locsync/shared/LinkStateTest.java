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

public class LinkStateTest {
    @Test
    public void notRunning_isStopped_regardlessOfConnected() {
        assertEquals(LinkState.STOPPED, LinkState.of(false, false));
        assertEquals(LinkState.STOPPED, LinkState.of(false, true));
    }

    @Test
    public void runningButNoPeer_isWaiting() {
        assertEquals(LinkState.WAITING, LinkState.of(true, false));
    }

    @Test
    public void runningWithPeer_isConnected() {
        assertEquals(LinkState.CONNECTED, LinkState.of(true, true));
    }
}
