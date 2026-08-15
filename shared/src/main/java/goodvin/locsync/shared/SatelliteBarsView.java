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

import goodvin.locsync.logexporter.R;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;


/**
 * A fixed row of 14 signal bars. A bar at index {@code i} is lit when {@code i < satelliteCount};
 * unlit bars are a short stub in {@code ls_bar_unlit}. When per-satellite C/N0 is supplied, each lit
 * bar's height and brightness map that satellite's signal (0–50&nbsp;dB-Hz → 5–36&nbsp;dp; a bright
 * {@code ls_accent} bar = strong satellite, {@code ls_accent_700} = weak). Without C/N0 (the client,
 * which only knows the count) lit bars are a uniform {@code ls_accent} count meter.
 */
public class SatelliteBarsView extends View {
    private static final int BAR_COUNT = 14;
    private static final float GAP_DP = 3f;
    private static final float MIN_H_DP = 5f;
    private static final float MAX_H_DP = 36f;
    private static final float UNLIT_H_DP = 5f;
    private static final float CN0_MAX = 50f;
    private static final float CN0_STRONG = 30f; // >= this reads as a strong (bright) satellite

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float density;
    private int cAccent, cAccent700, cUnlit;

    private int satelliteCount = 0;
    private float[] cn0; // per-bar C/N0 in dB-Hz; null → lit bars use a default height

    public SatelliteBarsView(Context context) {
        super(context);
        init(context);
    }

    public SatelliteBarsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        cAccent = context.getResources().getColor(R.color.ls_accent, null);
        cAccent700 = context.getResources().getColor(R.color.ls_accent_700, null);
        cUnlit = context.getResources().getColor(R.color.ls_bar_unlit, null);
    }

    /**
     * @param satelliteCount number of lit bars (clamped to 0..14)
     * @param cn0PerBar       optional per-bar C/N0 (dB-Hz); may be null or shorter than the count
     */
    public void setData(int satelliteCount, float[] cn0PerBar) {
        this.satelliteCount = Math.max(0, Math.min(BAR_COUNT, satelliteCount));
        this.cn0 = cn0PerBar;
        invalidate();
    }

    public void clear() {
        setData(0, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float gap = GAP_DP * density;
        float barW = (w - gap * (BAR_COUNT - 1)) / BAR_COUNT;
        float radius = 2f * density;

        for (int i = 0; i < BAR_COUNT; i++) {
            boolean lit = i < satelliteCount;
            float barH;
            if (!lit) {
                barH = UNLIT_H_DP * density;
                paint.setColor(cUnlit);
            } else if (cn0 != null && i < cn0.length) {
                // Real per-satellite signal: height + brightness reflect C/N0.
                float dbHz = cn0[i];
                paint.setColor(dbHz >= CN0_STRONG ? cAccent : cAccent700);
                float norm = Math.max(0f, Math.min(1f, dbHz / CN0_MAX));
                barH = (MIN_H_DP + norm * (MAX_H_DP - MIN_H_DP)) * density;
            } else {
                // Count only (client): uniform lit bars at a mid height.
                paint.setColor(cAccent);
                barH = (MIN_H_DP + 0.6f * (MAX_H_DP - MIN_H_DP)) * density;
            }
            float left = i * (barW + gap);
            float top = h - barH;
            RectF r = new RectF(left, top, left + barW, h);
            // 2dp top corners only: draw rounded rect then square off the bottom with an overlay.
            canvas.drawRoundRect(r, radius, radius, paint);
            canvas.drawRect(left, top + radius, left + barW, h, paint);
        }
    }
}
