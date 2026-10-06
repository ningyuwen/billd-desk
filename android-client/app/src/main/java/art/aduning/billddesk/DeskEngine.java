package art.aduning.billddesk;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.*;
import io.socket.client.IO;
import io.socket.client.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;

/** Native counterpart of src/hooks/use-websocket.ts and hooks/webrtc/remoteDesk.ts. */
final class DeskEngine {
    interface Listener {
        void onState();
        void onRequest(String sender, String device);
        void onViewerTrack(VideoTrack track);
    }
    final Context context;
    final EglBase egl = EglBase.create();
    final PeerConnectionFactory factory;
    final Handler main = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final OkHttpClient http = new OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build();
    private final Map<String, Peer> peers = new HashMap<>();
    private final Map<String, JSONObject> requests = new HashMap<>();
    private final Map<String, List<IceCandidate>> earlyIce = new HashMap<>();
    private Socket socket;
    private ScreenCapturerAndroid capturer;
    private SurfaceTextureHelper texture;
    private VideoSource screenSource;
    private VideoTrack screenTrack;
    private String viewerTarget = "", viewerPassword = "", viewerSender = "";
    private int screenWidth = 0, screenHeight = 0, limitSize = 1080, captureFps = 20;
    private boolean connecting = false;
    volatile ServerConfig config;
    volatile Listener listener;
    volatile String uuid = "", password = "", status = "正在连接服务器…";
    volatile boolean sharing, online;
    volatile VideoTrack viewerTrack;
    volatile String viewerStatus = "", viewing = "";
    volatile int hostConnections, frameWidth, frameHeight;
    volatile ViewerQuality viewerQuality;
    volatile int receivedFps;
    private volatile boolean viewerIsAndroid;
    boolean viewingAndroid() { return viewerIsAndroid; }

