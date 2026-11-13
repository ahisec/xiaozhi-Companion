package cn.bigfish.phone;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler();
    private final View[] pages = new View[4];
    private final TextView[] tabs = new TextView[4];
    private CompanionService service;
    private boolean bound;
    private boolean resumed;
    private boolean loaded;
    private boolean restoringTab;
    private int selectedTab = -1;
    private TextView status;
    private TextView feedback;
    private TextView dialStatus;
    private TextView touchStatus;
    private PetTouchView touchPad;
    private TextView testResult;
    private TextView webAddress;
    private TextView cameraState;
    private Button switchCamera;
    private Settings lastConfig;
    private EditText endpoint;
    private EditText completionUrl;
    private EditText model;
    private EditText apiKey;
    private EditText deviceId;
    private EditText contacts;
    private EditText dialRecipient;
    private Switch detail;
    private Switch custom;
    private TextureView preview;
    private ImageView testImage;
    private Button testOfficial;
    private Button testCustom;
    private ViewportScrollView settingsScroll;
    private ViewportScrollView cameraScroll;
    private ViewportScrollView phoneScroll;
    private boolean testing;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { render();handler.postDelayed(this, 1000); }
    };
    private final ServiceConnection binding = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((CompanionService.LocalBinder) binder).service();
            service.setListener(() -> render());
            if (!loaded) {
                load(service.settings());loaded = true;
                if (!restoringTab && service.settings().endpoint.isEmpty()) selectTab(3);
            }
            updatePreview();render();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;settingsScroll.updateStatus(() -> status.setText("系统已停止后台服务，请返回应用后重新连接"));
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);restoringTab = state != null;
        getWindow().setStatusBarColor(Color.rgb(11, 21, 27));getWindow().setNavigationBarColor(Color.rgb(11, 21, 27));
        LinearLayout root = new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(11, 21, 27));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(22), dp(10), dp(22), dp(6));root.addView(header);
        TextView title = text(header, "伴侣", 26);title.setTextColor(Color.WHITE);
        TextView version = text(header, "   " + BuildConfig.VERSION_NAME, 12);version.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout host = new FrameLayout(this);host.setFocusableInTouchMode(true);
        root.addView(host, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        buildTouchPage();buildCameraPage();buildPhonePage();buildSettingsPage();
        for (View page : pages) host.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        feedback = text(root, "手机陪你看世界、感受触摸和拨打电话", 12);
        feedback.setPadding(dp(22), dp(5), dp(22), dp(5));feedback.setMaxLines(3);feedback.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout navigation = new LinearLayout(this);navigation.setPadding(dp(8), dp(4), dp(8), dp(6));root.addView(navigation);
        String[] labels = {"触摸", "看世界", "电话", "设置"};
        for (int index = 0; index < tabs.length; index++) {
            final int target = index;
            TextView tab = new TextView(this);tab.setText(labels[index]);tab.setTextSize(15);tab.setGravity(Gravity.CENTER);
            tab.setClickable(true);tab.setFocusable(true);tab.setContentDescription(labels[index] + "选项卡");
            tab.setOnClickListener(view -> selectTab(target));tabs[index] = tab;
            navigation.addView(tab, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        setContentView(root);selectTab(state == null ? 0 : state.getInt("tab", 0));
        if (state != null) {
            settingsScroll.post(() -> settingsScroll.scrollTo(0, state.getInt("settings_scroll", 0)));
            cameraScroll.post(() -> cameraScroll.scrollTo(0, state.getInt("camera_scroll", 0)));
            phoneScroll.post(() -> phoneScroll.scrollTo(0, state.getInt("phone_scroll", 0)));
        }
    }

    private void buildTouchPage() {
        LinearLayout body = new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(22), 0, dp(22), dp(8));pages[0] = body;
        heading(body, "触摸陪伴", 22);text(body, "轻点是招呼 · 长按是抱抱 · 滑动是抚摸", 13);
        touchPad = new PetTouchView(this, event -> { if (service != null) service.recordTouch(event); });
        body.addView(touchPad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        touchStatus = text(body, "摸摸你的伙伴吧", 14);
        text(body, "摸完对小智说“你感觉到我摸你了吗”。需保持 MCP 连接；不会自动唤醒 M5，也不能测温。", 12);
        text(body, "角色：Deepseek Chan · Dullsaw / Codex Pets", 10);
    }

    private LinearLayout scrollPage(int index) {
        ViewportScrollView page = new ViewportScrollView(this);page.setFillViewport(true);pages[index] = page;
        if (index == 1) cameraScroll = page;else if (index == 2) phoneScroll = page;else settingsScroll = page;
        LinearLayout body = new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(22), 0, dp(22), dp(24));page.addView(body);return body;
    }

    private void buildCameraPage() {
        LinearLayout body = scrollPage(1);heading(body, "一起看世界", 22);
        cameraState = text(body, "正在读取摄像头选择…", 14);
        switchCamera = button(body, "切换前置 / 后置摄像头", view -> {
            if (service == null) { message("服务尚未就绪，请稍后重试");return; }
            try { service.switchCamera();message("正在切换摄像头，选择会自动保存");render(); }
            catch (Exception error) { message(error.getMessage()); }
        });
        preview = new TextureView(this);body.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)));
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) { updatePreview(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) { if (service != null) service.camera.setPreview(null);return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
        });
        button(body, "开启相机 · 后台常开", view -> {
            if (service == null || !save()) return;
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 10);return;
            }
            beginCamera();
        });
        button(body, "关闭相机", view -> { if (service != null) service.stopCamera(); });
        text(body, "开启后可切换 Tab、离开应用或锁屏。相机在本机待命，仅在小智调用工具时抓拍上传；通知栏可以随时关闭。其他应用占用相机或 MIUI 清理进程后需要重新开启。", 13);
        button(body, "打开后台电池设置", view -> {
            try { startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
            catch (Exception error) { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        });
        heading(body, "验证视觉模型", 19);
        testOfficial = button(body, "测试官方视觉", view -> test(true));
        testCustom = button(body, "测试自定义视觉（使用设置页填写值）", view -> test(false));
        text(body, "测试发送随机图形和数字图片，核对全部答案；正确答案不放入提问。不保存当前表单或上传相机照片。官方测试需要会话已下发视觉权限；单次工具调用权限无法提前测试。", 12);
        testImage = new ImageView(this);testImage.setAdjustViewBounds(true);body.addView(testImage);testResult = text(body, "尚未测试", 14);
    }

    private void buildPhonePage() {
        LinearLayout body = scrollPage(2);heading(body, "直接拨打电话", 22);
        dialStatus = text(body, "尚未发起呼叫", 15);button(body, "授权直接拨号", view -> phonePermission());
        text(body, "首次允许电话权限后，小智会直接向系统发起呼叫，无需逐次确认。系统可能要求选择 SIM 卡；通话由手机完成，接通状态请查看电话应用。", 13);
        contacts = field(body, "电话联系人，每行一个", "妈妈=13800138000\n自己=13900139000", false, true);
        text(body, "仅使用你手动保存的称呼映射，不读取系统通讯录。号码示例请替换。", 12);
        button(body, "保存联系人", view -> { if (save()) message("联系人已加密保存"); });
        dialRecipient = field(body, "本机直接呼叫", "输入已保存的称呼或电话号码", false, false);
        button(body, "立即拨打（真实呼叫）", view -> {
            if (service == null || !save()) return;if (!phonePermission()) return;
            try { message(service.requestDial(dialRecipient.getText().toString())); }
            catch (Exception error) { message(error.getMessage()); }
        });
    }

    private void buildSettingsPage() {
        LinearLayout body = scrollPage(3);heading(body, "连接与设置", 22);status = text(body, "正在读取配置…", 14);
        button(body, "连接 MCP", view -> {
            if (!save()) return;
            if (service.settings().endpoint.isEmpty()) { message("请先填写官方 MCP 接入点");return; }
            notificationPermission();startForegroundService(new Intent(this, CompanionService.class).setAction("connect"));
        });
        button(body, "停止全部服务", view -> { if (service != null) service.stopAll(); });
        endpoint = field(body, "官方 MCP 接入点", "wss://…（来自 M5 绑定的智能体）", true, false);
        custom = new Switch(this);custom.setText("官方未下发视觉时，允许自定义备用接口");custom.setTextColor(Color.WHITE);body.addView(custom);
        text(body, "默认仅官方视觉。开发者权益标注限时免费，外部 MCP 视觉权限以实际协商为准。开启备用或手动测试自定义接口可能按服务商规则计费。", 12);
        completionUrl = field(body, "完整 Chat Completions 地址", "支持 http:// 或 https://", false, false);
        model = field(body, "视觉模型名称", "填写你实际使用的模型名", false, false);
        apiKey = field(body, "自定义 API Key", "仅保存在本机加密配置中", true, false);
        deviceId = field(body, "Device-Id（可选）", "仅在官方接口要求时填写真实设备标识", false, false);
        detail = new Switch(this);detail.setText(R.string.detail_mode);detail.setTextColor(Color.WHITE);body.addView(detail);
        button(body, "保存设置", view -> { if (save()) message("已加密保存到手机；若修改接入点会重新连接"); });
        heading(body, "网页远程配置", 21);
        text(body, "手机与电脑连接同一 Wi-Fi。可打开手机地址输入访问口令，或复制完整配置链接到自己的设备。", 13);
        webAddress = text(body, "网页配置尚未开启", 14);webAddress.setTextIsSelectable(true);
        button(body, "开启网页配置", view -> { notificationPermission();startForegroundService(new Intent(this, CompanionService.class).setAction("web_on")); });
        button(body, "复制网页配置链接", view -> {
            if (service == null || !service.webAddress().startsWith("http")) { message("请先连接 Wi-Fi 并开启网页配置");return; }
            android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("伴侣配置链接", service.webAddress().split("\n")[0]));message("已复制带口令的配置链接，请只发送到自己的设备");
        });
        button(body, "关闭网页配置", view -> { if (service != null) service.stopWeb(); });
        text(body, "原电脑桥接程序需要停止，避免同一接入点重复连接和工具重名。", 12);
    }

    private void selectTab(int index) {
        if (index < 0 || index >= pages.length) index = 0;if (selectedTab == index) return;
        View focus = getCurrentFocus();
        if (focus != null) { getSystemService(InputMethodManager.class).hideSoftInputFromWindow(focus.getWindowToken(), 0);focus.clearFocus(); }
        touchPad.setActive(false);if (service != null) service.camera.setPreview(null);selectedTab = index;
        for (int position = 0; position < pages.length; position++) {
            boolean selected = position == index;pages[position].setVisibility(selected ? View.VISIBLE : View.GONE);tabs[position].setSelected(selected);
            tabs[position].setTextColor(selected ? Color.rgb(165, 232, 209) : Color.rgb(144, 170, 183));
            tabs[position].setBackgroundColor(selected ? Color.rgb(23, 54, 58) : Color.rgb(15, 30, 39));
        }
        touchPad.setActive(index == 0 && resumed);updatePreview();
    }

    private void updatePreview() {
        if (service != null) service.camera.setPreview(resumed && selectedTab == 1 && preview.isAvailable() ? preview.getSurfaceTexture() : null);
    }

    private void beginCamera() {
        notificationPermission();startForegroundService(new Intent(this, CompanionService.class).setAction("camera_on"));
        message("后台相机正在启动；可切换 Tab，请允许通知并在 MIUI 中设为无限制后台运行");
    }

    private void notificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
    }

    private boolean phonePermission() {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) { message("电话权限已授权，可直接呼叫");return true; }
        requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, 12);return false;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 10) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) beginCamera();
            else message("相机未授权。若系统提示叠加层，请暂时关闭悬浮球、录屏按钮、护眼覆盖层后重试");
        }
        if (requestCode == 12) message(results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED
            ? "电话权限已授权，现在可让小智直接拨号，或点击立即拨打" : "电话权限未授权；请在应用权限设置中允许电话后重试");
    }

    private Settings read() throws Exception {
        JSONObject value = new JSONObject().put("endpoint", endpoint.getText().toString()).put("completion_url", completionUrl.getText().toString())
            .put("model", model.getText().toString()).put("api_key", apiKey.getText().toString()).put("device_id", deviceId.getText().toString())
            .put("contacts", contacts.getText().toString()).put("detail", detail.isChecked()).put("use_custom", custom.isChecked());
        Settings config = new Settings(value);config.validate();return config;
    }

    private boolean save() {
        if (service == null) { message("应用服务尚未就绪，请稍后重试");return false; }
        try { service.update(read());return true; }
        catch (Exception error) { message(error instanceof IllegalArgumentException ? error.getMessage() : "保存失败，请检查手机密钥存储和可用空间");return false; }
    }

    private void load(Settings config) {
        lastConfig = config;endpoint.setText(config.endpoint);completionUrl.setText(config.completionUrl);model.setText(config.model);apiKey.setText(config.apiKey);
        deviceId.setText(config.deviceId);contacts.setText(config.contacts);detail.setChecked(config.detail);custom.setChecked(config.useCustom);
    }

    private void test(boolean official) {
        if (service == null || testing) return;
        try {
            Settings config = read();testing = true;testOfficial.setEnabled(false);testCustom.setEnabled(false);testResult.setText("正在发送随机图片并核对结果…");
            service.testModel(config, official, (challenge, result) -> {
                testing = false;testOfficial.setEnabled(true);testCustom.setEnabled(true);
                cameraScroll.updateStatus(() -> { if (challenge != null) testImage.setImageBitmap(challenge.bitmap);testResult.setText(result); });
            });
        } catch (Exception error) { message(error.getMessage()); }
    }

    private void render() {
        if (service == null || status == null) return;
        if (service.settings() != lastConfig) settingsScroll.updateStatus(() -> phoneScroll.updateStatus(() -> load(service.settings())));
        String nextStatus = service.status();String nextWebAddress = service.webAddress();String nextDialStatus = service.dialStatus();String nextTouchStatus = service.touchStatus();
        String nextCameraState = service.cameraState();
        String nextSwitchLabel = service.camera.isFrontFacing() ? "切换为后置摄像头" : "切换为前置摄像头";
        if (!TextUtils.equals(cameraState.getText(), nextCameraState) || !TextUtils.equals(switchCamera.getText(), nextSwitchLabel)) cameraScroll.updateStatus(() -> {
            cameraState.setText(nextCameraState);switchCamera.setText(nextSwitchLabel);
        });
        switchCamera.setEnabled(service.canSwitchCamera());
        if (!TextUtils.equals(status.getText(), nextStatus) || !TextUtils.equals(webAddress.getText(), nextWebAddress)) settingsScroll.updateStatus(() -> {
            if (!TextUtils.equals(status.getText(), nextStatus)) status.setText(nextStatus);
            if (!TextUtils.equals(webAddress.getText(), nextWebAddress)) webAddress.setText(nextWebAddress);
        });
        if (!TextUtils.equals(dialStatus.getText(), nextDialStatus)) phoneScroll.updateStatus(() -> dialStatus.setText(nextDialStatus));
        if (!TextUtils.equals(touchStatus.getText(), nextTouchStatus)) touchStatus.setText(nextTouchStatus);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout parent, String content, int size) {
        TextView view = new TextView(this);view.setText(content);view.setTextSize(size);view.setTextColor(Color.rgb(180, 205, 211));view.setPadding(0, dp(5), 0, dp(9));parent.addView(view);return view;
    }
    private void heading(LinearLayout parent, String content, int size) { TextView view = text(parent, content, size);view.setTextColor(Color.WHITE);view.setPadding(0, dp(16), 0, dp(8)); }
    private Button button(LinearLayout parent, String content, View.OnClickListener click) {
        Button view = new Button(this);view.setText(content);view.setTextColor(Color.rgb(13, 40, 36));view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(165, 232, 209)));view.setAllCaps(false);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);layout.topMargin = dp(6);parent.addView(view, layout);view.setOnClickListener(click);return view;
    }
    private EditText field(LinearLayout parent, String title, String hint, boolean secret, boolean multiline) {
        text(parent, title, 14);EditText view = new EditText(this);view.setHint(hint);view.setTextColor(Color.WHITE);view.setHintTextColor(Color.rgb(121, 151, 161));view.setTextSize(15);
        view.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        view.setSingleLine(!multiline);if (multiline) { view.setMinLines(3);view.setGravity(Gravity.TOP); }
        view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);parent.addView(view);return view;
    }
    private void message(String value) { if (feedback != null) feedback.setText(value == null ? "操作失败" : value); }

    @Override public void onSaveInstanceState(Bundle state) {
        state.putInt("tab", selectedTab);state.putInt("settings_scroll", settingsScroll.getScrollY());
        state.putInt("camera_scroll", cameraScroll.getScrollY());state.putInt("phone_scroll", phoneScroll.getScrollY());super.onSaveInstanceState(state);
    }
    @Override public void onStart() { super.onStart();bound = bindService(new Intent(this, CompanionService.class), binding, BIND_AUTO_CREATE);handler.post(refresh); }
    @Override public void onResume() { super.onResume();resumed = true;touchPad.setActive(selectedTab == 0);updatePreview(); }
    @Override public void onPause() { resumed = false;touchPad.setActive(false);if (service != null) service.camera.setPreview(null);super.onPause(); }
    @Override public void onStop() {
        handler.removeCallbacks(refresh);if (service != null) service.setListener(null);
        if (bound) { unbindService(binding);bound = false; }service = null;super.onStop();
    }
}
