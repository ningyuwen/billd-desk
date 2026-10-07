# Android → Mac 编码与发送排队复测（2026-10-07）

## 结论及范围

手机上的详情动画正常，Mac 上过渡被拉长并出现叠影，主要积压发生在 Android 编码与 RTP 发送之间。本轮修复后，同一可颂猫咪图文的主要进入动画从约 3 秒缩短到约 0.33 秒；2000 kbps 进入采样的平均包发送等待从 827 ms 降至 29 ms。返回最终画面也能同步。这里比较的是画面变化持续时间，不是点击到显示的端到端延迟，也不是平均显示帧率。

高码率及长时间静止后仍会出现大帧、编码数量波动。本轮没有证明全程稳定 60 fps，也没有覆盖公网、TURN、中低端机或其他硬件编码器。

## 原因与保留的修改

1. 固定 SDK `io.github.webrtc-sdk:android:150.7871.01` 的硬件编码器会按码率控制传入的 fps 递增 Surface 时间戳。静止后 native 估算降到 1–2 fps，恢复动画时，实测 QP 1–4、单个 delta 帧最高 1,085,478 字节。新增 `org.webrtc.ScreenVideoEncoderFactory`，仅在硬件叶子编码器的 `setRates` / `setRateAllocation` 中把 fps 保持为当前采集上限（既有范围 5–60）；保留原网络码率、采集机制、回调和 native 软件回退。可选采样记录输入/输出时刻、大小、QP 和有效参数，正常模式不采样。
2. 屏幕共享的 ALR 参数会覆盖通用 `WebRTC-Video-Pacing` 队列配置。SDK native 二进制及上游源码均可确认屏幕共享默认组为 `1.0,2875,80,40,-60,3`。初始化采用 `WebRTC-ProbingScreenshareBwe/1.0,100,80,40,-60,3/`，只把排队目标从 2875 改为 100 ms，保持其余 pacing / probing / ALR 参数。100 ms 是发送队列目标，不是端到端时延上限，原拥塞控制继续工作。
3. Mac 可选采样增加收到帧、字节、包、丢包、PLI/NACK 和解码总耗时计数；Android 增加编码 QP、重传及 TWCC 是否协商成功的布尔量。只记录白名单计数，不记录 SDP、ICE 地址、连接 URL 或凭证。

既有半秒静止补帧、H264 硬件优先和分辨率保持策略保留。上一轮持续 60 fps 补帧试验仍然撤回。

