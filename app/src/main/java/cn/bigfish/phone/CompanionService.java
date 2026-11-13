package cn.bigfish.phone;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.TelecomManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class CompanionService extends Service {
    public interface Listener { void changed(); }
    public interface TestCallback { void complete(VisionChallenge challenge, String result); }
    public final class LocalBinder extends Binder { public CompanionService service() { return CompanionService.this; } }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newFixedThreadPool(2);
    private final AtomicBoolean busy = new AtomicBoolean();
    private final VisionClient visionClient = new VisionClient();
    private final TouchMemory touchMemory = new TouchMemory();
    private final DirectDial directDial = new DirectDial(new DirectDial.Gateway() {
        @Override public boolean allowed() { return checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED; }
        @Override public void call(String number) {
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
                throw new IllegalStateException("电话权限已撤销，请在手机重新授权");
            TelecomManager telecom = getSystemService(TelecomManager.class);
            if (telecom == null) throw new IllegalStateException("手机没有可用的电话服务");
            telecom.placeCall(Uri.fromParts("tel", number, null), new Bundle());
        }
    });
    private final OkHttpClient socketClient = new OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build();
    private WebSocket socket;
    private volatile int epoch;
    private int retrySeconds = 2;
    private volatile boolean connected;
    private volatile boolean running;
    private volatile boolean cameraRequested;
    private volatile boolean cameraSwitching;
    private volatile JSONObject officialVision;
    private volatile Settings settings;
    private volatile Listener listener;
    private volatile String connectionStatus = "尚未连接 MCP";
    private volatile String cameraStatus = "相机已关闭";
    private volatile String answer = "";
    private volatile String metrics = "";
    private volatile String dialStatus = "尚未发起呼叫";
    private PowerManager.WakeLock wakeLock;
    public CameraController camera;
    private String clientId;
    private PhoneWebServer web;
    private final Runnable renewCameraWakeLock = new Runnable() {
        @Override public void run() {
            if (cameraRequested && running) {
                wakeLock.acquire(10 * 60 * 1000L);
                main.postDelayed(this, 5 * 60 * 1000L);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("companion", "相机与 MCP 运行状态", NotificationManager.IMPORTANCE_LOW));
        manager.deleteNotificationChannel("dial");
        clientId = getSharedPreferences("identity", MODE_PRIVATE).getString("id", "");
        if (clientId.isEmpty()) { clientId = UUID.randomUUID().toString();getSharedPreferences("identity", MODE_PRIVATE).edit().putString("id", clientId).apply(); }
        try { settings = new SecretStore(this).load(); }
        catch (Exception error) { settings = new Settings(new JSONObject());connectionStatus = "配置无法解密，请重新保存配置"; }
        camera = new CameraController(this, getSharedPreferences("camera", MODE_PRIVATE).getBoolean("front", false), status -> {
            cameraStatus = status;
            boolean failed = !camera.isReady();
            main.post(() -> {
                cameraSwitching = false;
                if (failed && cameraRequested) {
                    cameraRequested = false;main.removeCallbacks(renewCameraWakeLock);
                    if (wakeLock.isHeld()) wakeLock.release();
                    if (running) foreground(false);
                }
                changed();
            });
        });
        wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bigfish:camera");
        wakeLock.setReferenceCounted(false);
    }

    @Override public IBinder onBind(Intent intent) { return new LocalBinder(); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if ("stop".equals(action)) { stopAll();return START_NOT_STICKY; }
        if ("camera_off".equals(action)) { stopCamera();return START_NOT_STICKY; }
        foreground(cameraRequested);
        running = true;
        if ("camera_on".equals(action)) startCamera();
        if ("web_on".equals(action)) {
            try { if (web == null) { web = new PhoneWebServer(this, this);web.start(); } }
            catch (Exception error) { web = null;connectionStatus = "网页配置端口开启失败，请确认 8769 未被占用"; }
            changed();
        }
        if (!settings.endpoint.isEmpty() && socket == null) connect();
        return START_NOT_STICKY;
    }

    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, CompanionService.class).setAction("stop"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cameraOff = PendingIntent.getService(this, 2, new Intent(this, CompanionService.class).setAction("camera_off"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, "companion").setSmallIcon(cn.bigfish.phone.R.drawable.ic_fish)
            .setContentTitle(cameraRequested ? "伴侣 · 后台相机已开启" : "伴侣 · MCP 服务运行中")
            .setContentText(cameraRequested ? "仅在工具请求时抓拍上传；点此返回应用" : "点此查看连接、拨号和触摸状态")
            .setContentIntent(open).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE).addAction(0, "停止服务", stop);
        if (cameraRequested) builder.addAction(0, "关闭相机", cameraOff);
        return builder.build();
    }

    private void foreground(boolean cameraActive) {
        if (Build.VERSION.SDK_INT >= 30) startForeground(1, notification(), cameraActive ? ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA : ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(1, notification());
    }

    public void setListener(Listener value) { listener = value; }
    public Settings settings() { return settings; }
    public boolean cameraRequested() { return cameraRequested; }
    public String cameraState() { return "当前选择：" + camera.facingLabel() + "\n" + cameraStatus; }
    public boolean canSwitchCamera() { return !cameraSwitching && !busy.get() && (!cameraRequested || camera.isReady()); }
    public void switchCamera() {
        if (!canSwitchCamera()) throw new IllegalStateException("相机正在启动、切换或识图，请稍后再试");
        boolean wasRequested = cameraRequested;
        cameraSwitching = true;
        if (wasRequested) cameraStatus = "正在切换摄像头…";
        camera.selectFront(!camera.isFrontFacing()).whenComplete((ignored, error) -> main.post(() -> {
            if (error != null) {
                cameraSwitching = false;
                cameraStatus = error instanceof IllegalStateException ? error.getMessage() : "摄像头切换失败，请检查相机权限和设备状态";
            } else {
                getSharedPreferences("camera", MODE_PRIVATE).edit().putBoolean("front", camera.isFrontFacing()).apply();
                if (!wasRequested) cameraStatus = "相机未开启，已保存选择";
                if (!cameraRequested || camera.isReady()) cameraSwitching = false;
            }
            changed();
        }));
        changed();
    }
    public String dialStatus() { return dialStatus; }
    public String requestDial(String recipient) {
        try {
            String result = directDial.dial(recipient, settings.contacts, SystemClock.elapsedRealtime());
            dialStatus = "已向系统提交直接呼叫；接通状态请查看电话应用";changed();return result;
        } catch (Exception error) {
            dialStatus = error instanceof IllegalArgumentException || error instanceof IllegalStateException ? error.getMessage()
                : "系统未接受呼叫，请检查电话权限、SIM 卡和 MIUI 电话设置";
            changed();throw new IllegalStateException(dialStatus);
        }
    }
    public void recordTouch(TouchGesture.Event event) { touchMemory.add(event, SystemClock.elapsedRealtime(), System.currentTimeMillis());changed(); }
    public String touchStatus() { return touchMemory.summary(SystemClock.elapsedRealtime()); }
    public String webAddress() { return web == null ? "网页配置尚未开启" : web.addresses(); }
    public void stopWeb() { if (web != null) { web.close();web = null; }changed(); }
    public void connectNow() {
        if (settings.endpoint.isEmpty()) throw new IllegalArgumentException("请先填写 MCP 接入点");
        epoch++;if (socket != null) socket.cancel();socket = null;connected = false;officialVision = null;connect();
    }

    public void update(Settings value) throws Exception {
        new SecretStore(this).save(value);
        boolean reconnect = !value.endpoint.equals(settings.endpoint);
        settings = value;
        if (reconnect) {
            epoch++;
            if (socket != null) socket.cancel();socket = null;connected = false;officialVision = null;
            if (running && !settings.endpoint.isEmpty()) connect();
        }
        changed();
    }

    private void startCamera() {
        cameraRequested = true;
        try {
            foreground(true);
            main.removeCallbacks(renewCameraWakeLock);renewCameraWakeLock.run();
            cameraStatus = "正在开启相机…";camera.start();changed();
        } catch (Exception error) { cameraRequested = false;cameraStatus = "无法启动相机前台服务，请回到应用授权后重试";changed(); }
    }

    public void stopCamera() {
        cameraRequested = false;cameraSwitching = false;camera.stop();
        main.removeCallbacks(renewCameraWakeLock);
        if (wakeLock.isHeld()) wakeLock.release();
        if (running) foreground(false);
        changed();
    }

    public void stopAll() {
        running = false;epoch++;
        main.removeCallbacks(renewCameraWakeLock);
        if (socket != null) socket.cancel();socket = null;connected = false;officialVision = null;
        stopCamera();stopWeb();connectionStatus = "服务已停止";
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();changed();
    }

    private void changed() { main.post(() -> { if (listener != null) listener.changed(); }); }

    public String status() {
        String vision = officialVision != null ? "官方已下发视觉接口，识图优先使用官方通道" : "官方尚未下发会话视觉接口；工具调用也可能携带单次视觉权限";
        return connectionStatus + "\n" + cameraStatus + "\n" + vision + "\n"
            + (settings.useCustom ? "已允许自定义视觉备用接口" : "仅官方视觉模式，不自动调用自定义模型")
            + (busy.get() ? "\n正在抓拍或识图…" : "") + (metrics.isEmpty() ? "" : "\n" + metrics)
            + (answer.isEmpty() ? "" : "\n最近结果：\n" + answer);
    }

    private void connect() {
        int current = ++epoch;
        connectionStatus = "正在连接官方 MCP…";changed();
        socket = socketClient.newWebSocket(new Request.Builder().url(settings.endpoint).build(), new WebSocketListener() {
            @Override public void onOpen(WebSocket connection, Response response) {
                main.post(() -> { if (current != epoch) { connection.cancel();return; } connected = true;retrySeconds = 2;connectionStatus = "MCP WebSocket 已连接，等待工具调用";changed(); });
            }
            @Override public void onMessage(WebSocket connection, String text) {
                if (current != epoch) return;
                try {
                    JSONObject request = new JSONObject(text);
                    if (!"2.0".equals(request.optString("jsonrpc"))) { sendError(connection, request.opt("id"), -32600, "Invalid JSON-RPC request");return; }
                    if (!request.has("id")) return;
                    if ("tools/call".equals(request.optString("method"))) {
                        JSONObject params = request.optJSONObject("params");
                        if (params == null) { sendError(connection, request.opt("id"), -32602, "Invalid params");return; }
                        if ("self.camera.take_photo".equals(params.optString("name"))) {
                            if (!busy.compareAndSet(false, true)) { sendResult(connection, request, Protocol.text("相机正在处理上一个请求，请稍后再试", true));return; }
                            worker.execute(() -> {
                                try { sendResult(connection, request, cameraResult(params)); }
                                catch (Exception error) { safeToolError(connection, request, "抓拍或识图失败，请检查相机和视觉通道"); }
                                finally { busy.set(false);changed(); }
                            });
                        } else handleCall(connection, request, params);
                    } else handleControl(connection, request);
                } catch (Exception error) { sendError(connection, null, -32700, "Invalid JSON-RPC request"); }
            }
            @Override public void onFailure(WebSocket connection, Throwable error, Response response) { reconnect(current); }
            @Override public void onClosed(WebSocket connection, int code, String reason) { reconnect(current); }
            @Override public void onClosing(WebSocket connection, int code, String reason) { connection.close(code, null); }
        });
    }

    private void reconnect(int current) {
        main.post(() -> {
            if (current != epoch || !running) return;
            socket = null;connected = false;officialVision = null;
            connectionStatus = "MCP 连接中断，将自动重连；请检查网络和接入点";changed();
            int delay = retrySeconds;retrySeconds = Math.min(30, retrySeconds * 2);
            main.postDelayed(() -> { if (current == epoch && running && socket == null && !settings.endpoint.isEmpty()) connect(); }, delay * 1000L);
        });
    }

    private void handleControl(WebSocket connection, JSONObject request) throws Exception {
        String method = request.optString("method");
        JSONObject params = request.optJSONObject("params");
        JSONObject result;
        if ("initialize".equals(method)) {
            JSONObject capabilities = params == null ? null : params.optJSONObject("capabilities");
            officialVision = capabilities == null ? null : Protocol.vision(capabilities.optJSONObject("vision"));
            String version = params == null ? "" : params.optString("protocolVersion");
            if (!version.equals("2025-03-26") && !version.equals("2025-06-18")) version = "2024-11-05";
            result = new JSONObject().put("protocolVersion", version).put("capabilities", new JSONObject().put("tools", new JSONObject()))
                .put("serverInfo", new JSONObject().put("name", "companion").put("version", BuildConfig.VERSION_NAME));changed();
        } else if ("server/discover".equals(method)) {
            result = new JSONObject().put("supportedVersions", new JSONArray().put("2026-07-28").put("2024-11-05"))
                .put("capabilities", new JSONObject().put("tools", new JSONObject()).put("extensions", new JSONObject().put("me.xiaozhi/vision", new JSONObject())))
                .put("ttlMs", 60000).put("cacheScope", "private");
        } else if ("tools/list".equals(method)) result = new JSONObject().put("tools", Protocol.tools());
        else if ("ping".equals(method)) result = new JSONObject();
        else { sendError(connection, request.opt("id"), -32601, "Method not found");return; }
        sendResult(connection, request, result);
    }

    private void handleCall(WebSocket connection, JSONObject request, JSONObject params) throws Exception {
        String name = params.optString("name");
        if ("phone_camera.get_status".equals(name)) {
            JSONObject status = new JSONObject().put("connected", connected).put("camera_enabled", cameraRequested).put("camera_ready", camera.isReady())
                .put("camera_facing", camera.isFrontFacing() ? "front" : "back").put("camera_switching", cameraSwitching)
                .put("official_vision_available", officialVision != null).put("custom_vision_allowed", settings.useCustom).put("recognizing", busy.get());
            sendResult(connection, request, Protocol.text(status.toString(), false));
        } else if ("self.phone.dial".equals(name)) {
            JSONObject arguments = params.optJSONObject("arguments");
            if (arguments == null || !(arguments.opt("recipient") instanceof String)) { safeToolError(connection, request, "请提供联系人称呼或电话号码");return; }
            String recipient = arguments.getString("recipient");int current = epoch;
            main.post(() -> {
                if (current != epoch || !running) { safeToolError(connection, request, "连接已变更或服务已停止，未执行呼叫");return; }
                try { sendResult(connection, request, Protocol.text(requestDial(recipient), false)); }
                catch (Exception error) { safeToolError(connection, request, error.getMessage()); }
            });
        } else if ("self.touch.get_recent".equals(name)) {
            try {
                JSONObject arguments = params.optJSONObject("arguments");
                long afterId = integerArgument(arguments, "after_id", 0);
                long count = integerArgument(arguments, "limit", 6);
                if (count < 1 || count > 32) throw new IllegalArgumentException("limit 需为 1～32 的整数");
                sendResult(connection, request, Protocol.text(touchMemory.recent(afterId, (int) count, SystemClock.elapsedRealtime()).toString(), false));
            } catch (IllegalArgumentException error) { safeToolError(connection, request, error.getMessage()); }
        } else safeToolError(connection, request, "未知工具");
    }

    private long integerArgument(JSONObject arguments, String key, long fallback) {
        if (arguments == null || !arguments.has(key)) return fallback;
        Object value = arguments.opt(key);
        if (!(value instanceof Integer) && !(value instanceof Long)) throw new IllegalArgumentException(key + " 需为整数");
        long number = ((Number) value).longValue();
        if (number < 0) throw new IllegalArgumentException(key + " 不能为负数");
        return number;
    }

    private JSONObject cameraResult(JSONObject params) throws Exception {
        if (cameraSwitching) return Protocol.text("摄像头正在切换，请等相机就绪后重新提问，本次未使用旧照片", true);
        JSONObject arguments = params.optJSONObject("arguments");
        if (arguments == null || !(arguments.opt("question") instanceof String)) return Protocol.text("请提供具体图片问题", true);
        String question = arguments.getString("question").trim();
        if (question.isEmpty() || question.length() > 2000) return Protocol.text("图片问题需为 1～2000 字", true);
        long started = SystemClock.elapsedRealtime();
        Settings config = settings;
        JSONObject vision = Protocol.requestVision(params);
        if (vision == null) vision = officialVision;
        if (vision == null && !config.useCustom) return Protocol.text("官方未向本次 MCP 调用下发视觉接口。开发者权益不等于外部 MCP 自动获得访问地址；当前仅官方模式，未调用自定义服务。", true);
        try {
            byte[] source = camera.capture().get(12, TimeUnit.SECONDS);
            byte[] photo = resize(source, config.detail ? 1280 : 768);
            long captured = SystemClock.elapsedRealtime();
            if (!config.detail) question += "\n请先用一到三句话直接回答核心问题，除非用户要求，不展开长篇说明。";
            String result = vision != null ? visionClient.official(photo, question, vision, config, clientId) : visionClient.compatible(photo, question, config);
            long completed = SystemClock.elapsedRealtime();
            metrics = String.format(java.util.Locale.ROOT, "抓拍压缩 %.2fs / 视觉接口 %.2fs / 合计 %.2fs / 图片 %dKB / %s", (captured - started) / 1000.0, (completed - captured) / 1000.0, (completed - started) / 1000.0, photo.length / 1024, vision != null ? "官方视觉" : "自定义视觉");
            answer = result;changed();
            return Protocol.text("本次提问后手机" + camera.facingLabel() + "抓拍单张照片的识图结果：\n" + result, false);
        } catch (Exception error) {
            answer = "";metrics = "本次抓拍或识图失败，未使用旧照片";changed();
            Throwable cause = error.getCause() == null ? error : error.getCause();
            String message = cause instanceof IllegalStateException || cause instanceof IllegalArgumentException ? cause.getMessage() : "抓拍或视觉请求失败，请检查相机状态、接口权限和网络（超时上限约 45 秒）";
            return Protocol.text(message, true);
        }
    }

    private byte[] resize(byte[] bytes, int limit) throws Exception {
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap == null) throw new IllegalStateException("相机返回了无效图片");
        ExifInterface exif = new ExifInterface(new java.io.ByteArrayInputStream(bytes));
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        Matrix matrix = new Matrix();
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) matrix.postRotate(90);
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) matrix.postRotate(180);
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) matrix.postRotate(270);
        if (!matrix.isIdentity()) {
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) bitmap.recycle();bitmap = rotated;
        }
        float scale = Math.min(1f, (float) limit / Math.max(bitmap.getWidth(), bitmap.getHeight()));
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
        ByteArrayOutputStream output = new ByteArrayOutputStream();resized.compress(Bitmap.CompressFormat.JPEG, 80, output);
        if (resized != bitmap) resized.recycle();bitmap.recycle();return output.toByteArray();
    }

    public void testModel(Settings config, boolean official, TestCallback callback) {
        worker.execute(() -> {
            VisionChallenge challenge = null;
            String report;
            long started = SystemClock.elapsedRealtime();
            try {
                challenge = new VisionChallenge();
                String text = official ? visionClient.official(challenge.jpeg, VisionChallenge.QUESTION, officialVision, config, clientId)
                    : visionClient.compatible(challenge.jpeg, VisionChallenge.QUESTION, config);
                if (official) {
                    try {
                        JSONObject wrapped = new JSONObject(text);
                        if (wrapped.opt("answer") instanceof String) text = wrapped.getString("answer");
                        else if (wrapped.opt("text") instanceof String) text = wrapped.getString("text");
                    } catch (Exception ignored) {}
                }
                report = (VisionChallenge.matches(text, challenge.expected) ? "测试通过：图形、颜色、位置和数字全部正确" : "未通过本次校验，请对照图片和原始回答；接口返回文字不等于识图成功")
                    + "\n耗时 " + (SystemClock.elapsedRealtime() - started) / 1000.0 + " 秒\n正确答案：" + challenge.expected + "\n原始回答：\n" + text;
            } catch (Exception error) {
                report = official ? "官方视觉测试未完成：需要会话下发的有效视觉权限；请先连接 MCP。未调用自定义服务。" : "自定义视觉测试失败，请检查 URL、模型、Key 及网络";
            }
            VisionChallenge shown = challenge;String result = report;main.post(() -> callback.complete(shown, result));
        });
    }

    private void sendResult(WebSocket connection, JSONObject request, JSONObject result) {
        try { connection.send(Protocol.response(request, result).toString()); }
        catch (Exception ignored) {}
    }
    private void safeToolError(WebSocket connection, JSONObject request, String message) {
        try { sendResult(connection, request, Protocol.text(message, true)); } catch (Exception ignored) {}
    }
    private void sendError(WebSocket connection, Object identifier, int code, String message) {
        try { connection.send(new JSONObject().put("jsonrpc", "2.0").put("id", identifier == null ? JSONObject.NULL : identifier).put("error", new JSONObject().put("code", code).put("message", message)).toString()); }
        catch (Exception ignored) {}
    }

    @Override public void onDestroy() {
        running = false;epoch++;
        if (socket != null) socket.cancel();
        camera.release();
        if (web != null) web.close();
        if (wakeLock.isHeld()) wakeLock.release();
        worker.shutdownNow();socketClient.dispatcher().executorService().shutdown();
        super.onDestroy();
    }

    @Override public void onTimeout(int startId, int foregroundServiceType) { stopAll(); }
}
