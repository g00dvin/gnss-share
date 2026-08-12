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

/**
 * Constant-velocity Kalman filter for smoothing a moving GPS position. Operates in a local ENU
 * (east/north metres) frame anchored at the first fix. State: [e, n, ve, vn]. Full-state
 * measurement (position + GPS-derived velocity) applied as sequential scalar updates (the
 * measurement noise is diagonal, so no matrix inversion is needed). Pure Java — no Android APIs —
 * so it is unit-testable.
 */
public class LocationKalmanFilter {

    private static final double M_PER_DEG_LAT = 111320.0;
    private static final double REANCHOR_M = 10_000.0;
    private static final double MIN_POS_SIGMA = 1.0;

    private final double sigmaA;            // process acceleration noise (m/s^2)
    private final double defaultSpeedSigma; // fallback velocity measurement noise (m/s)

    private boolean initialized = false;
    private double lat0, lon0, mPerDegLon;

    // state
    private double e, n, ve, vn;
    // covariance (4x4)
    private final double[][] P = new double[4][4];

    public LocationKalmanFilter(double sigmaA, double defaultSpeedSigma) {
        this.sigmaA = sigmaA;
        this.defaultSpeedSigma = defaultSpeedSigma;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /** Forget all state so the next update() re-anchors fresh (e.g. after a reconnect). */
    public void reset() {
        initialized = false;
    }

    public void update(double lat, double lon, double speed, double bearingDeg,
                       double accuracy, double speedAccuracy, double bearingAccuracyDeg) {
        if (!initialized) {
            setAnchor(lat, lon);
            e = 0;
            n = 0;
            double br = Math.toRadians(bearingDeg);
            ve = speed * Math.sin(br);
            vn = speed * Math.cos(br);
            double sp = posSigma(accuracy);
            double sv = velSigma(speed, speedAccuracy, bearingAccuracyDeg);
            zero(P);
            P[0][0] = sp * sp;
            P[1][1] = sp * sp;
            P[2][2] = sv * sv;
            P[3][3] = sv * sv;
            initialized = true;
            return;
        }

        maybeReanchor(lat, lon);
        double em = (lon - lon0) * mPerDegLon;
        double nm = (lat - lat0) * M_PER_DEG_LAT;
        double br = Math.toRadians(bearingDeg);
        double vem = speed * Math.sin(br);
        double vnm = speed * Math.cos(br);
        double sp = posSigma(accuracy);
        double sv = velSigma(speed, speedAccuracy, bearingAccuracyDeg);

        double[] z = {em, nm, vem, vnm};
        double[] r = {sp * sp, sp * sp, sv * sv, sv * sv};
        for (int i = 0; i < 4; i++) {
            scalarUpdate(i, z[i], r[i]);
        }
    }

    public void predict(double dt) {
        if (!initialized || dt <= 0) {
            return;
        }
        // x = F x
        e += ve * dt;
        n += vn * dt;
        // P = F P F^T + Q, F = [[1,0,dt,0],[0,1,0,dt],[0,0,1,0],[0,0,0,1]]
        double[][] F = {
                {1, 0, dt, 0},
                {0, 1, 0, dt},
                {0, 0, 1, 0},
                {0, 0, 0, 1},
        };
        double[][] FP = mul(F, P);
        double[][] newP = mul(FP, transpose(F));
        // process noise Q
        double s2 = sigmaA * sigmaA;
        double dt2 = dt * dt, dt3 = dt2 * dt, dt4 = dt3 * dt;
        double q_pp = dt4 / 4.0, q_pv = dt3 / 2.0, q_vv = dt2;
        newP[0][0] += s2 * q_pp; newP[0][2] += s2 * q_pv;
        newP[2][0] += s2 * q_pv; newP[2][2] += s2 * q_vv;
        newP[1][1] += s2 * q_pp; newP[1][3] += s2 * q_pv;
        newP[3][1] += s2 * q_pv; newP[3][3] += s2 * q_vv;
        for (int i = 0; i < 4; i++) {
            System.arraycopy(newP[i], 0, P[i], 0, 4);
        }
    }

    // Sequential scalar Kalman update for measurement of state component `idx` (H row = unit vector).
    private void scalarUpdate(int idx, double z, double r) {
        double[] x = {e, n, ve, vn};
        double yInnov = z - x[idx];
        double s = P[idx][idx] + r;
        double[] k = new double[4];
        for (int i = 0; i < 4; i++) {
            k[i] = P[i][idx] / s;
        }
        e += k[0] * yInnov;
        n += k[1] * yInnov;
        ve += k[2] * yInnov;
        vn += k[3] * yInnov;
        // P = (I - K H) P ; H = e_idx^T  =>  P -= K * P[idx, :]
        double[] row = new double[4];
        System.arraycopy(P[idx], 0, row, 0, 4);
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                P[i][j] -= k[i] * row[j];
            }
        }
    }

    private double posSigma(double accuracy) {
        return accuracy > 0 ? accuracy : 10.0;
    }

    private double velSigma(double speed, double speedAccuracy, double bearingAccuracyDeg) {
        double base = speedAccuracy > 0 ? speedAccuracy : defaultSpeedSigma;
        double crossTrack = 0;
        if (bearingAccuracyDeg > 0) {
            crossTrack = Math.abs(speed) * Math.sin(Math.toRadians(bearingAccuracyDeg));
        }
        return Math.hypot(base, crossTrack);
    }

    private void setAnchor(double lat, double lon) {
        lat0 = lat;
        lon0 = lon;
        mPerDegLon = M_PER_DEG_LAT * Math.cos(Math.toRadians(lat));
        if (Math.abs(mPerDegLon) < 1.0) {
            mPerDegLon = 1.0; // guard near the poles
        }
    }

    private void maybeReanchor(double lat, double lon) {
        if (Math.abs(e) > REANCHOR_M || Math.abs(n) > REANCHOR_M) {
            // shift origin to current estimate's lat/lon, keep velocities
            double curLat = lat0 + n / M_PER_DEG_LAT;
            double curLon = lon0 + e / mPerDegLon;
            setAnchor(curLat, curLon);
            e = 0;
            n = 0;
        }
    }

    public double getLatitude() {
        return lat0 + n / M_PER_DEG_LAT;
    }

    public double getLongitude() {
        return lon0 + e / mPerDegLon;
    }

    public double getSpeed() {
        return Math.hypot(ve, vn);
    }

    public double getBearingDeg() {
        double b = Math.toDegrees(Math.atan2(ve, vn));
        return (b % 360 + 360) % 360;
    }

    public double getAccuracy() {
        return Math.max(MIN_POS_SIGMA, Math.sqrt((P[0][0] + P[1][1]) / 2.0));
    }

    public double getVe() {
        return ve;
    }

    public double getVn() {
        return vn;
    }

    // --- tiny 4x4 helpers ---
    private static double[][] mul(double[][] a, double[][] b) {
        double[][] c = new double[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double s = 0;
                for (int k = 0; k < 4; k++) {
                    s += a[i][k] * b[k][j];
                }
                c[i][j] = s;
            }
        }
        return c;
    }

    private static double[][] transpose(double[][] a) {
        double[][] t = new double[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                t[j][i] = a[i][j];
            }
        }
        return t;
    }

    private static void zero(double[][] a) {
        for (double[] row : a) {
            java.util.Arrays.fill(row, 0.0);
        }
    }
}
