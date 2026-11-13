package cn.bigfish.phone;

import java.util.ArrayDeque;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class TouchMemory {
    private static final long TTL_MS = 300000;
    private static final int LIMIT = 32;
    private static final class Entry {
        final long id;
        final long time;
        final long unixTime;
        final TouchGesture.Event event;
        Entry(long id, long time, long unixTime, TouchGesture.Event event) {
            this.id = id;this.time = time;this.unixTime = unixTime;this.event = event;
        }
    }
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private final String sessionId = UUID.randomUUID().toString();
    private long sequence;

    private void expire(long now) {
        while (!entries.isEmpty() && now - entries.peekFirst().time >= TTL_MS) entries.removeFirst();
    }

    public synchronized void add(TouchGesture.Event event, long now, long unixTime) {
        if (event == null) return;
        expire(now);
        if (entries.size() == LIMIT) entries.removeFirst();
        entries.addLast(new Entry(++sequence, now, unixTime, event));
    }

    public synchronized JSONObject recent(long afterId, int count, long now) throws JSONException {
        if (afterId < 0 || count < 1 || count > LIMIT) throw new IllegalArgumentException("after_id 需为非负整数，limit 需为 1～32 的整数");
        expire(now);
        ArrayDeque<Entry> selected = new ArrayDeque<>();
        for (Entry entry : entries) {
            if (entry.id <= afterId) continue;
            selected.addLast(entry);
            if (selected.size() > count) selected.removeFirst();
        }
        JSONArray events = new JSONArray();
        for (Entry entry : selected) events.put(new JSONObject().put("id", entry.id).put("type", entry.event.type)
            .put("age_ms", Math.max(0, now - entry.time)).put("unix_time_ms", entry.unixTime)
            .put("duration_ms", entry.event.durationMs).put("distance_dp", Math.round(entry.event.distanceDp)));
        return new JSONObject().put("events", events).put("session_id", sessionId).put("latest_id", sequence).put("retention_ms", TTL_MS)
            .put("source", "phone_touch_pad").put("temperature_available", false).put("pressure_available", false)
            .put("note", "仅手机应用触摸区的操作；tap=轻点，long_press=长按，stroke=抚摸。不会自动唤醒小智；无事件时请勿编造触感或温度。事件仅保留五分钟，最多32条，读取不清空；根据id避免重复回应。session_id变化代表应用服务重启，需清除旧after_id重新读取。");
    }

    public synchronized String summary(long now) {
        expire(now);
        if (entries.isEmpty()) return "最近五分钟没有触摸；摸摸你的伙伴吧";
        Entry entry = entries.peekLast();
        return "最近触摸：" + label(entry.event.type) + " · " + (now - entry.time) / 1000 + " 秒前";
    }

    public static String label(String type) {
        return "stroke".equals(type) ? "抚摸" : "long_press".equals(type) ? "长按" : "轻点";
    }
}
