package art.aduning.billddesk;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.webrtc.*;
import java.util.HashSet;
import java.util.Set;

public final class MainActivity extends Activity implements DeskEngine.Listener {
    private DeskEngine engine;
    private SavedDevices savedDevices;
    private TextView state, identity, connectionPassword, permissions, viewerState, serverLabel, qualityLabel, receivedQualityInfo;
    private Button sharingButton, fullscreenButton, orientationButton;
    private EditText remoteCode;
    private ScrollView dashboardScroll;
    private ScrollView sideToolbarScroll;
    private HorizontalScrollView bottomToolbarScroll;
    private FrameLayout videoContainer;
    private LinearLayout root, homePanel, dashboard, remotePanel, savedDeviceCard, savedDeviceRows, toolbar, navigationButtons;
    private SurfaceViewRenderer renderer;
    private RemoteViewport remoteViewport;
    private VideoTrack rendered;
    private boolean showPassword, resumed, fullscreen, toolbarOnSide, landscape = true;
    private String loadedServer = "", rememberServer = "", rememberCode = "", rememberPassword = "";
    private final Set<String> dialogs = new HashSet<>();
    private float lastX, lastY;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        engine = ((DeskApplication) getApplication()).engine();
        savedDevices = new SavedDevices(this);
        root = column(); root.setBackgroundColor(Color.rgb(245, 246, 248)); root.setFocusableInTouchMode(true);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left = insets.getSystemWindowInsetLeft(), top = insets.getSystemWindowInsetTop();
            int right = insets.getSystemWindowInsetRight(), bottom = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                DisplayCutout cutout = insets.getDisplayCutout();
                left = Math.max(left, cutout.getSafeInsetLeft()); top = Math.max(top, cutout.getSafeInsetTop());
                right = Math.max(right, cutout.getSafeInsetRight()); bottom = Math.max(bottom, cutout.getSafeInsetBottom());
            }
            view.setPadding(left, top, right, bottom); return insets;
        });
        setContentView(root);
        homePanel = column(); root.addView(homePanel, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView title = text("BilldDesk", 26); title.setTypeface(null, Typeface.BOLD); title.setPadding(dp(22), dp(16), dp(22), dp(4)); homePanel.addView(title);
        state = text("正在连接服务器…", 14); state.setPadding(dp(22), 0, dp(22), dp(12)); homePanel.addView(state);
        dashboardScroll = new ScrollView(this); dashboardScroll.setFillViewport(true);
        dashboard = column(); dashboard.setPadding(dp(16), 0, dp(16), dp(20)); dashboardScroll.addView(dashboard);
        homePanel.addView(dashboardScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout own = card("此设备");
        identity = text("设备代码：连接后生成", 23); identity.setTypeface(Typeface.MONOSPACE, Typeface.BOLD); own.addView(identity);
        connectionPassword = text("连接密码：••••••••", 17); own.addView(connectionPassword);
        LinearLayout identityActions = row(); own.addView(identityActions);
        action(identityActions, "显示密码", () -> { showPassword = !showPassword; update(); });
        action(identityActions, "复制连接信息", () -> {
            if (engine.uuid.isEmpty()) return;
            ClipData clip = ClipData.newPlainText("BilldDesk", "设备代码：" + engine.uuid + "\n连接密码：" + engine.password);
            android.os.PersistableBundle extras = new android.os.PersistableBundle();
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true); clip.getDescription().setExtras(extras);
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(clip);
            toast("已复制，请仅分享给信任的人");
        });
        action(identityActions, "换密码", () -> new AlertDialog.Builder(this).setMessage("更换连接密码会断开当前远程连接。")
                .setPositiveButton("更换", (d, w) -> engine.resetPassword()).setNegativeButton("取消", null).show());

        LinearLayout host = card("允许远程控制此手机");
        host.addView(text("先开启共享，再把设备代码和密码发给对方。每次连接都会要求你确认。", 14));
        permissions = text("", 14); host.addView(permissions);
        action(host, "开启远程触控权限", () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        sharingButton = action(host, "开启屏幕共享", () -> {
            if (engine.sharing) engine.stopSharing();
            else if (!engine.online) toast("请先连接服务器");
            else if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
            else projection();
        });

        LinearLayout controller = card("远程连接其他设备");
        remoteCode = input(controller, "对方设备代码", false);
        action(controller, "连接设备", () -> {
            String target = remoteCode.getText().toString().trim();
            if (target.isEmpty()) { toast("请输入对方设备代码"); return; }
            connectionDialog(target);
        });
        viewerState = text("", 14); controller.addView(viewerState);
        qualityLabel = text("", 13); controller.addView(qualityLabel);
        action(controller, "画质设置", this::qualitySettings);
        savedDeviceCard = card("已保存设备");
        savedDeviceCard.addView(text("点连接即可重连；电脑更换密码后点修改。", 13));
        savedDeviceRows = column(); savedDeviceCard.addView(savedDeviceRows);
        dashboard.removeView(savedDeviceCard); dashboard.addView(savedDeviceCard, 0);

        LinearLayout settings = card("服务器"); serverLabel = text(engine.config.server, 14); settings.addView(serverLabel);
        LinearLayout controls = row(); settings.addView(controls);
        action(controls, "私有服务器设置", this::serverSettings);
        action(controls, "重新连接", engine::connect);
        settings.addView(text("v" + BuildConfig.VERSION_NAME + " · Android 原生客户端", 12));
        remotePanel = column(); remotePanel.setVisibility(View.GONE); root.addView(remotePanel, new LinearLayout.LayoutParams(-1, 0, 2));
        renderer = new SurfaceViewRenderer(this);
        renderer.init(engine.egl.getEglBaseContext(), null);
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        videoContainer = new FrameLayout(this); videoContainer.setBackgroundColor(Color.BLACK);
        remotePanel.addView(videoContainer, new LinearLayout.LayoutParams(-1, 0, 1));
        videoContainer.addView(renderer, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        TextView zoomLabel = text("100% · 双指缩放/移动", 12);
        zoomLabel.setTextColor(Color.WHITE); zoomLabel.setBackgroundColor(0x99000000);
        zoomLabel.setPadding(dp(6), dp(3), dp(6), dp(3));
        videoContainer.addView(zoomLabel, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        remoteViewport = new RemoteViewport(videoContainer, renderer, zoomLabel, new RemoteViewport.RemoteInput() {
            @Override public int frameWidth() { return engine.frameWidth; }
            @Override public int frameHeight() { return engine.frameHeight; }
            @Override public void send(int type, float x, float y) {
                lastX = x; lastY = y; engine.behavior(type, x, y, 0, null);
            }
        });
        bottomToolbarScroll = new HorizontalScrollView(this); bottomToolbarScroll.setHorizontalScrollBarEnabled(false);
        toolbar = row(); bottomToolbarScroll.addView(toolbar); remotePanel.addView(bottomToolbarScroll);
        sideToolbarScroll = new ScrollView(this); sideToolbarScroll.setFillViewport(true);
        sideToolbarScroll.setVerticalScrollBarEnabled(false); sideToolbarScroll.setVisibility(View.GONE);
        remotePanel.addView(sideToolbarScroll, new LinearLayout.LayoutParams(dp(72), -1));
        fullscreenButton = action(toolbar, "全屏", () -> setFullscreen(!fullscreen));
        orientationButton = action(toolbar, "竖屏", () -> { landscape = !landscape; applyDisplayMode(); });
        action(toolbar, "画质", this::qualitySettings);
        Button resetView = action(toolbar, "复位", remoteViewport::reset);
        resetView.setContentDescription("还原完整画面");
        action(toolbar, "文字", this::remoteText);
        action(toolbar, "断开", engine::stopViewing);
        navigationButtons = row(); toolbar.addView(navigationButtons);
        action(navigationButtons, "返回", () -> engine.navigation("back"));
        action(navigationButtons, "主页", () -> engine.navigation("home"));
        action(navigationButtons, "任务", () -> engine.navigation("recents"));
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View item = toolbar.getChildAt(i);
            if (item instanceof Button) item.setLayoutParams(new LinearLayout.LayoutParams(dp(72), dp(48)));
        }
        navigationButtons.setLayoutParams(new LinearLayout.LayoutParams(dp(216), -2));
        for (int i = 0; i < navigationButtons.getChildCount(); i++)
            navigationButtons.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(dp(72), dp(48)));
        update(); engine.connect();
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true; engine.listener = this; update();
        onViewerTrack(engine.viewerTrack); engine.showPendingRequests();
    }
    @Override protected void onPause() {
        remoteViewport.cancel(); resumed = false;
        if (engine.listener == this) engine.listener = null; super.onPause();
    }
    @Override protected void onDestroy() {
        if (engine.listener == this) engine.listener = null;
        if (rendered != null) try { rendered.removeSink(renderer); } catch (IllegalStateException ignored) {}
        renderer.release(); super.onDestroy();
    }
    @Override public void onState() { update(); }
    private void update() {
        state.setText(engine.status);
        identity.setText("设备代码：" + (engine.uuid.isEmpty() ? "连接中…" : engine.uuid));
        connectionPassword.setText("连接密码：" + (showPassword ? engine.password : "••••••••"));
        if (showPassword) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        permissions.setText("远程触控：" + (DeskAccessibilityService.current == null ? "未开启（仍可观看屏幕）" : "已开启"));
        sharingButton.setText(engine.sharing ? "停止屏幕共享" : "开启屏幕共享");
        viewerState.setText(engine.viewerStatus);
        qualityLabel.setText("目标画面：" + engine.viewerQuality.label());
        if (receivedQualityInfo != null) receivedQualityInfo.setText(engine.receivedPicture());
        serverLabel.setText(engine.config.server);
        navigationButtons.setVisibility(engine.viewingAndroid() ? View.VISIBLE : View.GONE);
        if (!loadedServer.equals(engine.config.server)) {
            loadedServer = engine.config.server; remoteCode.setText(savedDevices.lastCode(loadedServer)); renderSavedDevices();
        }
        if (engine.viewing.isEmpty() && rendered == null) remotePanel.setVisibility(View.GONE);
    }
    @Override public void onRequest(String sender, String device) {
        if (!resumed || !dialogs.add(sender)) return;
        new AlertDialog.Builder(this).setTitle("允许本次远程连接？")
                .setMessage("设备 " + device + " 希望查看并操作此手机。你可以随时停止屏幕共享。")
                .setPositiveButton("允许", (d, w) -> { dialogs.remove(sender); engine.answerRequest(sender, true); })
                .setNegativeButton("拒绝", (d, w) -> { dialogs.remove(sender); engine.answerRequest(sender, false); })
                .setOnCancelListener(d -> { dialogs.remove(sender); engine.answerRequest(sender, false); }).show();
    }
    @Override public void onViewerTrack(VideoTrack track) {
        if (rendered == track) return;
        remoteViewport.reset();
        if (rendered != null) try { rendered.removeSink(renderer); } catch (IllegalStateException ignored) {}
        rendered = track;
        if (track != null) {
            track.addSink(renderer); remotePanel.setVisibility(View.VISIBLE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (!rememberCode.isEmpty() && rememberCode.equals(engine.viewing) && rememberServer.equals(engine.config.server)) {
                try { savedDevices.save(rememberServer, rememberCode, rememberPassword); renderSavedDevices(); }
                catch (Exception ignored) { toast("连接成功，但保存密码失败，请下次重新输入"); }
                rememberCode = ""; rememberPassword = ""; rememberServer = "";
            }
            setFullscreen(true);
        } else {
            renderer.clearImage(); remotePanel.setVisibility(View.GONE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); setFullscreen(false);
        }
    }
    private void setFullscreen(boolean enabled) {
        fullscreen = enabled && rendered != null;
        homePanel.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        fullscreenButton.setText(fullscreen ? "窗口" : "全屏");
        orientationButton.setVisibility(fullscreen ? View.VISIBLE : View.GONE);
        if (fullscreen) { hideKeyboard(); root.post(this::hideKeyboard); }
        else dashboardScroll.post(() -> dashboardScroll.scrollTo(0, 0));
        applyDisplayMode();
    }
    private void hideKeyboard() {
        remoteCode.clearFocus(); root.requestFocus();
        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(root.getWindowToken(), 0);
        if (Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null)
            getWindow().getInsetsController().hide(WindowInsets.Type.ime());
    }
    private void applyDisplayMode() {
        setRequestedOrientation(fullscreen ? (landscape ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT) : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        orientationButton.setText(landscape ? "竖屏" : "横屏");
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if (fullscreen) controller.hide(WindowInsets.Type.systemBars());
                else controller.show(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(fullscreen ? View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION : View.SYSTEM_UI_FLAG_VISIBLE);
        }
        root.requestApplyInsets();
        layoutRemoteControls();
    }
    private void layoutRemoteControls() {
        boolean side = fullscreen && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (side == toolbarOnSide) return;
        toolbarOnSide = side;
        ((ViewGroup) toolbar.getParent()).removeView(toolbar);
        remotePanel.setOrientation(side ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        videoContainer.setLayoutParams(side ? new LinearLayout.LayoutParams(0, -1, 1)
                : new LinearLayout.LayoutParams(-1, 0, 1));
        bottomToolbarScroll.setVisibility(side ? View.GONE : View.VISIBLE);
        sideToolbarScroll.setVisibility(side ? View.VISIBLE : View.GONE);
        toolbar.setOrientation(side ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        toolbar.setGravity(side ? Gravity.CENTER_VERTICAL : Gravity.NO_GRAVITY);
        navigationButtons.setOrientation(side ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        navigationButtons.setLayoutParams(new LinearLayout.LayoutParams(side ? -1 : dp(216), -2));
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View item = toolbar.getChildAt(i);
            if (item instanceof Button) item.setLayoutParams(new LinearLayout.LayoutParams(side ? -1 : dp(72), dp(48)));
        }
        for (int i = 0; i < navigationButtons.getChildCount(); i++)
            navigationButtons.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(side ? -1 : dp(72), dp(48)));
        if (side) sideToolbarScroll.addView(toolbar, new ScrollView.LayoutParams(-1, -2));
        else bottomToolbarScroll.addView(toolbar, new HorizontalScrollView.LayoutParams(-2, -2));
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus); if (hasFocus && fullscreen) applyDisplayMode();
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration); layoutRemoteControls(); if (fullscreen) root.requestApplyInsets();
    }
    @Override public void onBackPressed() {
        if (fullscreen) setFullscreen(false); else super.onBackPressed();
    }
    private String savedPassword(String target) {
        try { return savedDevices.password(engine.config.server, target); }
        catch (Exception ignored) { toast("已保存的密码无法读取，请重新输入"); return ""; }
    }
    private void connectionDialog(String target) {
        LinearLayout form = column(); form.setPadding(dp(20), 0, dp(20), 0);
        EditText password = input(form, "对方连接密码", true); password.setText(savedPassword(target));
        CheckBox remember = new CheckBox(this); remember.setText("记住设备号和密码（仅保存在本机）"); remember.setChecked(true); form.addView(remember);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("连接设备 " + target).setView(form)
                .setPositiveButton("连接", null).setNegativeButton("取消", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = password.getText().toString();
            if (value.isEmpty()) { password.setError("请输入连接密码"); return; }
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(password.getWindowToken(), 0);
            if (!remember.isChecked()) { savedDevices.forget(engine.config.server, target); renderSavedDevices(); }
            startConnection(target, value, remember.isChecked()); dialog.dismiss();
        }));
        dialog.setOnDismissListener(d -> hideKeyboard());
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); dialog.show();
    }
    private void startConnection(String target, String password, boolean remember) {
        remoteCode.setText(target);
        rememberServer = remember ? engine.config.server : "";
        rememberCode = remember ? target : ""; rememberPassword = remember ? password : "";
        engine.view(target, password);
    }
    private void renderSavedDevices() {
        savedDeviceRows.removeAllViews();
        java.util.List<String> codes = savedDevices.codes(engine.config.server);
        savedDeviceCard.setVisibility(codes.isEmpty() ? View.GONE : View.VISIBLE);
        for (String code : codes) {
            TextView label = text(code, 20); label.setTypeface(Typeface.MONOSPACE); savedDeviceRows.addView(label);
            LinearLayout actions = row(); savedDeviceRows.addView(actions);
            Button connect = action(actions, "连接", () -> {
                String password = savedPassword(code);
                if (password.isEmpty()) connectionDialog(code); else startConnection(code, password, true);
            }); connect.setContentDescription("连接已保存设备 " + code);
            Button edit = action(actions, "修改", () -> connectionDialog(code)); edit.setContentDescription("修改已保存设备 " + code);
            Button forget = action(actions, "忘记", () -> {
                savedDevices.forget(engine.config.server, code); renderSavedDevices();
                if (code.equals(remoteCode.getText().toString())) remoteCode.setText(savedDevices.lastCode(engine.config.server));
            }); forget.setContentDescription("忘记已保存设备 " + code);
        }
    }
    private void projection() {
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        Intent request = Build.VERSION.SDK_INT >= 34
                ? manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                : manager.createScreenCaptureIntent();
        startActivityForResult(request, 10);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 11) projection();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 10 && result == RESULT_OK && data != null)
            startForegroundService(new Intent(this, CaptureService.class).putExtra("consent", data));
        else if (request == 10) toast("未开启屏幕共享");
    }
    private void remoteText() {
        EditText text = new EditText(this); text.setHint("输入发送给远程设备的文字");
        text.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("远程文字输入").setView(text)
                .setPositiveButton("发送", (d, w) -> {
                    ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(text.getWindowToken(), 0);
                    engine.behavior(14, lastX, lastY, 0, new JSONArray().put(text.getText().toString()));
                })
                .setNeutralButton("删除", (d, w) -> {
                    engine.behavior(15, 0, 0, 0, new JSONArray().put(41));
                    engine.behavior(16, 0, 0, 0, new JSONArray().put(41));
                })
                .setNegativeButton("取消", null).create();
        dialog.setOnDismissListener(d -> hideKeyboard()); dialog.show();
    }
    private void qualitySettings() {
        ViewerQuality quality = engine.viewerQuality;
        LinearLayout form = column(); form.setPadding(dp(20), 0, dp(20), 0);
        Spinner resolution = qualityChoice(form, "分辨率", ViewerQuality.HEIGHTS, quality.height, "P", 1);
        Spinner fps = qualityChoice(form, "帧率", ViewerQuality.FRAME_RATES, quality.fps, " 帧/秒", 1);
        Spinner bitrate = qualityChoice(form, "码率上限", ViewerQuality.BITRATES_KBPS, quality.bitrateKbps, " Mbps", 1000);
        receivedQualityInfo = text(engine.receivedPicture(), 13); form.addView(receivedQualityInfo);
        form.addView(text("高分辨率和码率有助于文字清晰，60 帧提升流畅度。网络或电脑性能不足时可降低帧率。实际画面受电脑屏幕和编码器限制。", 13));
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("画质设置").setView(scroll)
                .setPositiveButton("应用", (d, w) -> engine.setViewerQuality(new ViewerQuality(
                        ViewerQuality.HEIGHTS[resolution.getSelectedItemPosition()],
                        ViewerQuality.FRAME_RATES[fps.getSelectedItemPosition()],
                        ViewerQuality.BITRATES_KBPS[bitrate.getSelectedItemPosition()])))
                .setNegativeButton("取消", null).create();
        dialog.setOnDismissListener(d -> receivedQualityInfo = null); dialog.show();
    }
    private Spinner qualityChoice(LinearLayout form, String label, int[] options, int current, String suffix, int divisor) {
        LinearLayout line = row(); line.setGravity(Gravity.CENTER_VERTICAL); form.addView(line);
        TextView name = text(label, 15); line.addView(name, new LinearLayout.LayoutParams(dp(90), -2));
        Spinner spinner = new Spinner(this); spinner.setContentDescription(label);
        String[] labels = new String[options.length]; int selected = 0;
        for (int i = 0; i < options.length; i++) {
            labels[i] = (options[i] / divisor) + suffix;
            if (options[i] == current) selected = i;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter); spinner.setSelection(selected);
        line.addView(spinner, new LinearLayout.LayoutParams(0, dp(48), 1)); return spinner;
    }
    private void serverSettings() {
        if (engine.sharing) { toast("请先停止共享，再修改服务器"); return; }
        LinearLayout form = column(); form.setPadding(dp(20), 0, dp(20), 0);
        EditText url = input(form, "HTTPS 服务器", false); url.setText(engine.config.server);
        EditText turn = input(form, "TURN 中继地址", false); turn.setText(engine.config.turn);
        EditText user = input(form, "中继用户名", false); user.setText(engine.config.user);
        EditText password = input(form, "中继密码", true); password.setText(engine.config.password);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("私有服务器设置").setView(form)
                .setPositiveButton("保存并连接", null).setNegativeButton("取消", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                engine.setConfig(new ServerConfig(url.getText().toString(), turn.getText().toString(), user.getText().toString(), password.getText().toString()));
                dialog.dismiss();
            } catch (IllegalArgumentException e) { toast(e.getMessage()); }
        })); dialog.show();
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private TextView text(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextColor(Color.rgb(35, 40, 48)); t.setTextSize(size); t.setPadding(0, dp(5), 0, dp(5)); return t;
    }
    private LinearLayout card(String title) {
        LinearLayout box = column(); box.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(16)); box.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(14); dashboard.addView(box, params);
        TextView heading = text(title, 17); heading.setTypeface(null, Typeface.BOLD); box.addView(heading); return box;
    }
    private Button action(LinearLayout box, String title, Runnable callback) {
        Button b = new Button(this); b.setText(title); b.setTextSize(13); b.setAllCaps(false); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setOnClickListener(v -> callback.run());
        box.addView(b, box.getOrientation() == LinearLayout.HORIZONTAL ? new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-1, -2)); return b;
    }
    private EditText input(LinearLayout box, String hint, boolean password) {
        EditText input = new EditText(this); input.setHint(hint); input.setSingleLine(true); input.setTextSize(16);
        input.setInputType(InputType.TYPE_CLASS_TEXT | (password ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        box.addView(input, new LinearLayout.LayoutParams(-1, -2)); return input;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
