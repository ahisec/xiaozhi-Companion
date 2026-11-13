package cn.bigfish.phone;

public final class TouchGesture {
    public static final long LONG_PRESS_MS = 600;
    public static final class Event {
        public final String type;
        public final long durationMs;
        public final float distanceDp;
        Event(String type, long durationMs, float distanceDp) {
            this.type = type;this.durationMs = durationMs;this.distanceDp = distanceDp;
        }
    }
    private boolean active;
    private boolean emitted;
    private float startX;
    private float startY;
    private float previousX;
    private float previousY;
    private float maxDistance;
    private float path;
    private long started;

    public void begin(float positionX, float positionY, long now) {
        active = true;emitted = false;startX = previousX = positionX;startY = previousY = positionY;
        maxDistance = 0;path = 0;started = now;
    }

    public void move(float positionX, float positionY) {
        if (!active) return;
        path += (float) Math.hypot(positionX - previousX, positionY - previousY);
        maxDistance = Math.max(maxDistance, (float) Math.hypot(positionX - startX, positionY - startY));
        previousX = positionX;previousY = positionY;
    }

    private boolean stroked() { return maxDistance >= 24 || path >= 80 && maxDistance >= 12; }

    public Event longPress(long now) {
        if (!active || emitted || stroked() || now - started < LONG_PRESS_MS) return null;
        emitted = true;return new Event("long_press", now - started, path);
    }

    public Event end(long now) {
        if (!active) return null;
        active = false;
        if (emitted) return null;
        return new Event(stroked() ? "stroke" : now - started >= LONG_PRESS_MS ? "long_press" : "tap", Math.max(0, now - started), path);
    }

    public void cancel() { active = false; }
}
