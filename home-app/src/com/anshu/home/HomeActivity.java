package com.anshu.home;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Centered date / big time / weather, a frosted dock, and a rotating photo wallpaper.
 * Sizes and spacing are measured from the mockup (1920px-wide frame, 1dp = 2px on this TV).
 */
public class HomeActivity extends Activity {

    // ---- Settings ----
    private static final String CITY = "Bangalore";
    private static final double LAT = 12.9716, LON = 77.5946;
    private static final long WALLPAPER_EVERY_MS = 5 * 60 * 1000;   // new photo every 5 minutes
    private static final long WEATHER_EVERY_MS = 20 * 60 * 1000;
    private static final String[][] APPS = {
            {"YouTube", "com.google.android.youtube.tv"},
            {"Netflix", "com.netflix.ninja"},
            {"Prime Video", "com.amazon.amazonvideo.livingroom"},
            {"JioHotstar", "in.startv.hotstar"},
            {"Apple TV", "com.apple.atve.androidtv.appletv"},
            {"Spotify", "com.spotify.tv.android"},
    };
    private static final int FOCUS_INDEX = 1; // Netflix

    // ---- Measured from the mockup (dp) ----
    private static final float ICON = 37f, ICON_FOCUS = 50.5f, ICON_GAP = 5.775f;
    private static final float SHELF_PAD_X = 8.5f, SHELF_PAD_TOP = 8f, SHELF_PAD_BOTTOM = 7.5f, SHELF_RADIUS = 12.87f, SHELF_BOTTOM = 10.5f;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final HashMap<String, Typeface> fonts = new HashMap<>();
    private final List<DockItem> dock = new ArrayList<>();