原理入口：[HardwareVideoEncoder.java](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/src/java/org/webrtc/HardwareVideoEncoder.java)、[ALR 默认与解析](https://webrtc.googlesource.com/src/+/refs/heads/main/rtc_base/experiments/alr_experiment.cc)、[屏幕共享覆盖通用队列参数](https://webrtc.googlesource.com/src/+/refs/heads/main/video/video_send_stream_impl.cc)、[Pacing 队列调整机制](https://webrtc.googlesource.com/src/+/refs/heads/main/modules/pacing/pacing_controller.cc)。本地同时检查了固定 AAR 的 Java 字节码和 native 字符串，避免仅依据上游 main 推断当前 SDK。

## 实机数据

小米 24122RKC7C / Android 16 → 本机 Apple 芯片 Mac；同一 Wi-Fi、直连 UDP。物理屏幕 1440×3200，发送尺寸 1080×2400。用户设定的码率是上限，12000 kbps 组实际目标约 4–6 Mbps。表中均为操作后的约两秒统计窗口。

| 版本 / 条件 | 操作 | 编码帧数 | 平均编码 ms | 平均包发送等待 ms | 最大采样编码帧 bytes |
| --- | --- | ---: | ---: | ---: | ---: |
| 0.1.24 原行为，2000 / 60 | 进入 | 12 | 29.17 | 827.25 | 1,085,478 |
| 0.1.24 原行为，2000 / 60 | 返回 | 5 | 72.00 | 894.95 | 772,729 |
| 0.1.25 固定编码 fps，2000 / 60 | 进入 | 21 | 22.33 | 206.17 | 58,549 |
| 0.1.25 固定编码 fps，12000 / 60，长静止 | 返回 | 7 | 42.00 | 874.26 | 585,327 |
| 0.1.26 通用 queue trial，2000 / 60 | 进入 | 17 | 16.94 | 583.04 | 39,452 |
| **0.1.27 正确屏幕共享 trial，2000 / 60** | **进入** | **21** | **15.43** | **29.03** | **50,826** |
| 0.1.27，2000 / 60 | 返回 | 26 | 20.12 | 27.83 | 33,197 |
| 0.1.27，12000 / 60 | 进入 | 21 | 17.76 | 35.69 | 152,931 |
| 0.1.27，12000 / 60，约 49 秒静止 | 返回 | 28 | 22.54 | 49.85 | 427,432 |
| 0.1.27，12000 / 60，再次进入 | 进入 | 14 | 31.93 | 49.19 | 430,961 |
| 0.1.27，12000 / 60，再次返回 | 返回 | 26 | 21.08 | 47.58 | 279,725 |
| 0.1.27，6000 / 30，长静止 | 进入 | 12 | 33.75 | 49.60 | 837,135 |
| 0.1.27，6000 / 30 | 返回 | 6 | 32.00 | 50.38 | 561,592 |

平均发送等待为 `ΔtotalPacketSendDelay / ΔpacketsSent`，平均编码为 `ΔtotalEncodeTime / ΔframesEncoded`。采样窗口的编码帧数包含静止补帧，不能直接除以两秒当作动画 fps；采集源最高约 120 Hz，采集到编码的数量差也不能全部称为编码掉帧。

0.1.27 所有这轮进入/返回窗口均无新增发送重传、接收丢包、NACK 或解码丢帧；TWCC 协商为 true。初次 2000 组解码 21 / 26 帧，12000 组解码 22 / 29 帧，平均 jitter buffer 等待约 10–14 ms。之后高码率进入仅编码/解码 14 帧，30 fps 组仅 12 / 6 帧，仍有明显的编码波动。没有把静止造成的 `freezeCount` 增长当作动画卡顿。

## 录屏与证据

本地证据目录为 `android-release/encoded-jank-20261007/`（忽略目录，未提交）。仅录 BilldDesk 窗口，无音轨。

- [原速前后对照](../android-release/encoded-jank-20261007/before-after.mp4)：左 0.1.24（仅加诊断，行为与 0.1.23 相同），右 0.1.27；都是 1080P / 60 fps 上限 / 2000 kbps。分别按主要画面开始变化对齐，未按点击时刻对齐，因此不能用于比较点击延迟。7 秒 / 420 帧 / H264 / 900×1080，未加速。
- 原文件 `baseline-2000.mov` / `alr-2000.mov`。主要图像 ROI 的变化点：旧 5.142 / 5.833 / 6.508 / 7.225 / 8.142 秒；新 4.567 至 4.900 秒。旧后续 9.667 秒还有细节更新。阈值取缩小灰度 ROI 的平均绝对差 > 0.6，并结合图像逐帧复核；它是可见变化检测，不能替代独立显示 fps 测量。录屏本身最大相邻时间戳间隔约 33 ms，解释不了旧画面约 0.7 秒一跳。
- `alr-12000.mov` 60 秒：进入及约 49 秒静止后的返回都在录制范围内，返回主要变化约 55.358–55.633 秒。
- `repeat-12000.mov` 60 秒：包含第一组进入/返回及第二次进入；后续滚动和最终返回发生在录制结束后，只有 UI 与诊断证据。`fixed-12000.mov` 同样只录到进入，不能声称录到了该次返回。
- `normal-2000.mov`：关闭诊断并经 LaunchServices 正常启动后，进入、滚动到评论和返回的独立录像。录制开始 12:17:27，三次操作分别为 12:17:35 / 12:17:50 / 12:17:57，均在 60 秒范围内；抽查第 25 秒为评论、第 36 秒为 Feed。`normal-detail.png` / `normal-scroll.png` / `normal-feed.png` / `normal-settings.png` 保存实际客户端画面。
- `sender-summary.json` 从白名单 Android 日志计算；Mac 对应计数保存在 `alr2000-mac.jsonl` / `alr12000-mac.jsonl` / `repeat12000-mac.jsonl`。最后一个文件还包含之后的 30 fps 验证，可按时间区分。

## 构建、部署与正常模式

- Android：`./gradlew :app:assembleRelease :app:lintRelease` 成功，Lint 0 errors / 8 warnings；当前 `versionName=0.1.27` / `versionCode=28`。使用既有发布签名 `adb install -r` 覆盖安装，保留数据、无障碍授权和共享配置。
- APK：`android-release/0.1.27/BilldDesk-android-0.1.27-arm64.apk`，SHA256 `eeff54f2891157641357c91d22cb05ed3aa5dd0f30e73e8e3fd58a3c9e666e1a`；从已安装应用拉出的 `base.apk` 完全相同。证书 SHA256 `6f337db36a294193a835e2eaf04901da77fc7b51f6e5d4982df0573c7c2503fa`。
- Mac：desktop Vite 构建及 electron-builder arm64 DMG 成功，覆盖 `/Applications/BilldDesk.app`，完全退出旧进程后通过 `open /Applications/BilldDesk.app` 正常启动。应用与本次产物的 `app.asar` SHA256 一致：`d2b4d018631ba8a8fc7a63c12ca6019c35607b56ac0deb17bbc14039c6170990`。`codesign --verify --deep --strict` 成功，固定证书要求仍为 `8d167221fed1c3d3b775d052bd7671e79fc348a6`。Mac 包版本保持 0.0.1，依赖哈希区分本次产物。
- 正常启动后实际重连手机；Mac 点击进入对应手机 `FlowPageActivity`，滚动到评论后返回对应 `SplashActivity`，画面回到双列 Feed。诊断日志 Android / Mac 均新增 0 字节，确认普通模式不采样。无新增系统权限。
- 检查固定 800×500 主窗口；远程窗口实际 448×994，连接详情可展开、收起并显示实际参数。最终恢复 1080P / 60 fps / 2000 kbps，手机旋转状态恢复 `free`，保留正常连接。
- 修改的 TypeScript 文件 ESLint 与 `git diff --check` 通过。未提交、推送、发布下载或修改服务端。

## 未解决的边界

原持续补帧方案不足以解决发送积压，本轮纠正了编码预算与屏幕共享排队入口。但硬件 CBR 在静止后仍可能输出 400–800 KB 帧，低码率动态图像也有暂时变糊；目前的 100 ms 队列目标只能减小局部排队，不能消除编码耗时、网络瓶颈或保证 60 fps。其他编码器、软件回退、横屏动画、网页/Windows/Linux 接收端及公网连接未在本轮实机覆盖。
