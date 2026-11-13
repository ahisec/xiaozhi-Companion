package cn.bigfish.phone;

import org.junit.Test;
import static org.junit.Assert.*;

public class DirectDialTest {
    private static final class FakePhone implements DirectDial.Gateway {
        boolean granted = true;
        boolean fail;
        int calls;
        String number;
        @Override public boolean allowed() { return granted; }
        @Override public void call(String number) {
            if (fail) throw new IllegalStateException("system rejected");
            calls++;this.number = number;
        }
    }

    @Test public void aliasImmediatelyInvokesSystemGateway() {
        FakePhone phone = new FakePhone();DirectDial dial = new DirectDial(phone);
        String result = dial.dial("妈妈", "妈妈=+86 (138) 0013-8000", 1000);
        assertEquals(1, phone.calls);assertEquals("+8613800138000", phone.number);
        assertTrue(result.contains("无需在伴侣应用确认"));assertTrue(result.contains("无法确认"));
    }

    @Test public void permissionDenialDoesNotQueueOrInvokeCall() {
        FakePhone phone = new FakePhone();phone.granted = false;DirectDial dial = new DirectDial(phone);
        try { dial.dial("10086", "", 1000);fail(); } catch (IllegalStateException expected) {}
        assertEquals(0, phone.calls);
        phone.granted = true;dial.dial("10086", "", 1001);assertEquals(1, phone.calls);
    }

    @Test public void repeatedSameNumberIsSuppressedForFiveSeconds() {
        FakePhone phone = new FakePhone();DirectDial dial = new DirectDial(phone);dial.dial("10086", "", 1000);
        try { dial.dial("10086", "", 5999);fail(); } catch (IllegalStateException expected) {}
        assertEquals(1, phone.calls);
        dial.dial("10086", "", 6000);assertEquals(2, phone.calls);
    }

    @Test public void invalidNumberAndSystemFailureAreNotReportedAsSuccess() {
        FakePhone phone = new FakePhone();DirectDial dial = new DirectDial(phone);
        try { dial.dial("*100#", "", 1000);fail(); } catch (IllegalArgumentException expected) {}
        assertEquals(0, phone.calls);phone.fail = true;
        try { dial.dial("10086", "", 1001);fail(); } catch (IllegalStateException expected) {}
        phone.fail = false;dial.dial("10086", "", 1002);assertEquals(1, phone.calls);
    }
}
