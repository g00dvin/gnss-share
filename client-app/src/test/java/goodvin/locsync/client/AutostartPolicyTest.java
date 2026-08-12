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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import goodvin.locsync.client.AutostartPolicy.Decision;

public class AutostartPolicyTest {

    @Test
    public void noWifi_skipsRegardlessOfOtherFlags() {
        assertEquals(Decision.SKIP_NO_WIFI, AutostartPolicy.decide(false, true, false));
        assertEquals(Decision.SKIP_NO_WIFI, AutostartPolicy.decide(false, false, false));
        assertEquals(Decision.SKIP_NO_WIFI, AutostartPolicy.decide(false, true, true));
    }

    @Test
    public void wifiButNotEnabled_skips() {
        assertEquals(Decision.SKIP_NOT_ENABLED, AutostartPolicy.decide(true, false, false));
        assertEquals(Decision.SKIP_NOT_ENABLED, AutostartPolicy.decide(true, false, true));
    }

    @Test
    public void enabledButAlreadyRunning_skips() {
        assertEquals(Decision.SKIP_ALREADY_RUNNING, AutostartPolicy.decide(true, true, true));
    }

    @Test
    public void wifiEnabledNotRunning_starts() {
        assertEquals(Decision.START, AutostartPolicy.decide(true, true, false));
    }
}
