package cn.bigfish.phone;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class TouchTest {
    @Test public void tapAllowsSmallHandJitter() {
        TouchGesture gesture = new TouchGesture();gesture.begin(50, 60, 1000);gesture.move(54, 62);
        TouchGesture.Event event = gesture.end(1100);
        assertEquals("tap", event.type);assertEquals(100, event.durationMs);
        assertNull(gesture.end(1150));
    }

    @Test public void longPressEmitsOnlyOnceAndDoesNotAlsoTap() {
        TouchGesture gesture = new TouchGesture();gesture.begin(0, 0, 1000);
        assertNull(gesture.longPress(1599));assertEquals("long_press", gesture.longPress(1600).type);
        assertNull(gesture.longPress(1700));assertNull(gesture.end(2000));
        gesture.begin(0, 0, 3000);assertEquals("long_press", gesture.end(3700).type);
    }

    @Test public void strokeRecognizesMovementEvenAfterReturningToStart() {
        TouchGesture gesture = new TouchGesture();gesture.begin(50, 60, 1000);
        gesture.move(110, 60);gesture.move(50, 60);
        assertNull(gesture.longPress(1700));
        TouchGesture.Event event = gesture.end(1800);
        assertEquals("stroke", event.type);assertEquals(120, event.distanceDp, 0.01);
    }

    @Test public void cancelledGestureProducesNoTouch() {
        TouchGesture gesture = new TouchGesture();gesture.begin(0, 0, 1000);gesture.move(50, 0);gesture.cancel();
        assertNull(gesture.end(1100));assertNull(gesture.longPress(2000));
    }

    @Test public void boundedHistoryReturnsNewestEventsInOrderWithoutConsuming() throws Exception {
        TouchMemory memory = new TouchMemory();
        for (int index = 0; index < 40; index++) memory.add(new TouchGesture.Event("tap", 50, 0), index * 1000, 1700000000000L + index * 1000);
        JSONObject result = memory.recent(0, 32, 40000);JSONArray events = result.getJSONArray("events");
        assertEquals(32, events.length());assertEquals(9, events.getJSONObject(0).getLong("id"));
        assertEquals(40, result.getLong("latest_id"));assertEquals(1000, events.getJSONObject(31).getLong("age_ms"));
        assertEquals(32, memory.recent(0, 32, 40000).getJSONArray("events").length());
        JSONArray recent = memory.recent(38, 1, 40000).getJSONArray("events");
        assertEquals(1, recent.length());assertEquals(40, recent.getJSONObject(0).getLong("id"));
        assertEquals(0, memory.recent(40, 6, 40000).getJSONArray("events").length());
    }

    @Test public void expiryAndMissingSensorsAreExplicit() throws Exception {
        TouchMemory memory = new TouchMemory();memory.add(new TouchGesture.Event("stroke", 800, 120), 1000, 1700000000000L);
        JSONObject before = memory.recent(0, 6, 300999);
        assertEquals(1, before.getJSONArray("events").length());assertFalse(before.getBoolean("temperature_available"));assertFalse(before.getBoolean("pressure_available"));
        assertEquals(0, memory.recent(0, 6, 301000).getJSONArray("events").length());
        assertTrue(memory.summary(301000).contains("没有触摸"));
    }

    @Test public void invalidHistoryLimitsAreRejected() throws Exception {
        TouchMemory memory = new TouchMemory();
        for (int limit : new int[]{0, -1, 33, Integer.MAX_VALUE}) {
            try { memory.recent(0, limit, 0);fail(); } catch (IllegalArgumentException expected) {}
        }
        try { memory.recent(-1, 6, 0);fail(); } catch (IllegalArgumentException expected) {}
    }
}
