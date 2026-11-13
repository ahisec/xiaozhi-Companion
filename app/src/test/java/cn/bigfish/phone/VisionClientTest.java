package cn.bigfish.phone;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import static org.junit.Assert.*;

public class VisionClientTest {
    private MockWebServer server;
    private VisionClient client;
    @Before public void setup() throws Exception { server = new MockWebServer();server.start();client = new VisionClient(); }
    @After public void close() throws Exception { server.shutdown(); }
    private Settings config() throws Exception {
        return new Settings(new JSONObject().put("completion_url", server.url("/v1/chat/completions").toString()).put("model", "test-vision").put("api_key", "test-secret"));
    }

    @Test public void compatibleSendsActualImageAndFastBudgetOverHttp() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"choices\":[{\"message\":{\"content\":\"green cup\"}}]}"));
        assertEquals("green cup", client.compatible(new byte[]{1, 2, 3}, "What is here?", config()));
        RecordedRequest request = server.takeRequest();assertEquals("Bearer test-secret", request.getHeader("Authorization"));
        JSONObject body = new JSONObject(request.getBody().readUtf8());assertEquals(384, body.getInt("max_tokens"));
        assertEquals("data:image/jpeg;base64,AQID", body.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(1).getJSONObject("image_url").getString("url"));
    }

    @Test public void officialSendsMultipartAndSessionToken() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"answer\":\"cup\"}"));
        JSONObject vision = new JSONObject().put("url", server.url("/vision").toString()).put("token", "official-test");
        Settings settings = new Settings(config().json().put("device_id", "real-device"));
        assertTrue(client.official("JPEG-PHOTO".getBytes(StandardCharsets.UTF_8), "look now", vision, settings, "client-123").contains("cup"));
        RecordedRequest request = server.takeRequest();String body = request.getBody().readUtf8();
        assertEquals("Bearer official-test", request.getHeader("Authorization"));assertEquals("real-device", request.getHeader("Device-Id"));
        assertEquals("client-123", request.getHeader("Client-Id"));assertTrue(body.contains("name=\"question\""));assertTrue(body.contains("name=\"file\""));assertTrue(body.contains("JPEG-PHOTO"));
    }

    @Test public void redirectsAreNotFollowedAndErrorsDoNotLeakHeaders() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", server.url("/wrong")));
        try { client.compatible(new byte[]{1}, "look", config());fail(); }
        catch (java.io.IOException error) { assertTrue(error.getMessage().contains("302"));assertFalse(error.getMessage().contains("test-secret")); }
        assertEquals(1, server.getRequestCount());
    }

    @Test public void emptyModelContentIsRejected() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"choices\":[{\"message\":{\"content\":\"\"}}]}"));
        try { client.compatible(new byte[]{1}, "look", config());fail(); } catch (java.io.IOException expected) {}
    }

    @Test public void officialMissingCapabilityNeverFallsBackToCustom() throws Exception {
        try { client.official(new byte[]{1}, "look", null, config(), "client");fail(); } catch (IllegalArgumentException expected) {}
        assertEquals(0, server.getRequestCount());
    }
}
