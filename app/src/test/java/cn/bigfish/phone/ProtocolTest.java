package cn.bigfish.phone;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProtocolTest {
    @Test public void httpAndHttpsVisionUrlsAreAccepted() {
        assertTrue(Protocol.validUrl("http://192.168.1.2:8000/v1/chat/completions", false));
        assertTrue(Protocol.validUrl("https://example.com/vision", false));
        assertFalse(Protocol.validUrl("ftp://example.com/vision", false));
        assertFalse(Protocol.validUrl("https://key@example.com/vision", false));
        assertFalse(Protocol.validUrl("http:///missing-host", false));
        assertFalse(Protocol.validUrl("https://example.com/#secret", false));
    }

    @Test public void officialMcpRequiresWss() {
        assertTrue(Protocol.validUrl("wss://example.com/mcp?token=secret", true));
        assertFalse(Protocol.validUrl("ws://example.com/mcp", true));
        assertFalse(Protocol.validUrl("https://example.com/mcp", true));
    }

    @Test public void perCallVisionAndTokensAreValidated() throws Exception {
        JSONObject vision = new JSONObject().put("url", "http://localhost:8000/vision").put("token", "private");
        JSONObject params = new JSONObject().put("_meta", new JSONObject().put("io.modelcontextprotocol/clientCapabilities", new JSONObject()
            .put("extensions", new JSONObject().put("me.xiaozhi/vision", vision))));
        assertEquals(vision, Protocol.requestVision(params));
        vision.put("token", "private\r\nInjected: true");assertNull(Protocol.requestVision(params));
        assertNull(Protocol.requestVision(new JSONObject()));
    }

    @Test public void contactAliasesAndNumberFormattingAreSupported() {
        assertEquals("+8613800138000", Protocol.resolveNumber("妈妈", "妈妈=+86 (138) 0013-8000\n自己=13900139000"));
        assertEquals("10086", Protocol.resolveNumber("10086", ""));
    }

    @Test public void dialCodesUrisAndUnknownAliasesAreRejected() {
        for (String value : new String[]{"*100#", "tel:13800138000", "13800138000;123", "+1+234", "123\n456", "妈妈", "12", ""}) {
            try { Protocol.resolveNumber(value, "");fail(value); } catch (IllegalArgumentException expected) {}
        }
    }

    @Test public void duplicateContactsAreRejected() {
        try { Protocol.contacts("妈妈=13800138000\n妈妈=13900139000");fail(); } catch (IllegalArgumentException expected) {}
    }

    @Test public void toolsDescribeDirectCallWithoutClaimingConnection() throws Exception {
        JSONObject dial = Protocol.tools().getJSONObject(2);
        assertEquals("self.phone.dial", dial.getString("name"));
        assertTrue(dial.getString("description").contains("无需再次询问"));
        assertTrue(dial.getString("description").contains("不代表已经接通"));
        assertEquals("recipient", dial.getJSONObject("inputSchema").getJSONArray("required").getString(0));
        assertTrue(Protocol.text("failed", true).getBoolean("isError"));
    }

    @Test public void touchToolAdvertisesBoundedReadAndSensorLimitations() throws Exception {
        JSONObject touch = Protocol.tools().getJSONObject(3);
        assertEquals("self.touch.get_recent", touch.getString("name"));
        assertTrue(touch.getString("description").contains("不能测量温度"));
        assertEquals(32, touch.getJSONObject("inputSchema").getJSONObject("properties").getJSONObject("limit").getInt("maximum"));
    }

    @Test public void officialOnlyIsDefaultAndHeaderInjectionIsRejected() throws Exception {
        assertFalse(new Settings(new JSONObject()).useCustom);
        Settings config = new Settings(new JSONObject().put("api_key", "bad\nheader"));
        try { config.validate();fail(); } catch (IllegalArgumentException expected) {}
    }

    @Test public void remoteBlankSecretsPreserveStoredValuesAndDoNotLeak() throws Exception {
        Settings current = new Settings(new JSONObject().put("endpoint", "wss://example.com/?token=private-endpoint").put("api_key", "private-key"));
        Settings changed = WebConfig.merge(current, new JSONObject().put("endpoint", "").put("api_key", "").put("model", "new-model"));
        assertEquals(current.endpoint, changed.endpoint);assertEquals(current.apiKey, changed.apiKey);assertEquals("new-model", changed.model);
        String visible = WebConfig.visible(changed, "connected").toString();
        assertFalse(visible.contains("private-key"));assertFalse(visible.contains("private-endpoint"));
        assertTrue(WebConfig.visible(changed, "connected").getBoolean("api_key_set"));
    }

    @Test public void remoteExplicitClearRemovesSecretsWithoutChangingOriginal() throws Exception {
        Settings current = new Settings(new JSONObject().put("endpoint", "wss://example.com/").put("api_key", "saved"));
        Settings changed = WebConfig.merge(current, new JSONObject().put("clear_endpoint", true).put("clear_api_key", true));
        assertEquals("", changed.endpoint);assertEquals("", changed.apiKey);assertEquals("saved", current.apiKey);
    }

    @Test public void challengeRequiresEveryShapeColorAndCode() throws Exception {
        JSONObject expected = new JSONObject("{\"left\":{\"color\":\"red\",\"shape\":\"circle\"},\"center\":{\"color\":\"green\",\"shape\":\"square\"},\"right\":{\"color\":\"blue\",\"shape\":\"triangle\"},\"code\":\"7392\"}");
        assertTrue(VisionChallenge.matches("```json\n" + expected + "\n```", expected));
        JSONObject wrong = new JSONObject(expected.toString()).put("code", "1111");assertFalse(VisionChallenge.matches(wrong.toString(), expected));
        assertFalse(VisionChallenge.matches("I can see images", expected));
        assertFalse(VisionChallenge.QUESTION.contains("7392"));
    }

    @Test public void modernResponsesKeepRequestIdAndServerMetadata() throws Exception {
        JSONObject request = new JSONObject().put("id", "call-123").put("method", "tools/call").put("params", new JSONObject()
            .put("_meta", new JSONObject().put("io.modelcontextprotocol/protocolVersion", "2026-07-28").put("io.modelcontextprotocol/clientCapabilities", new JSONObject())));
        JSONObject response = Protocol.response(request, Protocol.text("picture answer", false));
        assertEquals("call-123", response.getString("id"));
        assertEquals("complete", response.getJSONObject("result").getString("resultType"));
        assertEquals("companion", response.getJSONObject("result").getJSONObject("_meta").getJSONObject("io.modelcontextprotocol/serverInfo").getString("name"));
        assertFalse(response.getJSONObject("result").getBoolean("isError"));
    }

    @Test public void legacyResponsesRemainCompatibleAndDiscoveryUsesModernEnvelope() throws Exception {
        JSONObject legacy = Protocol.response(new JSONObject().put("id", 7).put("method", "tools/list"), new JSONObject().put("tools", Protocol.tools()));
        assertFalse(legacy.getJSONObject("result").has("resultType"));
        JSONObject discover = Protocol.response(new JSONObject().put("id", 8).put("method", "server/discover"), new JSONObject());
        assertEquals("complete", discover.getJSONObject("result").getString("resultType"));
    }
}
