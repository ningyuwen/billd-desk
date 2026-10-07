# Android 详情返回残影修复验证

2026-10-07。开工获取 `origin/develop` 后仍为 `e2aa6ef9cf0891fcbda51383a3aee0e4b3328d8a`，本地 develop、原工作分支 HEAD 均与其一致。保留所有既有未提交修改，从 develop 建立 `p/ningyuwen/remote-final-frame`；未提交、推送、发布或修改服务器。

## 复现与判断

用户截图中手机已经退出详情，Mac 停在城市图文退出动画的半透明中间帧。检查正在运行的客户端时，ICE 往返仍更新，解码、呈现和接收码率均为 0；手机硬件编码器日志也出现静止窗口 encode=0。

升级前，用实际 Mac 点击咖啡作品，再点右上角返回，CUA 截图再次观察到详情缩小浮在 Feed 上，解码和呈现均为 0，接收码率为 0。同期手机截图 `phone-stalled-mac.png` 已显示完整 Feed。稍后 Mac 自行恢复，保存 Mac 文件时已经恢复，因此该文件名为 `mac-before-recovered.png`，不能作为停帧截图。原始用户截图保存为 `user-reported-stalled.png`；第二次停帧的直接图像证据在本轮 CUA 工具输出中。

源码链路为 ScreenCapturerAndroid → SurfaceTextureHelper → CapturerObserver → VideoSource.adaptOutputFormat → 硬件 H.264 → RTP → Mac video。动态结束后原采集链路不重送静态纹理；适配、编码或发送途中丢掉最终帧时，会等待下一次真实屏幕变化才能补齐。这是本次修复针对的缺口；尚未逐帧证明最终帧具体丢在哪一级，也不将一次 0 fps 本身视为故障，正确静态画面原本允许 0 fps。

## 修改

- 仅新增 Android 静态纹理补帧与版本号、说明文档；Mac 源码保留本轮开始时的内容，没有另加修改。
- 有被控连接且 500 ms 没有采集回调时，调用现有 SurfaceTextureHelper.forceFrame 重送当前纹理。轮询也为 500 ms，实际第一次重试可能在最后回调后 500–1000 ms；到达时间还受编码和发送影响。
- 每次转交 VideoSource 时使用 System.nanoTime 的新时间戳，避免静态纹理原时间戳重复使重试帧被丢弃。短暂 retain/release 转交当前缓冲，不长期持有 OES 纹理，不阻挡后续真实采集。
- 没有连接时不补帧；停止共享时移除回调。动态画面继续沿用原采集、分辨率和帧率上限。
- Android 更新到 0.1.21 / versionCode 22。

机制参考：[WebRTC SurfaceTextureHelper.forceFrame](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/SurfaceTextureHelper.java) 会在没有新帧时重送上一纹理，仍使用 SurfaceTexture 时间戳；[WebRTC 编码入口](https://webrtc.googlesource.com/src/+/refs/heads/main/video/video_stream_encoder.cc) 包含时间戳检查及多种丢帧路径。已用本项目固定依赖 150.7871.01 的 classes.jar 核对 forceFrame API 存在。

## 编译与部署

- Android `:app:assembleRelease :app:lintRelease` 成功，Lint 0 errors / 8 warnings。用原签名覆盖安装到已确认的 Xiaomi 24122RKC7C / Android 16，未清除数据；主界面仍有已保存设备。
- 构建、本地副本和回读安装 APK 的 SHA-256 一致：`fa7de87c98d454d9699dabe6b39c86793f08e6090fc5cd56aa6dc428c14f0972`。
- Android 证书 SHA-256 保持 `6f337db36a294193a835e2eaf04901da77fc7b51f6e5d4982df0573c7c2503fa`；副本 `android-release/0.1.21/BilldDesk-android-0.1.21-arm64.apk`。
- Mac 桌面 Vite 和 arm64 electron-builder 成功，复用固定签名，DMG 校验有效。通过 Cmd+Q 完全退出，确认旧主进程不在后，覆盖 `/Applications/BilldDesk.app` 并由 LaunchServices 正常启动。
- Mac 产物及安装后的 app.asar SHA-256 均为 `d3f6243964ca49c2cb77b23b039d5c45b5a028d7efaa007e9533abf33e04e087`，严格签名校验通过；版本仍为 0.0.1。主进程路径为安装目录，未开启诊断启动参数。
- Git diff --check 通过。构建日志为 `/tmp/billd-final-frame-android-build.log`、`/tmp/billd-final-frame-mac-build.log`、`/tmp/billd-final-frame-mac-package.log`。

## 运行验证

复用既有权限，在手机重新开启整屏共享并确认同一 Mac 请求。实际视频为 1080×2400，直连 UDP，硬件编码器仍为 c2.qti.avc.encoder。使用真实 Mac 客户端点击原 Feed 两件作品和系统返回按钮。

| 条件 | 作品 | 返回次数 | 结果 |
| --- | --- | --- | --- |
| 1080P / 60 fps / 2000 kbps | 咖啡作品 | 2 | 均显示完整 Feed，无持续残影 |
| 1080P / 60 fps / 2000 kbps | 城市作品 | 2 | 均显示完整 Feed，无持续残影 |
| 1080P / 10 fps / 2000 kbps | 咖啡作品 | 1 | 显示完整 Feed，无持续残影 |

各次返回均保存真实客户端截图，另保存部分进入截图；检查对应进入画面为完整详情，返回画面为完整推荐页。10 fps 返回后另保存手机截图，手机与 Mac 都在 Feed。静态采样曾显示解码与呈现约 1–2 fps、码率约 0.02 Mbps，说明当前连接静止后仍有帧到达，不代表需要达到 60 fps。

截图和回读 APK 在 Git 忽略目录 `android-release/final-frame-20261007/`。`comparison-final.png` 对比用户原残影与两次修复后返回，`comparison-repeat.png` 对比第二轮城市和 10 fps 咖啡的进入、返回。界面树保存前剔除含连接凭证的 URL，未保存密码、SDP、ICE 地址或中继凭证。

结束时恢复原 1080P / 60 fps / 2000 kbps，保留正在使用的手机共享连接与设备数据。未证明动画过渡流畅性、物理屏幕帧率、触控端到端延迟或所有网络条件下的恢复时间；未覆盖其他手机、长时间压力、TURN 中继、多连接、共享停止再启、Windows、Linux或网页版。
