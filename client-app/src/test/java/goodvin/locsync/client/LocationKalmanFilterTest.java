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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocationKalmanFilterTest {

    private static LocationKalmanFilter newFilter() {
        return new LocationKalmanFilter(2.0, 1.0);
    }

    @Test
    public void firstUpdateInitializes() {
        LocationKalmanFilter f = newFilter();
        assertTrue(!f.isInitialized());
        f.update(59.0, 30.0, 10.0, 90.0, 5.0, 1.0, 5.0); // heading east at 10 m/s
        assertTrue(f.isInitialized());
        assertEquals(59.0, f.getLatitude(), 1e-6);
        assertEquals(30.0, f.getLongitude(), 1e-6);
        // bearing 90 => east velocity positive, north ~0
        assertTrue(f.getVe() > 8.0);
        assertEquals(0.0, f.getVn(), 0.5);
    }

    @Test
    public void resetForgetsState() {
        LocationKalmanFilter f = newFilter();
        f.update(59.0, 30.0, 10.0, 90.0, 5.0, 1.0, 5.0);
        assertTrue(f.isInitialized());
        f.reset();
        assertTrue(!f.isInitialized());
        // after reset, the next update re-anchors at the new position
        f.update(60.0, 31.0, 0.0, 0.0, 5.0, 1.0, 30.0);
        assertEquals(60.0, f.getLatitude(), 1e-6);
        assertEquals(31.0, f.getLongitude(), 1e-6);
    }

    @Test
    public void predictAdvancesAlongVelocity() {
        LocationKalmanFilter f = newFilter();
        f.update(59.0, 30.0, 10.0, 90.0, 5.0, 1.0, 5.0); // east 10 m/s
        double lon0 = f.getLongitude();
        f.predict(1.0); // 1 second
        // ~10 m east; 1 deg lon ~ 111320*cos(59) ~ 57330 m => 10m ~ 1.74e-4 deg
        double dLon = f.getLongitude() - lon0;
        assertTrue("expected eastward advance", dLon > 1.0e-4 && dLon < 2.5e-4);
        assertEquals(59.0, f.getLatitude(), 1e-5);
    }

    @Test
    public void noiseIsReduced() {
        LocationKalmanFilter f = newFilter();
        // Stationary truth at (59,30); feed noisy position measurements.
        f.update(59.0, 30.0, 0.0, 0.0, 8.0, 1.0, 30.0);
        double[] noise = {+1e-4, -1e-4, +8e-5, -9e-5, +1e-4, -1e-4};
        double maxErrM = 0;
        for (double dn : noise) {
            f.predict(0.2);
            f.update(59.0 + dn, 30.0 + dn, 0.0, 0.0, 8.0, 1.0, 30.0);
            double errLat = Math.abs(f.getLatitude() - 59.0) * 111320.0;
            maxErrM = Math.max(maxErrM, errLat);
        }
        // input noise ~ 1e-4 deg ~ 11 m; filtered error should be well under half that.
        assertTrue("filtered error too high: " + maxErrM, maxErrM < 6.0);
    }

    @Test
    public void enuRoundTrips() {
        LocationKalmanFilter f = newFilter();
        f.update(59.0, 30.0, 0.0, 0.0, 5.0, 1.0, 30.0);
        // Update to a point ~100 m north-east, filter should track close to it.
        for (int i = 0; i < 30; i++) {
            f.predict(0.2);
            f.update(59.0009, 30.0016, 0.0, 0.0, 2.0, 1.0, 30.0);
        }
        assertEquals(59.0009, f.getLatitude(), 5e-5);
        assertEquals(30.0016, f.getLongitude(), 5e-5);
    }

    @Test
    public void bearingDerivedFromVelocity() {
        LocationKalmanFilter f = newFilter();
        f.update(59.0, 30.0, 10.0, 0.0, 3.0, 0.5, 2.0); // heading north
        for (int i = 0; i < 10; i++) { f.predict(0.1); f.update(59.0 + i * 9e-5, 30.0, 10.0, 0.0, 3.0, 0.5, 2.0); }
        assertEquals(0.0, f.getBearingDeg(), 5.0); // ~north
        assertTrue(f.getSpeed() > 8.0 && f.getSpeed() < 12.0);
    }
}
