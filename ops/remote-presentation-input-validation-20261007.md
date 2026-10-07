# Mac 接收安卓画面：呈现统计与控制输入复查

2026-10-07，分支 `p/ningyuwen/web-remote-stats`。本轮获取 `origin/develop` 后仍为 `e2aa6ef9cf0891fcbda51383a3aee0e4b3328d8a`，与 HEAD 一致；保留此前修改，没有提交或推送。

## 纠正此前结论

上一轮的 53～59 fps 来自 RTP `framesDecoded` 的差分，是解码帧率，不能证明 Mac 屏幕真的持续显示相同帧率，也不能证明输入到显示的延迟低。用户再次反馈卡顿，原来的流畅性结论不成立。

Mac 现在将解码帧率与 `requestVideoFrameCallback` 的累计 `presentedFrames` 差分分别显示。后者是合成器提交帧，不是物理显示器或外部高速相机测量。每秒更新界面，避免每帧更新 Vue。显示回调帧间隔与末包接收到预计显示的平均等待，明确标注静止时间、漏回调的影响。[浏览器原始规范](https://wicg.github.io/video-rvfc/) 说明回调为 best effort，主线程忙时可漏回调；回调帧间隔不能直接等同于实际停顿。

## 输入链路修改

静态检查发现安卓被控端在 mouse-up 后才派发整段 Path，并按原拖动时间回放。这会延后操作反馈。改为 16 ms 分段的持续手势，保持按下，合并待处理点，松手派发最后一段，处理取消及 5 秒未更新的释放。Mac 在 window 上接收 mouse-up，避免移出视频后无法松开。

旧滚轮每个事件派发 350 ms 滑动，新事件会取消正在执行的手势。改为一个 80 ms 手势执行期间合并滚动量，完成后继续，限制积压距离。[Android 原始 API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) 说明 dispatchGesture 会取消已有手势；[持续手势 API](https://developer.android.com/reference/android/accessibilityservice/GestureDescription.StrokeDescription.html) 要求后续路径从上一段终点开始。

## 设备与采样

Xiaomi 24122RKC7C / Android 16；可颂 `com.ss.android.ugc.sicily_cm` 40.2.1 / code 400201；本机 Apple Silicon Mac，单连接 UDP 直连。画质 1080P、帧率上限 60、连接码率上限 12 Mbps，收到 1080×2400。码率上限只针对当前连接，不宣称重连后持续使用 12 Mbps。

以下是通过 ADB 长时间滑动产生连续动态画面，再从 Mac 界面每隔约 2.3 秒读数；用于观察视频呈现，不能替代 Mac 输入端到端延迟测量，也不是同作品的随机交叉实验。

| 版本 / 本地时间 | 解码 fps | 合成器呈现 fps | 回调帧间隔 ms | 收到末包至预计显示 ms | 缓冲 ms |
| --- | --- | --- | --- | --- | --- |
| 0.1.11 / 00:42:27 | 58.06 | 54.06 | 83.3 | 24.21 | 5.65 |
| 0.1.11 / 00:42:29 | 54.11 | 53.11 | 66.7 | 23.44 | 5.71 |
| 0.1.11 / 00:42:32 | 60 | 58 | 66.8 | 29.09 | 11.98 |
| 0.1.12 / 00:57:17 | 60.06 | 58.05 | 83.3 | 26.83 | 14.09 |
| 0.1.12 / 00:57:19 | 57 | 52.99 | 66.7 | 28.19 | 4.47 |
| 0.1.12 / 00:57:21 | 59.06 | 57.07 | 83.4 | 23.01 | 5.1 |

这些窗口均为 0 RTP 采样丢帧、0% 丢包。它们不包含合成器或屏幕丢帧。短窗口略高于 60 的解码差分不代表稳定超过上限。改输入链路并未使这组视频呈现采样明显提升，因此不能声称卡顿已解决。

Mac 实际操作已确认可打开五花海图文详情、返回 Feed，并使用滚轮继续下翻。图文进入、返回只证明操作到达与页面变化，没有量化过渡丢帧。CUA 的 drag 在手机日志中显示首个移动段在按下后 9 ms、`released=true`，工具产生的是快速拖动，不能证明持续按住时的实时跟手效果；持续按住的拖动验证仍未覆盖。定向日志未出现新取消警告，但不能由此宣称所有高频滚动场景都不会取消。

## 编译、安装与一致性

- Android 0.1.12 / code 13：`:app:assembleRelease :app:lintRelease` 成功，0 errors / 8 warnings；原发布签名覆盖安装，保留数据和保存设备。
- 本地构建 APK、本地副本 `android-release/0.1.12/BilldDesk-android-0.1.12-arm64.apk` 与回读安装 APK 的 SHA-256 均为 `b782b9a316b9489faf70a9e052928ac85d840780beae1cb5731011b646271e2c`。证书 SHA-256 保持 `6f337db36a294193a835e2eaf04901da77fc7b51f6e5d4982df0573c7c2503fa`。
- Mac 桌面 Vite 与 arm64 electron-builder 成功，使用原固定身份，完全退出后更新 `/Applications/BilldDesk.app`，通过 LaunchServices 正常启动；安装前后严格签名校验通过。包版本仍为 0.0.1。
- 最终 Mac 安装后与构建目录的 app.asar SHA-256 均为 `7452cf71d853e4e167cf9113ba58a08f31d9a478f075665c4f260236336da1ca`。正常主进程来自 `/Applications/BilldDesk.app/Contents/MacOS/BilldDesk`，无调试启动参数；再次连接原手机、正常确认后收到真实推荐 Feed，并确认最终界面包含解码、呈现、回调帧间隔和测量边界提示。
- 定向 ESLint 和 `git diff --check` 通过。

本机日志 `/tmp/billd-stream-input-android-build.log`、`/tmp/billd-presented-mac-build.log`、`/tmp/billd-presented-mac-package.log`。统计、截图、定向输入日志保存在 Git 忽略目录 `android-release/presentation-20261007/`，没有连接凭证、SDP 或通知内容。

## 边界与后续判断

这轮完成的是错误指标纠正与两个明确输入缺陷的修复。尚未证明用户感受到的持续卡顿已消除，不能用平均呈现 fps 替代这一结论。67～83 ms 是回调观察间隔，可能包括漏回调；下一步需要结合实际长拖动、呈现时间序列和源端帧时间区分控制延迟、采集/编码波动以及接收端调度。

屏幕共享保护回读仍为 secure `screen_share_protection_on=1`、global `disable_screen_share_protections_for_apps_and_notifications=0`；本轮没有扩大授权或清空应用数据。未部署网页、服务端或正式下载渠道；未覆盖物理显示器帧率、全链路触控延迟、持续按住拖动、弱网、多连接、其他手机、Windows/Linux。