    private Backdrop backdrop;
    private Shelf shelf;
    private TextView date, time, weather;
    private WeatherGlyph weatherGlyph;
    private LinearLayout weatherRow;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); }
    };

    int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setBackgroundDrawable(new ColorDrawable(0xFF070E2B));
        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);

        // Wallpaper (crossfading carousel) + a soft scrim so text stays readable on any photo.
        backdrop = new Backdrop(this);
        root.addView(backdrop, match());

        // Centered clock block.
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        blp.bottomMargin = dp(35); // block centre sits at ~43.5% of screen height, as in the mockup
        root.addView(block, blp);

        date = label(15.5f, 0xE6FFFFFF, "fonts/ui_400.ttf", -0.01f);
        date.setGravity(Gravity.CENTER_HORIZONTAL);
        block.addView(date, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        time = label(100f, 0xFFFFFFFF, "fonts/ui_600.ttf", -0.0616f);
        time.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(7);
        block.addView(time, tl);
        time.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int bo, int ol, int ot, int or, int ob) {
                // Soft white-to-lavender fade, like the mockup.
                time.getPaint().setShader(new LinearGradient(0, 0, 0, v.getHeight(),
                        new int[]{0xE6FFFFFF, 0xE6FFFFFF, 0xB8FFFFFF}, new float[]{0f, 0.28f, 0.78f}, Shader.TileMode.CLAMP));
                time.invalidate();
            }
        });

        weatherRow = new LinearLayout(this);
        weatherRow.setOrientation(LinearLayout.HORIZONTAL);
        weatherRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wl.topMargin = dp(6);
        block.addView(weatherRow, wl);
        View balance = new View(this); // mirrors the weather icon so the text stays centred
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(dp(20), dp(20));
        al.rightMargin = dp(8);
        weatherRow.addView(balance, al);
        weather = label(19f, 0xE0FFFFFF, "fonts/ui_400.ttf", -0.01f);
        weather.setText(CITY);
        weatherRow.addView(weather);
        weatherGlyph = new WeatherGlyph(this);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(dp(20), dp(20));
        gl.leftMargin = dp(8);
        weatherGlyph.setVisibility(View.INVISIBLE);
        weatherRow.addView(weatherGlyph, gl);

        // Dock.
        shelf = new Shelf(this);
        shelf.setOrientation(LinearLayout.HORIZONTAL);
        shelf.setGravity(Gravity.BOTTOM);
        shelf.setClipChildren(false);
        shelf.setClipToPadding(false);
        shelf.setPadding(dp(SHELF_PAD_X), dp(SHELF_PAD_TOP), dp(SHELF_PAD_X), dp(SHELF_PAD_BOTTOM));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        slp.bottomMargin = dp(SHELF_BOTTOM);
        root.addView(shelf, slp);

        PackageManager pm = getPackageManager();
        for (String[] a : APPS) {
            Intent launch = launchIntent(pm, a[1]);
            if (launch == null) continue;
            Drawable icon;
            try { icon = pm.getApplicationIcon(a[1]); } catch (Exception e) { continue; }
            DockItem item = new DockItem(a[0], icon, launch);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(ICON), dp(ICON));
            if (!dock.isEmpty()) lp.leftMargin = dp(ICON_GAP);
            shelf.addView(item, lp);
            dock.add(item);
        }

        // Resting state: an invisible holder keeps focus so no app looks selected.
        // Press Down to step into the dock; Up or Back steps out again.
        idle = new View(this);
        idle.setId(View.generateViewId());
        idle.setFocusable(true);
        idle.setFocusableInTouchMode(true);
        idle.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
                if (code == KeyEvent.KEYCODE_DPAD_DOWN && !dock.isEmpty()) {
                    dock.get(Math.min(lastIndex, dock.size() - 1)).requestFocus();
                    return true;
                }
                return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_LEFT
                        || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_CENTER
                        || code == KeyEvent.KEYCODE_ENTER;
            }
        });
        root.addView(idle, new FrameLayout.LayoutParams(1, 1, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        for (int i = 0; i < dock.size(); i++) {
            DockItem d = dock.get(i);
            d.setId(View.generateViewId());
            d.setNextFocusUpId(idle.getId());
        }
        if (!dock.isEmpty()) {
            dock.get(0).setNextFocusLeftId(dock.get(0).getId());
            dock.get(dock.size() - 1).setNextFocusRightId(dock.get(dock.size() - 1).getId());
        }

        setContentView(root);
        focusDefault();

        backdrop.showBundled();
        ui.post(wallpaperLoop); // new photo on every start
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        ui.post(weatherLoop);
    }

    // =============== Dock ===============

    private class DockItem extends View {
        private final Drawable icon;
        private final Path clip = new Path();
        private final RectF r = new RectF();
        private final Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float size = ICON;

        DockItem(String name, Drawable icon, final Intent launch) {
            super(HomeActivity.this);
            this.icon = icon;
            plate.setColor(0xFF1C1C1E);
            shade.setColor(0xFF1C1C1E);
            shade.setShadowLayer(dp(4), 0, dp(1), 0x38000000); // very subtle lift
            setContentDescription(name);
            setFocusable(true);
            setClickable(true);
            setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean has) {
                    grow(has ? ICON_FOCUS : ICON);
                    if (has) lastIndex = dock.indexOf(DockItem.this);
                }
            });
            setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { open(launch); }
            });
        }

        /** Only this icon changes size; the dock re-flows around it. */
        void grow(final float target) {
            final float from = size;
            size = target;
            tween(this, 240, new Frame() {
                @Override public void apply(float k) {
                    int px = dp(from + (target - from) * k);
                    ViewGroup.LayoutParams lp = getLayoutParams();
                    lp.width = px; lp.height = px;
                    setLayoutParams(lp);
                }
            });
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float rad = w * 0.30f;
            r.set(0, 0, w, h);
            r.inset(0.5f, 0.5f);
            c.drawRoundRect(r, rad, rad, shade); // shadow spills just outside the tile
            r.set(0, 0, w, h);
            clip.reset();
            clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            c.drawRect(r, plate);
            if (Build.VERSION.SDK_INT >= 26 && icon instanceof AdaptiveIconDrawable) {
                AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
                int pad = Math.round(w * 0.25f);
                Drawable bg = a.getBackground(), fg = a.getForeground();
                if (bg != null) { bg.setBounds(-pad, -pad, w + pad, h + pad); bg.draw(c); }
                if (fg != null) { fg.setBounds(-pad, -pad, w + pad, h + pad); fg.draw(c); }
            } else {
                icon.setBounds(0, 0, w, h);
                icon.draw(c);
            }
            c.restore();
        }
    }

    /** Frosted glass: blurred wallpaper behind it, faint white tint, hairline edge. No shadow. */
    private class Shelf extends LinearLayout {
        private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final RectF r = new RectF();
        private final Rect dst = new Rect();
        private final int[] loc = new int[2];

        Shelf(Context c) {
            super(c);
            setWillNotDraw(false);
            tint.setColor(0x1FFFFFFF);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(dp(0.75f));
            edge.setColor(0x40FFFFFF);
        }

        @Override protected void onDraw(Canvas c) {
            float rad = dp(SHELF_RADIUS);
            // Shelf hugs the resting icons; the focused one rises above it.
            float top = getHeight() - dp(SHELF_PAD_BOTTOM + ICON + SHELF_PAD_TOP);
            r.set(0, top, getWidth(), getHeight());
            clip.reset();
            clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            getLocationInWindow(loc);
            backdrop.drawBlurred(c, -loc[0], -loc[1], bmp);
            c.drawRect(r, tint);
            c.restore();
            r.inset(dp(0.4f), dp(0.4f));
            c.drawRoundRect(r, rad, rad, edge);
        }
    }

    private View idle;
    private int lastIndex = FOCUS_INDEX;

    // ---- Dock auto-hide: slides away after 5s without a button press; Down brings it back ----
    private static final long DOCK_IDLE_MS = 5000;
    private boolean dockShown = true;

    private final Runnable hideDock = new Runnable() {
        @Override public void run() {
            if (!dockShown) return;
            dockShown = false;
            if (dock.contains(getCurrentFocus())) idle.requestFocus(); // deselect before leaving
            slideDock(false);
        }
    };

    private void resetDockTimer() {
        ui.removeCallbacks(hideDock);
        ui.postDelayed(hideDock, DOCK_IDLE_MS);
    }

    private void slideDock(final boolean show) {
        final float fromY = shelf.getTranslationY(), toY = show ? 0f : dp(SHELF_BOTTOM + 90);
        final float fromA = shelf.getAlpha(), toA = show ? 1f : 0f;
        final long start = SystemClock.uptimeMillis(), ms = show ? 420 : 520;
        final Object token = new Object();
        shelf.setTag(token);
        shelf.postOnAnimation(new Runnable() {
            @Override public void run() {
                if (shelf.getTag() != token) return;
                float t = Math.min(1f, (SystemClock.uptimeMillis() - start) / (float) ms);
                // Slide in: decelerate. Slide out: gentle ease-in-out.
                float e = show ? 1f - (float) Math.pow(1f - t, 3)
                               : (t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f);
                shelf.setTranslationY(fromY + (toY - fromY) * e);
                shelf.setAlpha(fromA + (toA - fromA) * e);
                shelf.invalidate();
                if (t < 1f) shelf.postOnAnimation(this);
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            resetDockTimer();
            if (!dockShown) {
                if (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                    dockShown = true;
                    slideDock(true);
                    if (!dock.isEmpty()) dock.get(Math.min(lastIndex, dock.size() - 1)).requestFocus();
                    return true;
                }
                if (e.getKeyCode() != KeyEvent.KEYCODE_BACK) return true; // dock hidden: ignore other keys
            }
        }
        return super.dispatchKeyEvent(e);
    }

    /** Nothing selected: focus rests on the invisible holder. */
    private void focusDefault() {
        if (idle != null) idle.requestFocus();
    }

    // =============== Wallpaper carousel ===============

    /** Two layers that crossfade. Keeps a blurred copy for the glass dock. */
    private class Backdrop extends View {
        private Bitmap cur, next, curBlur, nextBlur;
        private float mix = 1f; // 0 = cur, 1 = next fully shown
        private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint scrim = new Paint();
        private final Rect full = new Rect();

        Backdrop(Context c) { super(c); }

        void showBundled() {
            Bitmap b = loadAsset("wallpaper.jpg");
            if (b != null) { cur = fit(b); curBlur = blur(cur); next = null; invalidateAll(); }
            // Show the last photo straight away (then the carousel moves on to a new one).
            final File f = new File(getFilesDir(), "wall_current.jpg");
            if (f.exists()) {
                Bitmap c = decode(f);
                if (c != null) { isPhoto = true; crossfadeTo(fit(c), 10); }
            }
        }

        void crossfadeTo(Bitmap bm, long ms) {
            if (cur != null && next != null) { cur = next; curBlur = nextBlur; }
            next = bm;
            nextBlur = blur(bm);
            mix = 0f;
            tween(this, ms, new Frame() {
                @Override public void apply(float k) { mix = k; invalidateAll(); }
            });
        }

        private void invalidateAll() { invalidate(); if (shelf != null) shelf.invalidate(); }

        void drawBlurred(Canvas c, int x, int y, Paint paint) {
            int w = getWidth(), h = getHeight();
            Rect d = new Rect(x, y, x + w, y + h);
            if (curBlur != null) { paint.setAlpha(255); c.drawBitmap(curBlur, null, d, paint); }
            if (nextBlur != null) { paint.setAlpha(Math.round(255 * mix)); c.drawBitmap(nextBlur, null, d, paint); }
            paint.setAlpha(255);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            scrim.setShader(new RadialGradient(w / 2f, h * 0.44f, w * 0.42f,
                    new int[]{0x47000000, 0x1A000000, 0x00000000}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override protected void onDraw(Canvas c) {
            full.set(0, 0, getWidth(), getHeight());
            if (cur != null) { p.setAlpha(255); c.drawBitmap(cur, null, full, p); }
            if (next != null) { p.setAlpha(Math.round(255 * mix)); c.drawBitmap(next, null, full, p); }
            if (next != null && mix > 0f) c.drawRect(full, scrim);
            else if (cur != null && isPhoto) c.drawRect(full, scrim);
        }
    }

    private boolean isPhoto = false;

    /**
     * Photo carousel. A new photo every time the TV screen turns on, on every cold start,
     * and every few minutes while the home screen is up. The next photo is always
     * downloaded ahead of time, so switching is instant.
     */
    private android.content.SharedPreferences prefs() { return getSharedPreferences("wall", MODE_PRIVATE); }
    private File nextFile() { return new File(getFilesDir(), "wall_next.jpg"); }
    private File currentFile() { return new File(getFilesDir(), "wall_current.jpg"); }

    private final Runnable wallpaperLoop = new Runnable() {
        @Override public void run() {
            showNextPhoto();
            ui.postDelayed(this, WALLPAPER_EVERY_MS);
        }
    };

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            ui.removeCallbacks(wallpaperLoop);
            ui.post(wallpaperLoop); // fresh photo as soon as the TV turns on
        }
    };

    private void showNextPhoto() {
        final File nf = nextFile();
        final boolean hadNext = nf.exists();
        if (hadNext) {
            Bitmap bm = decode(nf);
            if (bm != null) {
                isPhoto = true;
                backdrop.crossfadeTo(fit(bm), 1400);
                //noinspection ResultOfMethodCallIgnored
                nf.renameTo(currentFile());
            } else {
                //noinspection ResultOfMethodCallIgnored
                nf.delete();
            }
        }
        // Download the one after (or this one, if nothing was ready).
        final boolean showWhenReady = !hadNext; // nothing was ready: show the download as soon as it lands
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    android.content.SharedPreferences sp = prefs();
                    List<String> photos = new ArrayList<>();
                    String cached = sp.getString("list", "");
                    long fetched = sp.getLong("fetched", 0);
                    if (!cached.isEmpty() && System.currentTimeMillis() - fetched < 6 * 3600 * 1000L) {
                        JSONArray a = new JSONArray(cached);
                        for (int i = 0; i < a.length(); i++) photos.add(a.getString(i));
                    } else {
                        // Bing's daily landscape photos: 16:9, 1920x1080, new ones every day.
                        JSONObject j = new JSONObject(get("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-IN"));
                        JSONArray arr = j.getJSONArray("images");
                        JSONArray keep = new JSONArray();
                        for (int i = 0; i < arr.length(); i++) {
                            String u = "https://www.bing.com" + arr.getJSONObject(i).getString("url");
                            photos.add(u); keep.put(u);
                        }
                        sp.edit().putString("list", keep.toString()).putLong("fetched", System.currentTimeMillis()).apply();
                    }
                    if (photos.isEmpty()) return;
                    int idx = sp.getInt("index", 0);
                    String url = photos.get(idx % photos.size());
                    sp.edit().putInt("index", (idx + 1) % 1000).apply(); // remembered across restarts
                    byte[] data = getBytes(url);
                    try (FileOutputStream out = new FileOutputStream(nextFile())) { out.write(data); }
                    if (showWhenReady) {
                        ui.post(new Runnable() { @Override public void run() { showNextPhoto(); } });
                    }
                } catch (Exception ignored) { /* offline: keep current wallpaper */ }
            }
        });
    }

    // =============== Weather (Open-Meteo, no account needed) ===============

    private final Runnable weatherLoop = new Runnable() {
        @Override public void run() {
            io.execute(new Runnable() {
                @Override public void run() {
                    try {
                        JSONObject j = new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=" + LAT + "&longitude=" + LON
                                + "&current=temperature_2m,weather_code,is_day&timezone=auto"));
                        JSONObject cur = j.getJSONObject("current");
                        final int temp = (int) Math.round(cur.getDouble("temperature_2m"));
                        final int code = cur.getInt("weather_code");
                        final boolean day = cur.optInt("is_day", 1) == 1;
                        ui.post(new Runnable() {
                            @Override public void run() {
                                weather.setText(CITY + ", " + temp + "°C");
                                weatherGlyph.set(code, day);
                                weatherGlyph.setVisibility(View.VISIBLE);
                            }
                        });
                    } catch (Exception ignored) { }
                }
            });
            ui.postDelayed(this, WEATHER_EVERY_MS);
        }
    };

    /** Outline navigation arrow, as in the mockup. */
    private class LocationArrow extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        LocationArrow(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0xE0FFFFFF);
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), u = w / 24f;
            p.setStrokeWidth(1.6f * u);
            path.reset();
            path.moveTo(21 * u, 3 * u);
            path.lineTo(3 * u, 10.5f * u);
            path.lineTo(10.5f * u, 13.5f * u);
            path.lineTo(13.5f * u, 21 * u);
            path.close();
            c.drawPath(path, p);
        }
    }

    /** Current-weather icon from Lucide, drawn with Lucide's own stroke style (2/24, round caps and joins). */
    private class WeatherGlyph extends View {
        private float[] icon = LucideIcons.CLOUD;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        WeatherGlyph(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(0xE0FFFFFF);
            p.setShadowLayer(dp(5), 0, dp(1), 0x40000000);
        }
        void set(int code, boolean day) { icon = LucideIcons.forWeather(code, day); invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float size = Math.min(getWidth(), getHeight());
            p.setStrokeWidth(2f * size / 24f);
            LucideIcons.draw(c, icon, size, p);
        }
    }

    // =============== Motion ===============

    private interface Frame { void apply(float k); }

    /** Time-based ease-out; runs smoothly even with the TV's system animations switched off. */
    private void tween(final View host, final long ms, final Frame f) {
        final long start = SystemClock.uptimeMillis();
        final Object token = new Object();
        host.setTag(token);
        host.postOnAnimation(new Runnable() {
            @Override public void run() {
                if (host.getTag() != token) return;
                float t = ms <= 0 ? 1f : Math.min(1f, (SystemClock.uptimeMillis() - start) / (float) ms);
                float e = 1f - (float) Math.pow(1f - t, 3);
                f.apply(e);
                if (t < 1f) host.postOnAnimation(this);
            }
        });
    }

    // =============== Helpers ===============

    private TextView label(float sp, int color, String fontAsset, float tracking) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(font(fontAsset));
        t.setLetterSpacing(tracking);
        t.setIncludeFontPadding(false);
        t.setShadowLayer(dp(6), 0, dp(1), 0x40000000); // very subtle, keeps text readable on light photos
        return t;
    }

    private Typeface font(String asset) {
        Typeface tf = fonts.get(asset);
        if (tf != null) return tf;
        try { tf = Typeface.createFromAsset(getAssets(), asset); } catch (Exception e) { tf = Typeface.DEFAULT; }
        fonts.put(asset, tf);
        return tf;
    }

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        boolean h24 = DateFormat.is24HourFormat(this);
        time.setText(DateFormat.format(h24 ? "HH:mm" : "h:mm", now));
        date.setText(DateFormat.format("EEEE, d MMM", now));
    }

    private Bitmap fit(Bitmap src) {
        int w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
        float scale = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        int cw = Math.min(src.getWidth(), Math.round(w / scale)), ch = Math.min(src.getHeight(), Math.round(h / scale));
        Bitmap cut = Bitmap.createBitmap(src, (src.getWidth() - cw) / 2, (src.getHeight() - ch) / 2, cw, ch);
        return (cut.getWidth() == w && cut.getHeight() == h) ? cut : Bitmap.createScaledBitmap(cut, w, h, true);
    }

    private static Bitmap blur(Bitmap src) {
        Bitmap b = src;
        int w = src.getWidth(), h = src.getHeight();
        for (int i = 0; i < 5; i++) { w = Math.max(8, w / 2); h = Math.max(8, h / 2); b = Bitmap.createScaledBitmap(b, w, h, true); }
        for (int i = 0; i < 3; i++) { w *= 2; h *= 2; b = Bitmap.createScaledBitmap(b, w, h, true); }
        return b;
    }

    private Bitmap loadAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) { return null; }
    }

    private static Bitmap decode(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    private static byte[] getBytes(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally { c.disconnect(); }
    }

    private static String get(String url) throws Exception { return new String(getBytes(url), "UTF-8"); }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    private void open(Intent i) {
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); } catch (Exception ignored) { }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateClock();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, f);
        focusDefault(); // coming back home: dock unselected
        // Dock visible on arrival, then slides away after 5s idle.
        dockShown = true;
        shelf.setTag(null);
        shelf.setTranslationY(0f);
        shelf.setAlpha(1f);
        resetDockTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(hideDock);
        try { unregisterReceiver(tick); } catch (Exception ignored) { }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        try { unregisterReceiver(screenOn); } catch (Exception ignored) { }
        io.shutdownNow();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { focusDefault(); return true; } // Back = step out of the dock
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onBackPressed() { }
}
