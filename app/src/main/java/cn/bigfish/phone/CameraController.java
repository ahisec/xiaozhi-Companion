package cn.bigfish.phone;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;

public final class CameraController {
    public interface Listener { void changed(String status); }
    private final Context context;
    private final Listener listener;
    private final HandlerThread thread = new HandlerThread("bigfish-camera");
    private final Handler handler;
    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader jpeg;
    private ImageReader drain;
    private Surface preview;
    private SurfaceTexture previewTexture;
    private CameraCharacteristics characteristics;
    private CompletableFuture<byte[]> pending;
    private long expectedTimestamp;
    private long imageTimestamp;
    private byte[] pendingBytes;
    private boolean requested;
    private int generation;
    private volatile boolean ready;
    private volatile boolean frontFacing;

    public CameraController(Context context, boolean frontFacing, Listener listener) {
        this.context = context;
        this.frontFacing = frontFacing;
        this.listener = listener;
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    public boolean isReady() { return ready; }
    public boolean isFrontFacing() { return frontFacing; }
    public String facingLabel() { return frontFacing ? "前置摄像头" : "后置摄像头"; }

    private String chooseCamera(boolean front) throws Exception {
        CameraManager manager = context.getSystemService(CameraManager.class);
        String[] identifiers = manager.getCameraIdList();int[] facings = new int[identifiers.length];
        for (int index = 0; index < identifiers.length; index++) {
            Integer facing = manager.getCameraCharacteristics(identifiers[index]).get(CameraCharacteristics.LENS_FACING);
            facings[index] = facing == null ? -1 : facing;
        }
        return CameraPolicy.select(identifiers, facings, front);
    }

    public CompletableFuture<Void> selectFront(boolean front) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        handler.post(() -> {
            try {
                if (pending != null) throw new IllegalStateException("正在抓拍，请完成后再切换摄像头");
                chooseCamera(front);
                if (frontFacing != front) {
                    boolean restart = requested;
                    stopInternal();frontFacing = front;
                    if (restart) startInternal();
                }
                result.complete(null);
            } catch (Exception error) { result.completeExceptionally(error); }
        });
        return result;
    }

    public void setPreview(SurfaceTexture texture) {
        handler.post(() -> {
            if (previewTexture == texture) return;
            previewTexture = texture;
            if (session != null) { session.close(); session = null; }
            ready = false;
            failPending("相机预览正在切换，请重新提问");
            if (preview != null) { preview.release(); preview = null; }
            if (texture != null) { texture.setDefaultBufferSize(640, 480); preview = new Surface(texture); }
            if (device != null) configure();
        });
    }

    public void start() {
        handler.post(this::startInternal);
    }

