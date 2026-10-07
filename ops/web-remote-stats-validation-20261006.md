# 网页实时统计与 Android 共享帧率验证

日期：2026-10-06。分支 `p/ningyuwen/web-remote-stats`，基于更新后的 develop（e2aa6ef）。本轮未提交或推送。

## 修复

- 原网页通过接收轨道 getSettings.frameRate 得到 Infinity；WebRTC 统计轮询定义后没有启动，延迟和丢包停在占位值。
- 启动每秒统计，使用相邻 inbound-rtp 报告的解码帧数、接收字节、丢包、缓冲等待和解码耗时增量。往返延迟取 transport 选中候选对，路径只显示直连/中继和协议，不显示地址。缺失数据显示“测量中”，关闭连接清理轮询。详情区域限制尺寸并允许纵向滚动。
- Android ScreenCapturerAndroid 忽略 fps 参数，项目 SDK 150.7871.01 字节码也已确认。增加 VideoSource.adaptOutputFormat，在编码前限制输出帧率，保留原尺寸调整和 5–30 fps 范围。

上游依据：[ScreenCapturerAndroid](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/ScreenCapturerAndroid.java)、[VideoSource](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/VideoSource.java)、[WebRTC Statistics](https://www.w3.org/TR/webrtc-stats/)。

## 构建与部署

- 网页生产构建成功，产物 /tmp/billd-desk-web-stats-20261006，部署到 49.51.202.11:/var/www/billd-desk。20 个文件哈希一致，HTTPS index 和入口脚本回读一致。旧资源、downloads 保留；备份 /home/ubuntu/billd-desk-stats-backup-20261006-h1yJyh/web.tar.gz。
- Mac 构建与 ARM64 DMG 打包成功，固定身份 BilldDesk Local Code Signing。覆盖 /Applications/BilldDesk.app，LaunchServices 重新启动，设置页和导航正常。严格签名验证通过；安装和打包 app.asar SHA-256 均为 1c835376270856d2ee9989fcf13fc271d0ac6b2cda59b4cb9c7b5621c3f4cef4。
- Android 0.1.8（versionCode 9）release 和 Lint 成功，0 errors、8 warnings。原签名覆盖安装到 Xiaomi 24122RKC7C / Android 16，保存设备、默认画质和触控授权保留。回读安装 APK 与产物 SHA-256 均为 c860bc642be2a3328a8d7eddb66d66cea2a64fd048a4b8ed465764d5a0d7dfcc；证书 SHA-256 为 6f337db36a294193a835e2eaf04901da77fc7b51f6e5d4982df0573c7c2503fa。
- 本地 APK 副本 android-release/0.1.8/BilldDesk-android-0.1.8-arm64.apk，Android/Mac 正式下载包未发布。
- Vue/TypeScript 文件 ESLint、git diff --check 通过，保留已有大包及 Gradle 弃用提示。

## 实机采样

Chrome 网页连接真实手机，经确认后共享横屏。动态采样采用 BilldDesk 首页上下滚动，避免将静止画面的低帧率误判为卡顿。帧率指每秒解码帧数增量，不是完整的触控到显示延迟。

| 场景 | 实际尺寸 | 解码帧率 | 网络往返 | 缓冲等待 | 单帧解码 | 采样丢帧 |
| --- | --- | --- | --- | --- | --- | --- |
| 修复前，高画质采样 1 | 1920×864 | 19 fps | 7 ms | 41.52 ms | 1.80 ms | 0 |
| 修复前，高画质采样 2 | 1920×864 | 8 fps | 11 ms | 1556.32 ms | 2.04 ms | 0 |
| 修复前，降低尺寸后 | 1440×648 | 121.98 fps | 90 ms | 128.16 ms | 1.14 ms | 0 |
| 修复后，1440 采样 1 | 1440×648 | 28 fps | 31 ms | 73.42 ms | 1.95 ms | 0 |
| 修复后，1440 采样 2 | 1440×648 | 30 fps | 7 ms | 104.04 ms | 2.48 ms | 0 |
| 修复后，高清采样 1 | 1920×864 | 30 fps | 8 ms | 102.04 ms | 3.50 ms | 0 |
| 修复后，高清采样 2 | 1920×864 | 30 fps | 7 ms | 71.88 ms | 3.81 ms | 0 |
| 新 Mac 控制端，真实连接手机 | 1920×864 | 30.02 fps | 8 ms | 81.22 ms | 3.64 ms | 0 |

这些采样均为 UDP 直连。高清两次采样丢包为 0%，未经美国服务器中继。帧率限制失效及一次超过 1.5 秒的缓冲等待有实际证据；不能断言其他网络波动或操作卡顿已全部消除。

当前会话保留 2160P / 30 fps / 6000 kbps / 文本。Android 共享长边最多 1920，因此实际横屏为 1920×864，并非 2160 高度。码率设置不是持续吞吐量；高清两次 1 秒接收采样为 6.90、8.56 Mbps，有短时超过目标的读数，不把该设置当作网络速率保证。静止页采样降至 0–1 fps，不承诺持续固定 30 fps。

新 Mac 客户端也建立真实手机连接，显示实际共享画面与持续更新的统计；滚动采样约 30.02 fps，UDP 直连、0% 丢包。额外 Mac 测试连接及窗口已结束，避免多路编码增加负载。

覆盖升级后重新建立真实网页连接；网页点击成功打开手机设置，再次点击完成关闭弹窗。手机保存的控制电脑默认画质仍为 2160P / 60 fps / 30 Mbps，未更改持久设置。截图和不含连接凭证的采样位于被 Git 忽略的 android-release/web-remote-stats-20261006/。保留实时连接供用户体验。

## 未覆盖

- Windows、Linux、其他手机、跨网络中继和长时间高负载未验证。
- Mac 控制手机的画面和实时统计已验证；本轮未新增 Android 控制 Mac 的完整回归或 Mac 控制手机的输入操作测试。
- Chrome 视口覆盖未改变页面实际尺寸，已恢复，不能作为统计区 800×500 实测证据。Mac 主窗口启动和设置页已验证，新增远控统计在 Mac 小窗口的滚动需单独验证。
- 没有测量完整的触控到屏幕响应延迟，也未新增共享状态下的竖屏回归。

额外观察：关闭 Mac 测试连接后，手机仍显示“2 个连接”。源码 closePeer 只在全部连接结束时改写状态文字，因此部分断开时计数文案可能过期；该文案不能单独证明仍有两路活动编码，本轮未改动此状态问题。
