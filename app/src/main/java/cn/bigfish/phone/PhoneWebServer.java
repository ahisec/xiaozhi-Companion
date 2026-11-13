package cn.bigfish.phone;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class PhoneWebServer {
    private final Context context;
    private final CompanionService service;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService clients = Executors.newFixedThreadPool(3);
    private final String token = "peiban@520";
    private ServerSocket server;
    private volatile boolean running;

    public PhoneWebServer(Context context, CompanionService service) {
        this.context = context;this.service = service;
    }

    public void start() throws Exception {
        start(8769);
    }

    void start(int port) throws Exception {
        server = new ServerSocket(port, 8);running = true;
        Thread thread = new Thread(() -> {
            while (running) {
                try { Socket client = server.accept();clients.execute(() -> handle(client)); }
                catch (Exception error) { if (!running) break; }
            }
        }, "bigfish-web");thread.setDaemon(true);thread.start();
    }

    int port() { return server.getLocalPort(); }

    public String addresses() {
        StringBuilder result = new StringBuilder();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback()) continue;
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress())
                        result.append("http://").append(address.getHostAddress()).append(':').append(port()).append("/#").append(token).append('\n');
                }
            }
        } catch (Exception ignored) {}
        return result.length() == 0 ? "请先连接 Wi-Fi，然后重新开启网页配置" : result.toString().trim();
    }

    private String line(InputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') break;
            if (bytes.size() >= 4096) throw new IllegalArgumentException("Header too long");
            if (value != '\r') bytes.write(value);
        }
        return bytes.toString("UTF-8");
    }

    private void handle(Socket client) {
        try (Socket connection = client) {
            connection.setSoTimeout(10000);
            InputStream input = connection.getInputStream();OutputStream output = connection.getOutputStream();
            String[] request = line(input).split(" ");
            if (request.length != 3) { reply(output, 400, "application/json", "{\"error\":\"请求格式不正确\"}".getBytes(StandardCharsets.UTF_8));return; }
            Map<String, String> headers = new HashMap<>();
            int headerSize = 0;
            while (true) {
                String header = line(input);if (header.isEmpty()) break;
                headerSize += header.length();if (headerSize > 16384) throw new IllegalArgumentException("Headers too large");
                int delimiter = header.indexOf(':');
                if (delimiter > 0) headers.put(header.substring(0, delimiter).trim().toLowerCase(Locale.ROOT), header.substring(delimiter + 1).trim());
            }
            String path = request[1];String method = request[0];
            if ("GET".equals(method) && "/".equals(path)) {
                try (InputStream asset = context.getAssets().open("config.html")) {
                    ByteArrayOutputStream content = new ByteArrayOutputStream();byte[] buffer = new byte[8192];int count;
                    while ((count = asset.read(buffer)) != -1) content.write(buffer, 0, count);
                    reply(output, 200, "text/html; charset=utf-8", content.toByteArray());
                }
                return;
            }
            byte[] supplied = headers.getOrDefault("x-admin-key", "").getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied)) {
                reply(output, 403, "application/json", "{\"error\":\"请使用手机显示的完整配置链接或访问口令\"}".getBytes(StandardCharsets.UTF_8));return;
            }
            try {
                JSONObject result;
                if ("GET".equals(method) && "/api/config".equals(path)) result = config();
                else if ("POST".equals(method)) {
                    if (headers.containsKey("transfer-encoding")) throw new IllegalArgumentException("不支持分块请求");
                    int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
                    if (length < 0 || length > 32768) throw new IllegalArgumentException("请求过大");
                    byte[] content = new byte[length];int offset = 0;
                    while (offset < length) { int count = input.read(content, offset, length - offset);if (count < 0) throw new IllegalArgumentException("请求不完整");offset += count; }
                    JSONObject values = length == 0 ? new JSONObject() : new JSONObject(new String(content, StandardCharsets.UTF_8));
                    if ("/api/config".equals(path)) {
                        Settings updated = merged(values);
                        onMain(() -> { service.update(updated);return null; });result = new JSONObject().put("ok", true);
                    } else if ("/api/connect".equals(path)) { onMain(() -> { service.connectNow();return null; });result = new JSONObject().put("ok", true); }
                    else if ("/api/camera-off".equals(path)) { onMain(() -> { service.stopCamera();return null; });result = new JSONObject().put("ok", true); }
                    else if ("/api/test".equals(path)) {
                        Settings config = merged(values);
                        CompletableFuture<JSONObject> test = new CompletableFuture<>();
                        service.testModel(config, values.optBoolean("official", true), (challenge, report) -> {
                            try { test.complete(new JSONObject().put("report", report).put("image", challenge == null ? "" : "data:image/jpeg;base64," + Base64.encodeToString(challenge.jpeg, Base64.NO_WRAP))); }
                            catch (Exception error) { test.completeExceptionally(error); }
                        });
                        result = test.get(55, TimeUnit.SECONDS);
                    } else throw new IllegalArgumentException("未知操作");
                } else { reply(output, 404, "application/json", "{}".getBytes(StandardCharsets.UTF_8));return; }
                reply(output, 200, "application/json; charset=utf-8", result.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception error) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                String message = cause instanceof IllegalArgumentException ? cause.getMessage() : "操作未完成，请检查手机状态和配置";
                reply(output, 400, "application/json; charset=utf-8", new JSONObject().put("error", message).toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    private Settings merged(JSONObject values) throws Exception {
        return WebConfig.merge(service.settings(), values);
    }

    private JSONObject config() throws Exception {
        return WebConfig.visible(service.settings(), service.status());
    }

    private <Result> Result onMain(java.util.concurrent.Callable<Result> action) throws Exception {
        CompletableFuture<Result> completed = new CompletableFuture<>();
        main.post(() -> { try { completed.complete(action.call()); } catch (Exception error) { completed.completeExceptionally(error); } });
        return completed.get(5, TimeUnit.SECONDS);
    }

    private void reply(OutputStream output, int status, String type, byte[] content) throws Exception {
        String header = "HTTP/1.1 " + status + " " + (status == 200 ? "OK" : "Error") + "\r\nContent-Type: " + type + "\r\nContent-Length: " + content.length
            + "\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\n\r\n";
        output.write(header.getBytes(StandardCharsets.US_ASCII));output.write(content);output.flush();
    }

    public void close() { running = false;try { server.close(); } catch (Exception ignored) {}clients.shutdownNow(); }
}