    private void startInternal() {
            if (requested) return;
            if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                listener.changed("请先允许相机权限"); return;
            }
            requested = true;
            int opening = ++generation;
            try {
                CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                String chosen = chooseCamera(frontFacing);
                characteristics = manager.getCameraCharacteristics(chosen);
                Size[] sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
                if (sizes == null || sizes.length == 0) throw new IllegalStateException("所选摄像头不支持 JPEG 抓拍");
                Size size = Arrays.stream(sizes).filter(value -> Math.max(value.getWidth(), value.getHeight()) <= 1600)
                    .max(Comparator.comparingLong(value -> (long) value.getWidth() * value.getHeight())).orElse(sizes[sizes.length - 1]);
                jpeg = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 2);
                jpeg.setOnImageAvailableListener(reader -> {
                    try (Image image = reader.acquireLatestImage()) {
                        if (reader == jpeg && image != null && pending != null) {
                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            byte[] bytes = new byte[buffer.remaining()];buffer.get(bytes);
                            imageTimestamp = image.getTimestamp();pendingBytes = bytes;completeImage();
                        }
                    } catch (Exception error) { if (reader == jpeg) failPending("读取相机画面失败"); }
                }, handler);
                drain = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2);
                drain.setOnImageAvailableListener(reader -> { try (Image image = reader.acquireLatestImage()) {} catch (IllegalStateException ignored) {} }, handler);
                manager.openCamera(chosen, new CameraDevice.StateCallback() {
                    @Override public void onOpened(CameraDevice camera) {
                        if (!requested || opening != generation) { camera.close(); return; }
                        device = camera;configure();
                    }
                    @Override public void onDisconnected(CameraDevice camera) {
                        if (camera != device && opening != generation) { camera.close();return; }
                        camera.close();stopInternal();listener.changed("相机已断开，请回到 App 重新开启");
                    }
                    @Override public void onError(CameraDevice camera, int error) {
                        if (camera != device && opening != generation) { camera.close();return; }
                        camera.close();stopInternal();listener.changed("相机被系统关闭或被其他应用占用，请重新开启");
                    }
                }, handler);
            } catch (Exception error) {
                stopInternal();listener.changed(error instanceof IllegalStateException ? error.getMessage() : "相机开启失败，请检查权限或其他相机应用");
            }
    }

    private void autofocus(CaptureRequest.Builder request) {
        request.set(CaptureRequest.CONTROL_AF_MODE, CameraPolicy.autofocus(characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)));
    }

    private void configure() {
        int current = ++generation;
        Surface target = preview != null ? preview : drain.getSurface();
        try {
            device.createCaptureSession(Arrays.asList(target, jpeg.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession configured) {
                    if (current != generation || device == null) { configured.close(); return; }
                    try {
                        session = configured;
                        CaptureRequest.Builder request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        request.addTarget(target);
                        autofocus(request);
                        session.setRepeatingRequest(request.build(), null, handler);
                        ready = true;listener.changed(facingLabel() + (preview == null ? "后台常开，等待按需抓拍" : "已开启，切后台或锁屏后继续待命"));
                    } catch (Exception error) { stopInternal();listener.changed("相机配置失败，请关闭后重新开启"); }
                }
                @Override public void onConfigureFailed(CameraCaptureSession failed) { if (current == generation) { stopInternal();listener.changed("相机输出配置失败，请重新开启"); } }
            }, handler);
        } catch (Exception error) { stopInternal();listener.changed("相机切换失败，请重新开启"); }
    }

    public CompletableFuture<byte[]> capture() {
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        handler.post(() -> {
            if (!ready || device == null || session == null) { result.completeExceptionally(new IllegalStateException("相机未就绪，请在手机开启相机；本次未使用旧照片")); return; }
            if (pending != null) { result.completeExceptionally(new IllegalStateException("相机正在抓拍，请稍后重试")); return; }
            pending = result;
            expectedTimestamp = 0;imageTimestamp = 0;pendingBytes = null;
            try {
                CaptureRequest.Builder request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                request.addTarget(jpeg.getSurface());
                autofocus(request);
                request.set(CaptureRequest.JPEG_QUALITY, (byte) 85);
                int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation() * 90;
                Integer sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                request.set(CaptureRequest.JPEG_ORIENTATION, CameraPolicy.jpegOrientation(sensor == null ? 90 : sensor, rotation, frontFacing));
                session.capture(request.build(), new CameraCaptureSession.CaptureCallback() {
                    @Override public void onCaptureStarted(CameraCaptureSession captureSession, CaptureRequest captureRequest, long timestamp, long frameNumber) {
                        if (pending == result) { expectedTimestamp = timestamp;completeImage(); }
                    }
                    @Override public void onCaptureFailed(CameraCaptureSession captureSession, CaptureRequest captureRequest, CaptureFailure failure) { if (pending == result) failPending("抓拍失败，请重试"); }
                }, handler);
                handler.postDelayed(() -> { if (pending == result) failPending("相机抓拍超时，本次未使用旧照片"); }, 10000);
            } catch (Exception error) { failPending("无法提交抓拍，请重新开启相机"); }
        });
        return result;
    }

    private void failPending(String message) {
        if (pending != null) { pending.completeExceptionally(new IllegalStateException(message));pending = null; }pendingBytes = null;
    }

    private void completeImage() {
        if (pending != null && expectedTimestamp != 0 && imageTimestamp == expectedTimestamp && pendingBytes != null) {
            CompletableFuture<byte[]> completed = pending;byte[] bytes = pendingBytes;pending = null;pendingBytes = null;completed.complete(bytes);
        }
    }

    private void stopInternal() {
        requested = false;ready = false;generation++;
        failPending("相机已关闭，本次未使用旧照片");
        if (session != null) { session.close();session = null; }
        if (device != null) { device.close();device = null; }
        if (jpeg != null) { jpeg.close();jpeg = null; }
        if (drain != null) { drain.close();drain = null; }
    }

    public void stop() { handler.post(() -> { stopInternal();listener.changed("相机已关闭"); }); }
    public void release() { handler.post(() -> { stopInternal();if (preview != null) preview.release();previewTexture = null;thread.quitSafely(); }); }
}
