package cn.bigfish.phone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

public final class PetTouchView extends View {
    public interface Listener { void touched(TouchGesture.Event event); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect source = new Rect();
    private final RectF destination = new RectF();
    private Bitmap sprites;
    private final TouchGesture gesture = new TouchGesture();
    private final Listener listener;
    private String reaction = "摸摸我吧";
    private boolean happy;
    private boolean active;
    private int animationRow;
    private int animationFrames = 6;
    private int frameMs = 260;
    private long animationStarted = SystemClock.uptimeMillis();
    private final Runnable reset = () -> {
        happy = false;reaction = "摸摸我吧";animationRow = 0;animationFrames = 6;frameMs = 260;animationStarted = SystemClock.uptimeMillis();invalidate();
    };
    private final Runnable hold = () -> emit(gesture.longPress(SystemClock.uptimeMillis()));
    private final Runnable animate = new Runnable() {
        @Override public void run() { invalidate();scheduleAnimation(); }
    };

    public PetTouchView(Context context) { this(context, event -> {}); }

    public PetTouchView(Context context, Listener listener) {
        super(context);this.listener = listener;setClickable(true);setFocusable(true);
        setContentDescription("Deepseek Chan 触摸区，轻点、按住或滑动抚摸");
        paint.setFilterBitmap(true);
        try (java.io.InputStream input = context.getAssets().open("deepseek-chan.webp")) {
            sprites = BitmapFactory.decodeStream(input);
        } catch (java.io.IOException ignored) {}
    }

    private float density() { return getResources().getDisplayMetrics().density; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();float height = getHeight();float centerX = width * 0.5f;
        paint.setColor(happy ? Color.rgb(22, 48, 66) : Color.rgb(18, 33, 48));
        canvas.drawRoundRect(0, 0, width, height, 20 * density(), 20 * density(), paint);
        float imageHeight = Math.max(0, Math.min(height - 64 * density(), width * 0.9f * 208 / 192));
        float imageWidth = imageHeight * 192 / 208;float imageTop = Math.max(0, (height - 50 * density() - imageHeight) * 0.5f);
        if (sprites != null && imageHeight > 0) {
            int frame = (int) ((SystemClock.uptimeMillis() - animationStarted) / frameMs % animationFrames);
            source.set(frame * 192, animationRow * 208, (frame + 1) * 192, (animationRow + 1) * 208);
            destination.set(centerX - imageWidth / 2, imageTop, centerX + imageWidth / 2, imageTop + imageHeight);
            canvas.drawBitmap(sprites, source, destination, paint);
        }
        paint.setColor(Color.rgb(208, 240, 248));paint.setTextSize(17 * density());paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(sprites == null ? "角色资源加载失败" : reaction, centerX, height - 22 * density(), paint);
    }

    private void emit(TouchGesture.Event event) {
        if (event == null) return;
        happy = true;reaction = "stroke".equals(event.type) ? "好舒服，继续摸摸～" : "long_press".equals(event.type) ? "收到你的抱抱啦" : "我在呢～";
        animationRow = "stroke".equals(event.type) ? 4 : "long_press".equals(event.type) ? 6 : 3;
        animationFrames = "stroke".equals(event.type) ? 5 : "long_press".equals(event.type) ? 6 : 4;
        frameMs = "long_press".equals(event.type) ? 260 : 180;animationStarted = SystemClock.uptimeMillis();
        removeCallbacks(reset);postDelayed(reset, 2200);invalidate();
        Vibrator vibrator = getContext().getSystemService(Vibrator.class);
        if (vibrator != null && vibrator.hasVibrator()) {
            try { vibrator.vibrate(VibrationEffect.createOneShot("tap".equals(event.type) ? 25 : 55, VibrationEffect.DEFAULT_AMPLITUDE)); }
            catch (SecurityException ignored) {}
        }
        listener.touched(event);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!active) return false;
        float scale = density();
        if (event.getPointerCount() > 1) { cancelGesture();return true; }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                gesture.begin(event.getX() / scale, event.getY() / scale, event.getEventTime());
                postDelayed(hold, TouchGesture.LONG_PRESS_MS);return true;
            case MotionEvent.ACTION_MOVE:
                for (int index = 0; index < event.getHistorySize(); index++) gesture.move(event.getHistoricalX(index) / scale, event.getHistoricalY(index) / scale);
                gesture.move(event.getX() / scale, event.getY() / scale);return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(hold);gesture.move(event.getX() / scale, event.getY() / scale);emit(gesture.end(event.getEventTime()));
                getParent().requestDisallowInterceptTouchEvent(false);super.performClick();return true;
            case MotionEvent.ACTION_CANCEL:
                cancelGesture();return true;
            default: return true;
        }
    }

    @Override public boolean performClick() {
        super.performClick();if (active) { gesture.begin(0, 0, SystemClock.uptimeMillis());emit(gesture.end(SystemClock.uptimeMillis())); }return true;
    }

    private void cancelGesture() { removeCallbacks(hold);gesture.cancel();if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false); }
    private void scheduleAnimation() {
        removeCallbacks(animate);
        if (active && getWindowVisibility() == VISIBLE && isShown()) postDelayed(animate, frameMs);
    }
    public void setActive(boolean value) {
        active = value;
        if (!value) { cancelGesture();removeCallbacks(reset);reset.run(); }
        scheduleAnimation();
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow();scheduleAnimation(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) { cancelGesture();removeCallbacks(reset);reset.run(); }
        scheduleAnimation();
    }
    @Override protected void onDetachedFromWindow() { setActive(false);super.onDetachedFromWindow(); }
}
