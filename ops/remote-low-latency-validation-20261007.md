# Android 共享到 Mac 的低延迟修复验证

测试开始于 2026-10-06，构建、安装和最终验证跨日完成于 2026-10-07。分支 `p/ningyuwen/web-remote-stats`，基线与获取后的 `origin/develop` 均为 `e2aa6ef9cf0891fcbda51383a3aee0e4b3328d8a`。保留已有未提交修改，本轮没有提交、推送或发布。

## 问题与修改

此前可颂视频测试中，Mac 的网络往返为 5～12 ms、解码约 5～7 ms、采样丢帧 0、丢包 0%，但接收缓冲约 302～651 ms；准备阶段曾观察到 1619 ms。降低分辨率仍有数百毫秒缓冲，不能仅靠提高码率消除操作后的等待。详见 [前一轮性能对照](kesong-feed-jank-validation-20261006.md)。这些缓冲指标只描述接收链路的一部分，不是完整触控到显示延迟。

- Electron 主进程在 ready 前启用 `WebRTC-ForcePlayoutDelay/min_ms:0,max_ms:50/`，让接收端采用低延迟播放策略，保留小幅恢复窗口。当前 Electron 33.2.1 的已安装框架包含该配置项，测试确认有效；升级 Electron 时需要重新核实兼容性。[WebRTC 原始实现](https://webrtc.googlesource.com/src/+/14a23a32c4419210c65cd5e4f98557c3f19ab3a0)。
- Android 将共享 `VideoSource` 的帧率上限从 30 提高到 60，并将 `RtpParameters.Encoding.maxFramerate` 同步到请求值；改变帧率时重新应用现有码率参数。保留按原生像素限制分辨率、MAINTAIN_RESOLUTION 和原有数据通道。
- Android 更新为 0.1.11 / versionCode 12。Mac 包版本仍为 0.0.1，没有运行版本升级脚本。

## 对照与最终运行证据

先用旧版 Mac 的启动参数临时开启上述播放配置，保持同一 Android 0.1.10 和可颂视频来源，重新连接后观察到 29.03 fps、62 ms 往返、5 ms 缓冲、4.12 ms 解码、0 采样丢帧和 0% 丢包。这说明接收播放配置对本次缓冲等待有直接影响；这是临时对照，不替代新版安装后的验证。数据为 `android-release/low-latency-20261006/playout-trial.json`。

随后完全退出旧 Mac 进程，安装本次构建产物，以 LaunchServices 正常 `open -a /Applications/BilldDesk.app` 启动，没有附加 fieldtrial 参数；确认主进程命令和打包后的主进程脚本。Android 使用同一签名覆盖安装，正常开启系统共享和单次远程连接确认。触控确认最初没有生效，改用聚焦到可见「允许」按钮的键盘确认后，手机显示 1 个连接，Mac 正常收到真实画面；没有绕过连接审批或改变授权范围。

测试设备：Xiaomi 24122RKC7C / Android 16；可颂 `com.ss.android.ugc.sicily_cm`，40.2.1 / 400201。当前持久画质为 1080P / 60 帧，最终连接使用 12 Mbps 上限和平衡模式，实际收到 1080×2400。码率与帧率设置是上限，不保证每个采样窗口都有 60 帧。

连续慢滑产生动态画面，通过 Mac 界面实时统计读取三个相隔约 2.3 秒的独立窗口：

| 本地时间 | 解码帧率 | 缓冲等待 | 单帧解码 | 网络往返 | 实际码率 | 采样丢帧 / 丢包 |
| --- | --- | --- | --- | --- | --- | --- |
| 00:25:15 | 55.98 fps | 18.05 ms | 3.73 ms | 41 ms | 7.26 Mbps | 0 / 0% |
| 00:25:17 | 58.98 fps | 3.91 ms | 3.96 ms | 5 ms | 7.43 Mbps | 0 / 0% |
| 00:25:20 | 53.04 fps | 5.27 ms | 3.66 ms | 8 ms | 6.81 Mbps | 0 / 0% |

均为 UDP 直连。此前额外动态窗口为 42.88 fps / 7.72 ms 缓冲和 63.21 fps / 21.88 ms 缓冲；短统计窗口可能略高于 60，不能解读为稳定超过设定上限。静止 Feed 的 0 fps 和「测量中」不计入流畅性结论。以上记录证明已解除原来的 30 fps 限制，本次动态窗口未再次出现数百毫秒缓冲；没有持续峰值、长时间或弱网测量。

用 Mac 鼠标实际打开当前推荐 Feed 的「当我在宏村找了一个女摄～」及「海豚从眼前飞跃…」两件图文作品，并以 Option+Shift+B 返回，远程画面均确认进入、退出；第二件手机的 topResumedActivity 确认为 FlowPageActivity，返回后为 SplashActivity。最终 Feed 与上一轮不同，这些操作不加入上一轮同作品的 gfxinfo 对照，也没有宣称重新测量了原视频作品的慢帧比例。

## 编译、安装与产物一致性

- Android `:app:assembleRelease :app:lintRelease` 成功，Lint 0 errors / 8 warnings。使用原发布签名覆盖安装；回读安装包为 0.1.11 / code 12，与本地 APK 哈希相同。
- APK SHA-256：`c8a2a6f7ab51cf92e8b7e4d372ca8ae9308e0f614eb18a2d16447e1e50611e10`。本地副本 `android-release/0.1.11/BilldDesk-android-0.1.11-arm64.apk`；证书 SHA-256 保持 `6f337db36a294193a835e2eaf04901da77fc7b51f6e5d4982df0573c7c2503fa`。
- Mac 桌面 Vite 生产构建、arm64 electron-builder 和 DMG 生成成功。使用 `electron-builder.private.json5` 与原固定身份，安装到 `/Applications/BilldDesk.app`；安装前后 `codesign --verify --deep --strict` 通过。
- 安装后 app.asar 与打包产物 SHA-256 均为 `457afe43043708a9588c08ebdeb6ca3c9f034857ab8bb8148c2a41550982883b`。安装包 `electron-release/0.0.1/BilldDesk-mac-darwin-0.0.1-arm64-installer.dmg`。
- 主进程定向 ESLint、`git diff --check` 通过。未新增无关测试或变更应用数据、连接密码。

本机构建日志：`/tmp/billd-low-latency-android-build.log`、`/tmp/billd-low-latency-mac-build.log`、`/tmp/billd-low-latency-mac-package.log`。

## 结论和边界

本次 Android 到 Mac 单连接测试的主要播放等待已显著下降，动态解码帧率由最多约 30 提升到约 53～59 fps。这不是物理屏幕帧率，也不能证明操作已经流畅；用户随后反馈仍然卡顿，本轮结论只限于解码上限与缓冲等待。可颂自身进入详情的本地慢帧在此前无共享对照中也存在，本次没有修改可颂源码，不能保证所有作品或长时间操作完全不卡。

屏幕共享保护结束时回读为 secure `screen_share_protection_on=1`、global `disable_screen_share_protections_for_apps_and_notifications=0`，均保持原值。设备记录、连接数据保留；最终保留 Mac 与手机连接和推荐 Feed。

未覆盖网页接收端（浏览器不能复用 Electron 启动配置）、Windows/Linux 实机、其他手机、多连接、弱网和长时间视频。未部署线上网页或正式下载渠道。

截图和实时统计保存在 Git 忽略目录 `android-release/low-latency-20261006/`：`final-stats.json`、`final-continuous-stats.png`、`final-image-detail.png`、`final-second-detail.png`、`final-return-feed.png`。不保存密码、信令 SDP 或通知内容。

2026-10-07 后续纠正：此前界面的「实际帧率」来自 `framesDecoded`。已拆分解码与合成器呈现统计，并继续处理控制手势；见 [呈现与输入验证](remote-presentation-input-validation-20261007.md)。
