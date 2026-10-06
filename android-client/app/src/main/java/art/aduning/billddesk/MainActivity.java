package art.aduning.billddesk;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
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
    private static final int PRIMARY = 0xFF8A5A00, ON_PRIMARY = Color.WHITE;
    private static final int SURFACE = Color.WHITE, BACKGROUND = 0xFFF5F6F8;
    private static final int ON_SURFACE = 0xFF232830, SECONDARY = 0xFF59616D;
    private static final int PRIMARY_CONTAINER = 0xFFF5EDD9, ERROR = 0xFFB42318;
    private DeskEngine engine;
    private SavedDevices savedDevices;
    private TextView state, identity, connectionPassword, permissions, viewerState, receivedQualityInfo;
    private Button sharingButton, passwordButton, permissionButton, fullscreenButton, orientationButton;
    private EditText remoteCode;
    private ScrollView dashboardScroll;
    private ScrollView sideToolbarScroll;
    private HorizontalScrollView bottomToolbarScroll;
    private FrameLayout videoContainer;
    private LinearLayout root, homePanel, dashboard, connectionColumn, shareColumn, remotePanel, savedDeviceCard, savedDeviceRows, toolbar, navigationButtons;
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
        root = column(); root.setBackgroundColor(BACKGROUND); root.setFocusableInTouchMode(true);
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
        LinearLayout header = row(); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(12), dp(20), dp(4)); homePanel.addView(header);
        TextView title = text("BilldDesk", 24); title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button settingsButton = action(header, "设置", this::settingsOverview);
        settingsButton.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(48)));
        state = text("正在连接服务器…", 13); state.setTextColor(SECONDARY);
        state.setPadding(dp(20), 0, dp(20), dp(16)); homePanel.addView(state);
        dashboardScroll = new ScrollView(this); dashboardScroll.setFillViewport(true);
        dashboard = column(); dashboard.setPadding(dp(16), 0, dp(16), dp(20)); dashboardScroll.addView(dashboard);
        connectionColumn = column(); shareColumn = column();
        dashboard.addView(connectionColumn); dashboard.addView(shareColumn); layoutHomeDashboard();
        homePanel.addView(dashboardScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout controller = card("连接设备", connectionColumn);
        remoteCode = input(controller, "对方设备代码", false);
        controller.removeView(remoteCode);
        LinearLayout connectRow = row(); connectRow.setGravity(Gravity.CENTER_VERTICAL); controller.addView(connectRow);
        connectRow.addView(remoteCode, new LinearLayout.LayoutParams(0, dp(52), 1));
        remoteCode.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        Runnable connectDevice = () -> {
            String target = remoteCode.getText().toString().trim();
            if (target.isEmpty()) { remoteCode.setError("请输入对方设备代码"); return; }
            connectionDialog(target);
        };
        Button connectButton = action(connectRow, "连接", connectDevice); primary(connectButton);
        LinearLayout.LayoutParams connectLayout = new LinearLayout.LayoutParams(dp(80), dp(52));
        connectLayout.setMarginStart(dp(8)); connectButton.setLayoutParams(connectLayout);
        remoteCode.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != android.view.inputmethod.EditorInfo.IME_ACTION_GO) return false;
            connectDevice.run(); return true;
        });
        viewerState = text("", 13); viewerState.setTextColor(SECONDARY); controller.addView(viewerState);
        savedDeviceCard = card("常用设备", connectionColumn);
        savedDeviceRows = column(); savedDeviceCard.addView(savedDeviceRows);

        LinearLayout own = card("共享此手机", shareColumn);
        TextView codeLabel = text("此设备代码", 13); codeLabel.setTextColor(SECONDARY); own.addView(codeLabel);
        identity = text("连接中…", 24); identity.setTypeface(null, Typeface.BOLD); own.addView(identity);
        LinearLayout passwordRow = row(); passwordRow.setGravity(Gravity.CENTER_VERTICAL); own.addView(passwordRow);
        connectionPassword = text("连接密码：••••••••", 14);
        passwordRow.addView(connectionPassword, new LinearLayout.LayoutParams(0, -2, 1));
        passwordButton = action(passwordRow, "显示", () -> { showPassword = !showPassword; update(); });
        passwordButton.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(48)));
        LinearLayout identityActions = row(); own.addView(identityActions);
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

        TextView sharingHint = text("开启共享后，对方每次连接都需要你确认。", 13);
        sharingHint.setTextColor(SECONDARY); own.addView(sharingHint);
        permissions = text("", 13); permissions.setTextColor(SECONDARY); own.addView(permissions);
        permissionButton = action(own, "开启远程触控权限", () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        sharingButton = action(own, "开启屏幕共享", () -> {
            if (engine.sharing) engine.stopSharing();
            else if (!engine.online) toast("请先连接服务器");
            else if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
            else projection();
        });

        remotePanel = column(); remotePanel.setVisibility(View.GONE); root.addView(remotePanel, new LinearLayout.LayoutParams(-1, 0, 2));
        renderer = new SurfaceViewRenderer(this);
        renderer.init(engine.egl.getEglBaseContext(), null);
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        videoContainer = new FrameLayout(this); videoContainer.setBackgroundColor(Color.BLACK);
        remotePanel.addView(videoContainer, new LinearLayout.LayoutParams(-1, 0, 1));
        videoContainer.addView(renderer, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        TextView zoomLabel = text("100% · 单指移鼠标/双指缩放", 12);
        zoomLabel.setTextColor(Color.WHITE); zoomLabel.setBackgroundColor(0x99000000);
        zoomLabel.setPadding(dp(6), dp(3), dp(6), dp(3));
        FrameLayout.LayoutParams zoomLabelLayout = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        zoomLabelLayout.setMarginEnd(dp(8));
        videoContainer.addView(zoomLabel, zoomLabelLayout);
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
        identity.setText(engine.uuid.isEmpty() ? "连接中…" : engine.uuid);
        connectionPassword.setText("连接密码：" + (showPassword ? engine.password : "••••••••"));
        passwordButton.setText(showPassword ? "隐藏" : "显示");
        passwordButton.setContentDescription(showPassword ? "隐藏连接密码" : "显示连接密码");
        if (showPassword) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        permissions.setText("远程触控：" + (DeskAccessibilityService.current == null ? "未开启（仍可观看屏幕）" : "已开启"));
        permissionButton.setVisibility(DeskAccessibilityService.current == null ? View.VISIBLE : View.GONE);
        sharingButton.setText(engine.sharing ? "停止屏幕共享" : "开启屏幕共享");
        if (sharingButton.isActivated() != engine.sharing) {
            sharingButton.setActivated(engine.sharing);
            buttonStyle(sharingButton, engine.sharing ? ERROR : PRIMARY_CONTAINER, engine.sharing ? ON_PRIMARY : PRIMARY);
        }
        viewerState.setText(engine.viewerStatus);
        viewerState.setVisibility(engine.viewerStatus.isEmpty() ? View.GONE : View.VISIBLE);
        if (receivedQualityInfo != null) receivedQualityInfo.setText(engine.receivedPicture());
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
        super.onConfigurationChanged(configuration); layoutHomeDashboard(); layoutRemoteControls(); if (fullscreen) root.requestApplyInsets();
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
            LinearLayout device = row(); device.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams deviceLayout = new LinearLayout.LayoutParams(-1, -2);
            deviceLayout.topMargin = dp(8); savedDeviceRows.addView(device, deviceLayout);
            TextView label = text(code, 17);
            device.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout actions = row(); device.addView(actions);
            Button connect = action(actions, "连接", () -> {
                String password = savedPassword(code);
                if (password.isEmpty()) connectionDialog(code); else startConnection(code, password, true);
            }); connect.setContentDescription("连接已保存设备 " + code);
            connect.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
            Button more = action(actions, "更多", () -> {});
            LinearLayout.LayoutParams moreLayout = new LinearLayout.LayoutParams(-2, -2);
            moreLayout.setMarginStart(dp(8)); more.setLayoutParams(moreLayout);
            more.setContentDescription("管理已保存设备 " + code);
            more.setOnClickListener(view -> {
                PopupMenu menu = new PopupMenu(this, view);
                menu.getMenu().add(0, 1, 0, "修改密码"); menu.getMenu().add(0, 2, 1, "忘记设备");
                menu.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == 1) connectionDialog(code);
                    else {
                        savedDevices.forget(engine.config.server, code); renderSavedDevices();
                        if (code.equals(remoteCode.getText().toString())) remoteCode.setText(savedDevices.lastCode(engine.config.server));
                    }
                    return true;
                }); menu.show();
            });
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
        text.setSingleLine(true);
        text.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
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
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        text.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId != android.view.inputmethod.EditorInfo.IME_ACTION_SEND && !enter) return false;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); return true;
        });
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
    private void settingsOverview() {
        LinearLayout form = column(); form.setPadding(dp(20), dp(8), dp(20), dp(16));
        TextView quality = text("默认画质", 17); quality.setTypeface(null, Typeface.BOLD); form.addView(quality);
        TextView qualitySummary = text(engine.viewerQuality.label(), 14); qualitySummary.setTextColor(SECONDARY); form.addView(qualitySummary);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("设置").setView(scroll).setPositiveButton("完成", null).create();
        action(form, "画质设置", () -> { dialog.dismiss(); qualitySettings(); });
        TextView server = text("私有服务器", 17); server.setTypeface(null, Typeface.BOLD); server.setPadding(0, dp(24), 0, dp(4)); form.addView(server);
        TextView address = text(engine.config.server, 14); address.setTextColor(SECONDARY); form.addView(address);
        action(form, "服务器设置", () -> { dialog.dismiss(); serverSettings(); });
        action(form, "重新连接服务器", () -> { dialog.dismiss(); engine.connect(); });
        TextView version = text("BilldDesk v" + BuildConfig.VERSION_NAME, 13); version.setTextColor(SECONDARY);
        version.setPadding(0, dp(24), 0, 0); form.addView(version); dialog.show();
    }
    private void serverSettings() {
        if (engine.sharing) { toast("请先停止共享，再修改服务器"); return; }
        LinearLayout form = column(); form.setPadding(dp(20), 0, dp(20), 0);
        EditText url = input(form, "HTTPS 服务器", false); url.setText(engine.config.server);
        EditText turn = input(form, "TURN 中继地址", false); turn.setText(engine.config.turn);
        EditText user = input(form, "中继用户名", false); user.setText(engine.config.user);
        EditText password = input(form, "中继密码", true); password.setText(engine.config.password);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("私有服务器设置").setView(scroll)
                .setPositiveButton("保存并连接", null).setNegativeButton("取消", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                engine.setConfig(new ServerConfig(url.getText().toString(), turn.getText().toString(), user.getText().toString(), password.getText().toString()));
                dialog.dismiss();
            } catch (IllegalArgumentException e) { toast(e.getMessage()); }
        })); dialog.show();
    }
    private void layoutHomeDashboard() {
        boolean wide = getResources().getConfiguration().screenWidthDp >= 600;
        dashboard.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        connectionColumn.setLayoutParams(wide ? new LinearLayout.LayoutParams(0, -2, 1)
                : new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams shareLayout = wide ? new LinearLayout.LayoutParams(0, -2, 1)
                : new LinearLayout.LayoutParams(-1, -2);
        shareLayout.setMarginStart(wide ? dp(12) : 0); shareColumn.setLayoutParams(shareLayout);
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private TextView text(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextColor(ON_SURFACE); t.setTextSize(size); t.setPadding(0, dp(4), 0, dp(4)); return t;
    }
    private LinearLayout card(String title, LinearLayout parent) {
        LinearLayout box = column(); box.setPadding(dp(16), dp(12), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(SURFACE); bg.setCornerRadius(dp(16)); box.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(12); parent.addView(box, params);
        TextView heading = text(title, 17); heading.setTypeface(null, Typeface.BOLD); box.addView(heading); return box;
    }
    private Button action(LinearLayout box, String title, Runnable callback) {
        Button b = new Button(this); b.setText(title); b.setTextSize(14); b.setAllCaps(false); b.setMinWidth(dp(48)); b.setMinimumWidth(dp(48));
        b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setPadding(dp(12), dp(8), dp(12), dp(8));
        buttonStyle(b, PRIMARY_CONTAINER, PRIMARY);
        b.setOnClickListener(v -> callback.run());
        LinearLayout.LayoutParams params = box.getOrientation() == LinearLayout.HORIZONTAL ? new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-1, -2);
        if (box.getOrientation() == LinearLayout.HORIZONTAL) {
            if (box.getChildCount() > 0) params.setMarginStart(dp(8));
        } else params.topMargin = dp(8);
        box.addView(b, params); return b;
    }
    private void primary(Button button) { buttonStyle(button, PRIMARY, ON_PRIMARY); }
    private void buttonStyle(Button button, int background, int foreground) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(background); shape.setCornerRadius(dp(10));
        button.setBackgroundTintList(null); button.setTextColor(foreground);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), shape, null));
        button.setStateListAnimator(null);
    }
    private EditText input(LinearLayout box, String hint, boolean password) {
        TextView label = text(hint, 13); label.setTextColor(SECONDARY); box.addView(label);
        EditText input = new EditText(this); input.setHint(hint); input.setSingleLine(true); input.setTextSize(16);
        input.setId(View.generateViewId()); label.setLabelFor(input.getId());
        input.setTextColor(ON_SURFACE); input.setHintTextColor(SECONDARY); input.setMinHeight(dp(52));
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable background = new GradientDrawable(); background.setColor(BACKGROUND);
        background.setCornerRadius(dp(10)); background.setStroke(dp(1), 0xFFD3D7DE);
        GradientDrawable focused = new GradientDrawable(); focused.setColor(BACKGROUND);
        focused.setCornerRadius(dp(10)); focused.setStroke(dp(2), PRIMARY);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused); states.addState(new int[]{}, background);
        input.setBackground(states);
        input.setInputType(InputType.TYPE_CLASS_TEXT | (password ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        box.addView(input, new LinearLayout.LayoutParams(-1, -2)); return input;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
