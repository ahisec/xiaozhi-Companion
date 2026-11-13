package cn.bigfish.phone;

import org.junit.Test;
import java.net.HttpURLConnection;
import java.net.URL;
import static org.junit.Assert.*;

public class PhoneWebServerTest {
    @Test public void networkRequestsRequireTheDefaultPassword() throws Exception {
        PhoneWebServer server = new PhoneWebServer(null, null);server.start(0);
        try {
            URL url = new URL("http://127.0.0.1:" + server.port() + "/api/not-a-command");
            HttpURLConnection denied = (HttpURLConnection) url.openConnection();denied.setReadTimeout(3000);
            assertEquals(403, denied.getResponseCode());
            assertNull(denied.getHeaderField("Access-Control-Allow-Origin"));
            assertEquals("no-store", denied.getHeaderField("Cache-Control"));denied.disconnect();
            HttpURLConnection authorized = (HttpURLConnection) url.openConnection();authorized.setReadTimeout(3000);authorized.setRequestProperty("X-Admin-Key", "peiban@520");
            assertEquals(404, authorized.getResponseCode());authorized.disconnect();
            HttpURLConnection wrong = (HttpURLConnection) url.openConnection();wrong.setReadTimeout(3000);wrong.setRequestProperty("X-Admin-Key", "wrong");
            assertEquals(403, wrong.getResponseCode());wrong.disconnect();
        } finally { server.close(); }
    }

    @Test public void defaultPasswordIsAcceptedAfterServerRestart() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            PhoneWebServer server = new PhoneWebServer(null, null);server.start(0);
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + server.port() + "/api/not-a-command").openConnection();
                connection.setReadTimeout(3000);connection.setRequestProperty("X-Admin-Key", "peiban@520");
                assertEquals(404, connection.getResponseCode());connection.disconnect();
            } finally { server.close(); }
        }
    }
}
