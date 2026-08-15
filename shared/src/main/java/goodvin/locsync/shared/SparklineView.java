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
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;


/**
 * A minimal sparkline: a filled + stroked {@link Path} over a {@link SparkBuffer} ring of the last
 * {@code capacity} samples (46 by default, one per second). Fill is accent&nbsp;14%, stroke is
 * 1.5&nbsp;dp accent with rounded joins. The value range auto-scales to the current window.
 */
public class SparklineView extends View {
    private static final int DEFAULT_CAPACITY = 46;

    private final SparkBuffer buffer = new SparkBuffer(DEFAULT_CAPACITY);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path stroke = new Path();
    private final Path area = new Path();
    private float density;

    public SparklineView(Context context) {
        super(context);
        init(context);
    }

    public SparklineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        int accent = context.getResources().getColor(R.color.ls_accent, null);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(accent);
        fill.setAlpha(36); // ~14%
        line.setStyle(Paint.Style.STROKE);
        line.setColor(accent);
        line.setStrokeWidth(1.5f * density);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Append a sample and redraw. */
    public void push(float value) {
        buffer.add(value);
        invalidate();
    }

    /** Clear the window (does not draw anything until new samples arrive). */
    public void clear() {
        buffer.clear();
        invalidate();
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = buffer.size();
        if (n < 2) {
            return;
        }
        float[] v = buffer.values();
        float min = buffer.min();
        float max = buffer.max();
        float range = max - min;
        if (range <= 0f) {
            range = 1f; // flat series → draw a centered line
        }

        float pad = 2f * density;
        float w = getWidth();
        float h = getHeight();
        float usableH = h - 2 * pad;
        float stepX = (w) / (n - 1);

        stroke.reset();
        area.reset();
        for (int i = 0; i < n; i++) {
            float x = i * stepX;
            float norm = (v[i] - min) / range;
            float y = pad + (1f - norm) * usableH;
            if (i == 0) {
                stroke.moveTo(x, y);
                area.moveTo(x, h);
                area.lineTo(x, y);
            } else {
                stroke.lineTo(x, y);
                area.lineTo(x, y);
            }
        }
        area.lineTo((n - 1) * stepX, h);
        area.close();

        canvas.drawPath(area, fill);
        canvas.drawPath(stroke, line);
    }
}