    DeskEngine(Context context) {
        this.context = context;
        config = ServerConfig.load(context);
        viewerQuality = ViewerQuality.load(context);
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions());
        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext())).createPeerConnectionFactory();
        worker.scheduleWithFixedDelay(() -> {
            if (socket != null && socket.connected() && !uuid.isEmpty()) {
                emit("billdDeskUpdateUser", json("deskUserUuid", uuid, "deskUserPassword", password));
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    static JSONObject json(Object... values) {
        JSONObject result = new JSONObject();
        try { for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]); }
        catch (Exception e) { throw new IllegalArgumentException(e); }
        return result;
    }
    void changed() {
        main.post(() -> { Listener l = listener; if (l != null) l.onState(); });
    }
    private void error(String operation, Exception e) {
        // Never log signaling payloads, SDP, device passwords, or TURN credentials.
        Log.w("BilldDesk", operation + ": " + e.getClass().getSimpleName());
        for (int i = 0; i < Math.min(8, e.getStackTrace().length); i++)
            Log.w("BilldDesk", "at " + e.getStackTrace()[i]);
        String reason = e instanceof java.net.UnknownHostException ? "域名解析失败"
                : e instanceof javax.net.ssl.SSLException ? "HTTPS 证书验证失败"
                : e instanceof java.io.InterruptedIOException ? "连接超时"
                : e instanceof java.io.IOException ? "网络不可达"
                : e instanceof IllegalStateException && e.getMessage() != null && e.getMessage().startsWith("API ")
                ? e.getMessage() : e.getClass().getSimpleName();
        status = operation + "失败（" + reason + "），可点重新连接"; changed();
    }
    private JSONObject api(String method, String path, JSONObject data) throws Exception {
        Request.Builder request = new Request.Builder().url(config.server + "/api" + path);
        if (!method.equals("GET")) request.method(method,
                RequestBody.create((data == null ? "{}" : data.toString()), MediaType.get("application/json; charset=utf-8")));
        try (Response response = http.newCall(request.build()).execute()) {
            if (response.body() == null) throw new IllegalStateException("Empty response");
            JSONObject result = new JSONObject(response.body().string());
            if (!response.isSuccessful() || result.optInt("code") != 200) throw new IllegalStateException("API " + response.code());
            return result;
        }
    }
    private SharedPreferences identityPreferences() {
        String name = android.util.Base64.encodeToString(config.server.getBytes(StandardCharsets.UTF_8),
                android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
        return context.getSharedPreferences("identity_" + name, Context.MODE_PRIVATE);
    }
    void connect() { worker.execute(this::connectNow); }
    private void connectNow() {
        if (connecting || online) return;
        connecting = true; status = "正在连接服务器…"; changed();
        try {
            SharedPreferences p = identityPreferences();
            uuid = p.getString("uuid", ""); password = p.getString("password", "");
            if (uuid.isEmpty() || password.isEmpty()) {
                JSONObject account = api("POST", "/desk_user/create", null).getJSONObject("data");
                uuid = account.getString("uuid"); password = account.getString("password");
                p.edit().putString("uuid", uuid).putString("password", password).apply();
            } else {
                api("POST", "/desk_user/login", json("uuid", uuid, "password", password));
            }
            if (socket != null) { socket.off(); socket.disconnect(); }
            IO.Options options = IO.Options.builder().setTransports(new String[]{"websocket"})
                    .setReconnection(true).setTimeout(15000).build();
            socket = IO.socket(config.server, options);
            socket.on(Socket.EVENT_CONNECT, args -> worker.execute(() -> {
                online = true;
                emit("billdDeskJoin", json("deskUserUuid", uuid, "deskUserPassword", password, "live_room_id", uuid));
                status = "已连接私有服务器"; changed();
            }));
            socket.on(Socket.EVENT_DISCONNECT, args -> worker.execute(() -> {
                online = false; closePeers(); status = "连接断开，正在重连…"; changed();
            }));
            socket.on(Socket.EVENT_CONNECT_ERROR, args -> worker.execute(() -> {
                online = false; status = "服务器连接失败，正在重试…"; changed();
            }));
            listen("billdDeskStartRemoteResult", this::remoteResult);
            listen("nativeWebRtcOffer", this::offerReceived);
            listen("nativeWebRtcAnswer", data -> {
                Peer peer = peers.get(data.optString("sender"));
                if (peer != null && peer.host && addressed(data)) peer.remote(data.getJSONObject("sdp"), null);
            });
            listen("nativeWebRtcCandidate", data -> {
                Peer peer = peers.get(data.optString("sender"));
                String sender = data.optString("sender");
                if (addressed(data) && (peer != null || (!viewerSender.isEmpty() && sender.equals(viewerSender)))) {
                    JSONObject c = data.getJSONObject("candidate");
                    if (!c.optString("candidate").isEmpty()) {
                        IceCandidate ice = new IceCandidate(c.optString("sdpMid"), c.optInt("sdpMLineIndex"), c.getString("candidate"));
                        if (peer != null) peer.candidate(ice);
                        else {
                            List<IceCandidate> queued = earlyIce.computeIfAbsent(sender, key -> new ArrayList<>());
                            if (queued.size() < 128) queued.add(ice);
                        }
                    }
                }
            });
            socket.connect();
        } catch (Exception e) { error("连接服务器", e); }
        finally { connecting = false; }
    }
    interface MessageHandler { void run(JSONObject data) throws Exception; }
    private void listen(String event, MessageHandler handler) {
        socket.on(event, args -> worker.execute(() -> {
            try { if (args.length > 0 && args[0] instanceof JSONObject) handler.run((JSONObject) args[0]); }
            catch (Exception e) { error("处理连接消息", e); }
        }));
    }
    private boolean addressed(JSONObject data) {
        return socket != null && Objects.equals(socket.id(), data.optString("receiver"));
    }
    private void emit(String event, JSONObject data) {
        if (socket != null && socket.connected()) socket.emit(event, json("request_id", UUID.randomUUID().toString(),
                "socket_id", socket.id(), "user_info", new JSONObject(), "user_token", "", "time", System.currentTimeMillis(), "data", data));
    }
    private void remoteResult(JSONObject result) {
        if (result.optInt("code", -1) != 0) {
            stopViewingNow(); viewerStatus = "连接失败：" + result.optString("msg"); changed(); return;
        }
        JSONObject data = result.optJSONObject("data");
        if (data == null) return;
        if (addressed(data) && sharing && data.optString("remoteDeskUserUuid").equals(uuid)
                && MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),
                data.optString("remoteDeskUserPassword").getBytes(StandardCharsets.UTF_8))) {
            String sender = data.optString("sender");
            if (!sender.isEmpty() && !peers.containsKey(sender) && requests.size() < 3 && hostConnections < 3) {
                requests.put(sender, data);
                main.post(() -> { Listener l = listener; if (l != null) l.onRequest(sender, data.optString("deskUserUuid")); });
                status = "收到远程请求，请在手机上确认"; changed();
            }
        } else if (Objects.equals(socket.id(), data.optString("sender"))
                && data.optString("remoteDeskUserUuid").equals(viewerTarget)) {
            viewerSender = data.optString("receiver");
            viewerStatus = "等待对方允许连接…"; changed();
        }
    }
    void showPendingRequests() {
        worker.execute(() -> {
            for (Map.Entry<String, JSONObject> r : requests.entrySet()) {
                String sender = r.getKey(), device = r.getValue().optString("deskUserUuid");
                main.post(() -> { Listener l = listener; if (l != null) l.onRequest(sender, device); });
            }
        });
    }
    void answerRequest(String sender, boolean allow) {
        worker.execute(() -> {
            JSONObject request = requests.remove(sender);
            if (!allow || request == null || !sharing || screenTrack == null || !online) return;
            try {
                Peer peer = new Peer(sender, uuid, true);
                peers.put(sender, peer); hostConnections++; changed();
                peer.pc.addTrack(screenTrack, Collections.singletonList("billd-screen"));
                peer.pc.createOffer(new SdpAdapter() {
                    @Override public void onCreateSuccess(SessionDescription sdp) {
                        worker.execute(() -> peer.local(sdp, "nativeWebRtcOffer"));
                    }
                }, new MediaConstraints());
            } catch (Exception e) { error("启动远程", e); }
        });
    }
    void view(String target, String targetPassword) {
        worker.execute(() -> {
            if (!online) { viewerStatus = "请先连接服务器"; changed(); return; }
            if (target.isEmpty() || targetPassword.isEmpty() || target.equals(uuid)) {
                viewerStatus = "请输入其他设备的代码和连接密码"; changed(); return;
            }
            stopViewingNow(); viewerTarget = target; viewerPassword = targetPassword;
            viewing = target; viewerStatus = "正在验证连接密码…"; changed();
            emit("billdDeskJoin", json("deskUserUuid", uuid, "deskUserPassword", password, "live_room_id", target));
            emit("billdDeskStartRemote", json("roomId", target, "sender", socket.id(), "receiver", "",
                    "deskUserUuid", uuid, "deskUserPassword", password, "remoteDeskUserUuid", target,
                    "remoteDeskUserPassword", targetPassword, "maxBitrate", viewerQuality.bitrateKbps, "maxFramerate", viewerQuality.fps,
                    "resolutionRatio", viewerQuality.height, "videoContentHint", "text", "audioContentHint", "speech"));
            String requested = target;
            worker.schedule(() -> {
                if (viewerTarget.equals(requested) && viewerTrack == null && !viewing.isEmpty()) {
                    stopViewingNow(); viewerStatus = "连接超时，请确认对方已开启共享并允许连接"; changed();
                }
            }, 45, TimeUnit.SECONDS);
        });
    }
    void setViewerQuality(ViewerQuality quality) {
        worker.execute(() -> {
            viewerQuality = quality; quality.save(context);
            Peer peer = peers.get(viewerSender);
            if (peer != null && !peer.host && !peer.closed) peer.sendQuality();
            changed();
        });
    }
    String receivedPicture() {
        return viewerTrack == null || frameWidth == 0 ? "尚未收到画面"
                : "已收到：" + frameWidth + " × " + frameHeight + " · "
                + (receivedFps == 0 ? "帧率测量中" : "约 " + receivedFps + " 帧/秒");
    }
    private void offerReceived(JSONObject data) throws Exception {
        String sender = data.optString("sender");
        if (!addressed(data) || viewerSender.isEmpty() || !sender.equals(viewerSender)
                || !data.optString("live_room_id").equals(viewerTarget) || peers.containsKey(sender)) return;
        Peer peer = new Peer(sender, viewerTarget, false); peers.put(sender, peer);
        viewerIsAndroid = data.optString("platform").equals("android");
        List<IceCandidate> queued = earlyIce.remove(sender);
        if (queued != null) queued.forEach(peer::candidate);
        peer.remote(data.getJSONObject("sdp"), () -> peer.pc.createAnswer(new SdpAdapter() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                worker.execute(() -> peer.local(sdp, "nativeWebRtcAnswer"));
            }
        }, new MediaConstraints()));
        viewerStatus = "正在建立画面连接…"; changed();
    }
    void setConfig(ServerConfig next) {
        worker.execute(() -> {
            if (sharing) { status = "请先停止共享，再修改服务器"; changed(); return; }
            closePeers();
            if (socket != null) { socket.off(); socket.disconnect(); socket = null; }
            online = false; next.save(context); config = next;
            uuid = ""; password = ""; connectNow();
        });
    }
    void resetPassword() {
        worker.execute(() -> {
            try {
                if (!online) return;
                closePeers();
                String next = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                api("PUT", "/desk_user/update_by_uuid", json("uuid", uuid, "password", password, "new_password", next));
                password = next;
                identityPreferences().edit().putString("password", next).apply();
                emit("billdDeskUpdateUser", json("deskUserUuid", uuid, "deskUserPassword", password));
                status = "连接密码已更新"; changed();
            } catch (Exception e) { error("更新密码", e); }
        });
    }

    void startScreen(Intent consent, int width, int height) {
        worker.execute(() -> {
            if (sharing) return;
            try {
                screenWidth = width; screenHeight = height;
                texture = SurfaceTextureHelper.create("billd-capture", egl.getEglBaseContext());
                screenSource = factory.createVideoSource(true);
                capturer = new ScreenCapturerAndroid(consent, new MediaProjection.Callback() {
                    @Override public void onStop() { stopSharing(); }
                });
                capturer.initialize(texture, context, screenSource.getCapturerObserver());
                double scale = Math.min(1.0, (double) limitSize / Math.max(width, height));
                capturer.startCapture(even(width * scale), even(height * scale), captureFps);
                screenTrack = factory.createVideoTrack("billd-screen", screenSource);
                sharing = true; status = "共享已开启，连接仍需你确认"; changed();
            } catch (Exception e) { stopScreenNow(); error("屏幕共享", e); }
        });
    }
    private static int even(double value) { return Math.max(2, ((int) value / 2) * 2); }
    void resizeScreen(int width, int height) {
        worker.execute(() -> {
            screenWidth = width; screenHeight = height;
            if (capturer != null && sharing) {
                double scale = Math.min(1.0, (double) limitSize / Math.max(width, height));
                capturer.changeCaptureFormat(even(width * scale), even(height * scale), captureFps);
            }
        });
    }
    void stopSharing() { worker.execute(this::stopScreenNow); }
    private void stopScreenNow() {
        sharing = false; requests.clear();
        main.post(() -> { if (DeskAccessibilityService.current != null) DeskAccessibilityService.current.resetInput(); });
        List<String> hosts = new ArrayList<>();
        peers.forEach((id, p) -> { if (p.host) hosts.add(id); });
        hosts.forEach(this::closePeer);
        if (capturer != null) { capturer.stopCapture(); capturer.dispose(); capturer = null; }
        if (screenTrack != null) { screenTrack.dispose(); screenTrack = null; }
        if (screenSource != null) { screenSource.dispose(); screenSource = null; }
        if (texture != null) { texture.dispose(); texture = null; }
        main.post(() -> context.stopService(new Intent(context, CaptureService.class)));
        status = online ? "已停止共享" : "未连接服务器"; changed();
    }
    void stopViewing() { worker.execute(() -> { stopViewingNow(); changed(); }); }
    private void stopViewingNow() {
        List<String> viewers = new ArrayList<>();
        peers.forEach((id, p) -> { if (!p.host) viewers.add(id); });
        viewers.forEach(this::closePeer);
        earlyIce.clear();
        viewerTarget = ""; viewerPassword = ""; viewerSender = ""; viewing = ""; viewerTrack = null;
        main.post(() -> { Listener l = listener; if (l != null) l.onViewerTrack(null); });
    }
    private void closePeers() { new ArrayList<>(peers.keySet()).forEach(this::closePeer); requests.clear(); stopViewingNow(); }
    private void closePeer(String id) {
        Peer peer = peers.remove(id);
        if (peer == null) return;
        peer.closed = true;
        for (DataChannel c : peer.channels) { c.unregisterObserver(); c.close(); c.dispose(); }
        peer.pc.close(); peer.pc.dispose();
        if (peer.host) {
            hostConnections = Math.max(0, hostConnections - 1);
            if (hostConnections == 0) {
                main.post(() -> { if (DeskAccessibilityService.current != null) DeskAccessibilityService.current.resetInput(); });
                if (sharing) status = "共享已开启，连接仍需你确认";
            }
        }
        else {
            viewerTrack = null; viewing = ""; viewerStatus = "远程连接已结束";
            frameWidth = 0; frameHeight = 0; receivedFps = 0;
            main.post(() -> { Listener l = listener; if (l != null) l.onViewerTrack(null); });
        }
        changed();
    }
    void behavior(int type, double x, double y, double amount, JSONArray keys) {
        worker.execute(() -> {
            Peer peer = peers.get(viewerSender);
            if (peer != null && !peer.host) peer.send("billdDeskBehavior", json("roomId", peer.room,
                    "sender", socket.id(), "receiver", peer.id, "type", type, "x", x, "y", y,
                    "amount", amount, "key", keys == null ? new JSONArray() : keys));
        });
    }
    void androidAction(String action) {
        worker.execute(() -> {
            Peer peer = peers.get(viewerSender);
            if (peer != null && !peer.host) peer.send("androidAction", json("action", action));
        });
    }
    void navigation(String action) {
        worker.execute(() -> {
            if (viewerIsAndroid) androidAction(action);
            else {
                JSONArray keys = action.equals("back") ? new JSONArray().put(0)
                        : action.equals("home") ? new JSONArray().put(43) : new JSONArray().put(107).put(49);
                behavior(15, 0, 0, 0, keys);
                behavior(16, 0, 0, 0, keys);
            }
        });
    }

    private class SdpAdapter implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sdp) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String reason) { worker.execute(() -> { status = "画面协商失败"; changed(); }); }
        @Override public void onSetFailure(String reason) { worker.execute(() -> { status = "画面协商失败"; changed(); }); }
    }
    private final class Peer implements PeerConnection.Observer {
        final String id, room;
        final boolean host;
        final PeerConnection pc;
        final List<DataChannel> channels = new ArrayList<>();
        final List<IceCandidate> pendingIce = new ArrayList<>();
        DataChannel outgoing;
        boolean remoteSet, closed;
        Peer(String id, String room, boolean host) {
            this.id = id; this.room = room; this.host = host;
            List<PeerConnection.IceServer> ice = Arrays.asList(
                    PeerConnection.IceServer.builder("stun:" + java.net.URI.create(config.server).getHost() + ":3478").createIceServer(),
                    PeerConnection.IceServer.builder(config.turn).setUsername(config.user).setPassword(config.password).createIceServer());
            PeerConnection.RTCConfiguration rtc = new PeerConnection.RTCConfiguration(ice);
            rtc.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            pc = factory.createPeerConnection(rtc, this);
            if (pc == null) throw new IllegalStateException("Peer connection unavailable");
            DataChannel.Init init = new DataChannel.Init();
            init.ordered = true;
            outgoing = pc.createDataChannel("MessageChannel", init); channel(outgoing);
        }
        void local(SessionDescription sdp, String event) {
            if (closed) return;
            pc.setLocalDescription(new SdpAdapter() {
                @Override public void onSetSuccess() {
                    worker.execute(() -> {
                        if (!closed) emit(event, json("live_room_id", room, "sender", socket.id(), "receiver", id,
                                "sdp", json("type", sdp.type.canonicalForm(), "sdp", sdp.description),
                                "isRemoteDesk", true, "platform", "android", "live_room", json("id", room)));
                    });
                }
            }, sdp);
        }
        void remote(JSONObject sdp, Runnable done) throws Exception {
            if (closed) return;
            pc.setRemoteDescription(new SdpAdapter() {
                @Override public void onSetSuccess() {
                    worker.execute(() -> {
                        if (closed) return;
                        remoteSet = true; pendingIce.forEach(pc::addIceCandidate); pendingIce.clear();
                        if (done != null) done.run();
                    });
                }
            }, new SessionDescription(SessionDescription.Type.fromCanonicalForm(sdp.getString("type")), sdp.getString("sdp")));
        }
        void candidate(IceCandidate ice) { if (remoteSet) pc.addIceCandidate(ice); else if (pendingIce.size() < 128) pendingIce.add(ice); }
        void channel(DataChannel channel) {
            channels.add(channel);
            channel.registerObserver(new DataChannel.Observer() {
                @Override public void onBufferedAmountChange(long previous) {}
                @Override public void onStateChange() {
                    worker.execute(() -> {
                        if (closed || host || channel != outgoing || outgoing.state() != DataChannel.State.OPEN) return;
                        // Desktop hosts apply bitrate through the control channel, not the initial offer.
                        sendQuality();
                    });
                }
                @Override public void onMessage(DataChannel.Buffer buffer) {
                    if (buffer.binary || buffer.data.remaining() > 32768) return;
                    byte[] bytes = new byte[buffer.data.remaining()]; buffer.data.get(bytes);
                    worker.execute(() -> {
                        if (closed || !host || !sharing || !peers.containsKey(id)) return;
                        try {
                            JSONObject msg = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                            JSONObject data = msg.optJSONObject("data");
                            if (data == null) return;
                            String type = msg.optString("msgType");
                            if (type.equals("billdDeskBehavior") || type.equals("androidAction")) {
                                main.post(() -> {
                                    if (!sharing || closed) return;
                                    DeskAccessibilityService service = DeskAccessibilityService.current;
                                    if (service != null) service.command(type, data);
                                });
                            } else if (type.equals("changeMaxFramerate") || type.equals("changeResolutionRatio")) {
                                if (type.equals("changeMaxFramerate")) captureFps = Math.max(5, Math.min(30, data.optInt("val", 20)));
                                else limitSize = Math.max(360, Math.min(1920, data.optInt("val", 1080)));
                                resizeScreen(screenWidth, screenHeight);
                            } else if (type.equals("changeMaxBitrate")) bitrate(data.optInt("val", 1500));
                        } catch (Exception ignored) { Log.w("BilldDesk", "Ignored invalid control message"); }
                    });
                }
            });
        }
        void sendQuality() {
            ViewerQuality quality = viewerQuality;
            send("changeMaxBitrate", json("val", quality.bitrateKbps));
            send("changeMaxFramerate", json("val", quality.fps));
            send("changeResolutionRatio", json("val", quality.height));
            send("changeVideoContentHint", json("val", "text"));
        }
        void send(String type, JSONObject data) {
            if (outgoing.state() == DataChannel.State.OPEN && outgoing.bufferedAmount() < 65536)
                outgoing.send(new DataChannel.Buffer(ByteBuffer.wrap(json("msgType", type,
                        "requestId", UUID.randomUUID().toString(), "data", data).toString().getBytes(StandardCharsets.UTF_8)), false));
        }
        void bitrate(int kbps) {
            for (RtpSender sender : pc.getSenders()) {
                if (sender.track() != null && sender.track().kind().equals("video")) {
                    RtpParameters p = sender.getParameters();
                    for (RtpParameters.Encoding e : p.encodings) e.maxBitrateBps = Math.max(250, Math.min(8000, kbps)) * 1000;
                    sender.setParameters(p);
                }
            }
        }
        @Override public void onIceCandidate(IceCandidate candidate) {
            worker.execute(() -> { if (!closed) emit("nativeWebRtcCandidate", json("live_room_id", room,
                    "sender", socket.id(), "receiver", id, "candidate", json("candidate", candidate.sdp,
                    "sdpMid", candidate.sdpMid, "sdpMLineIndex", candidate.sdpMLineIndex))); });
        }
        @Override public void onDataChannel(DataChannel channel) { worker.execute(() -> { if (!closed) channel(channel); }); }
        @Override public void onConnectionChange(PeerConnection.PeerConnectionState state) {
            worker.execute(() -> {
                if (closed) return;
                if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                    if (host) { status = "正在被远程控制（" + hostConnections + " 个连接）"; bitrate(1500); }
                    else viewerStatus = "已连接，可触控和输入文字";
                    changed();
                } else if (state == PeerConnection.PeerConnectionState.FAILED || state == PeerConnection.PeerConnectionState.CLOSED) closePeer(id);
            });
        }
        @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
            if (!host && receiver.track() instanceof VideoTrack) {
                VideoTrack track = (VideoTrack) receiver.track();
                track.addSink(new VideoSink() {
                    long since;
                    int frames;
                    @Override public void onFrame(VideoFrame frame) {
                        if (closed || viewerTrack != track) return;
                        frameWidth = frame.getRotatedWidth(); frameHeight = frame.getRotatedHeight(); frames++;
                        long now = android.os.SystemClock.elapsedRealtime();
                        if (since == 0) since = now;
                        long elapsed = now - since;
                        if (elapsed >= 2000) {
                            receivedFps = (int) Math.round(frames * 1000.0 / elapsed);
                            frames = 0; since = now; changed();
                        }
                    }
                });
                viewerTrack = track;
                main.post(() -> { Listener l = listener; if (l != null && !closed) l.onViewerTrack(track); });
            }
        }
        @Override public void onSignalingChange(PeerConnection.SignalingState state) {}
        @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {}
        @Override public void onIceConnectionReceivingChange(boolean receiving) {}
        @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}
        @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
        @Override public void onAddStream(MediaStream stream) {}
        @Override public void onRemoveStream(MediaStream stream) {}
        @Override public void onRenegotiationNeeded() {}
    }
}
