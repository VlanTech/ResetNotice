package com.vlan.resetnotice;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Background-only progress. Text/layout never animate or stretch. */
final class CountdownProgress extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final int track, fill;
    private final float radius;
    private boolean active = true;

    CountdownProgress(int background, int accent, float radius) {
        this(background, accent, radius, 0.25f);
    }
    CountdownProgress(int background, int accent, float radius, float strength) {
        track = blend(background, accent, 0.075f);
        fill = blend(background, accent, strength);
        this.radius = radius;
    }
    private static int blend(int a, int b, float f) {
        return Color.rgb(Math.round(Color.red(a) * (1-f) + Color.red(b) * f),
                Math.round(Color.green(a) * (1-f) + Color.green(b) * f),
                Math.round(Color.blue(a) * (1-f) + Color.blue(b) * f));
    }
    @Override public void draw(Canvas canvas) {
        if (!active) return;
        rect.set(getBounds());
        paint.setColor(track);
        canvas.drawRoundRect(rect, radius, radius, paint);
        if (getLevel() <= 0) return;
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clip);
        paint.setColor(fill);
        canvas.drawRect(rect.left, rect.top, rect.left + rect.width() * getLevel() / 10000f, rect.bottom, paint);
        canvas.restoreToCount(save);
    }
    void setActive(boolean enabled) {
        if (active != enabled) { active = enabled; invalidateSelf(); }
    }
    @Override protected boolean onLevelChange(int level) { invalidateSelf(); return true; }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
