package com.vlan.resetnotice;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.LruCache;
import android.widget.ImageView;
import android.widget.RemoteViews;

/** Generates a small, reusable, independently blurred frosted material.
 * All source colors are procedural and local, never wallpaper pixels. */
final class WidgetGlass {
    private static final int W = 176, H = 112;
    private static final LruCache<Integer, Bitmap> CACHE = new LruCache<>(8);
    private static int plain(Context c) {
        String theme = AppPreferences.theme(c);
        if (AppPreferences.LIGHT.equals(theme)) return R.drawable.widget_bg_light;
        if (AppPreferences.DARK.equals(theme)) return R.drawable.widget_bg_dark;
        return R.drawable.widget_bg_auto;
    }
    static synchronized Bitmap bitmap(Context localized, int strength) {
        int bucket = GlassMath.bucket(strength);
        if (bucket == 0) return null;
        boolean dark = (localized.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int key = (dark ? 200 : 0) + bucket;
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;
        int top = dark ? 0xff302b40 : 0xfffdfaff, bottom = dark ? 0xff1c1b27 : 0xffe9e1f9;
        int lavender = dark ? 0xff66528f : 0xffc5b4f2;
        int pink = dark ? 0xff644056 : 0xffefcee8;
        int blue = dark ? 0xff355267 : 0xffcfdeff;
        int[] texture = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int color = GlassMath.mix(top, bottom, y/(float)(H-1));
            float a = shape(x,y,W*.18f,H*.18f,W*.34f);
            float b = shape(x,y,W*.84f,H*.30f,W*.30f);
            float d = shape(x,y,W*.55f,H*.95f,W*.31f);
            color = GlassMath.mix(color, lavender, a*.39f);
            color = GlassMath.mix(color, pink, b*.30f);
            color = GlassMath.mix(color, blue, d*.25f);
            // fine local frosted grain vanishes progressively under the filter
            int noise = ((x*37 + y*73 + x*y*19) ^ (x*11+y*17)) & 15;
            color = GlassMath.mix(color, noise < 8 ? 0xff000000 : 0xffffffff, Math.abs(noise-7.5f)*.0017f);
            texture[y*W+x] = color;
        }
        int[] blurred = GlassMath.blur(texture,W,H,GlassMath.blurRadius(bucket));
        Bitmap material = Bitmap.createBitmap(blurred,W,H,Bitmap.Config.ARGB_8888);
        Bitmap result = Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(new BitmapShader(material, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        RectF area = new RectF(0.5f,.5f,W-.5f,H-.5f);
        canvas.drawRoundRect(area, 13f, 13f, paint);
        paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1f);
        paint.setColor(dark ? 0xff6b607d : 0xffe2d9f2);
        canvas.drawRoundRect(area,13f,13f,paint);
        CACHE.put(key,result);
        return result;
    }
    private static float shape(float x,float y,float cx,float cy,float r) {
        float dx=x-cx,dy=y-cy;
        // A defined soft source boundary makes independent blur strength
        // visually discernible rather than filtering an already flat tint.
        return Math.max(0f,Math.min(1f,(1f-(float)Math.sqrt(dx*dx+dy*dy)/r)*3.5f));
    }
    static void applyRemote(RemoteViews v, Context context, Context localized) {
        Bitmap image = bitmap(localized, AppPreferences.widgetBlur(context));
        if (image == null) v.setImageViewResource(R.id.widget_glass, plain(context));
        else v.setImageViewBitmap(R.id.widget_glass, image);
        v.setInt(R.id.widget_glass, "setImageAlpha", WidgetMath.glassAlpha(AppPreferences.widgetTransparency(context)));
    }
    static void preview(ImageView image, Context c) {
        Bitmap bitmap = bitmap(AppPreferences.localized(c),AppPreferences.widgetBlur(c));
        if (bitmap == null) image.setImageResource(plain(c));
        else image.setImageBitmap(bitmap);
        image.setImageAlpha(WidgetMath.glassAlpha(AppPreferences.widgetTransparency(c)));
    }
}
