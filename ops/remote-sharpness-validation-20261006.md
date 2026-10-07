# 手机共享清晰度验证（2026-10-06）

## 修改与结论

当前工作分支 `p/ningyuwen/web-remote-stats`，基线为 `develop` 的 `e2aa6ef9cf0891fcbda51383a3aee0e4b3328d8a`；保留本轮之前的实时统计改动。本轮未提交或推送代码。

手机横屏原画面为 3200×1440，旧代码将长边限制为 1920，实际传输 1920×864，再放大显示在 Mac 窗口中，导致文字模糊。现在画质 P 值对应短边，采集不超过屏幕原生像素：2160P 在验证手机上实际为 3200×1440，1080P 为 2400×1080。原生画面像素约为旧画面的 2.78 倍；这仍是压缩视频，不能视为无损画面。

Android 保留每个连接请求的码率，上限由 8000 提高到 30000 kbps；连接建立时不再重置为 1500 kbps。编码器优先保持分辨率，拥塞时仍可能降低帧率。保留上一轮 `VideoSource.adaptOutputFormat` 的手机共享帧率限制，最高 30 fps。

网页及 Mac 共用的远控页面在 DataChannel 打开后自动发送当前画质参数，无需手动切换一次选项。监听接收端 ID 和通道状态两个独立来源，避免每秒统计更新时重复发送。页面分别显示画质预设和实际尺寸。

## 编译与部署

- Android `:app:assembleRelease :app:lintRelease` 成功，Lint 为 0 errors、8 warnings。版本 0.1.9，versionCode 10。
- 使用已有发布证书覆盖安装，保留应用数据、保存的设备和画质设置。一次 USB 传输中断后改用 `adb install --no-streaming -r` 成功，未卸载应用。
- APK 副本：`android-release/0.1.9/BilldDesk-android-0.1.9-arm64.apk`。回读安装 APK 与副本 SHA-256 均为 `fd3ed820e5bd4951e1f2be4d11ae58825d3feb9f1a56ebe0e7c8e7a5de813932`。
- 发布证书 SHA-256 为 `6f337db36a294193a835e2eaf04901da77fc7b51f6e5d4982df0573c7c2503fa`，与升级前一致。
- Mac Vite 生产构建及 arm64 electron-builder 打包成功，正常退出旧进程、覆盖安装 `/Applications/BilldDesk.app`，经 LaunchServices 启动。严格签名校验通过，继续使用 `BilldDesk Local Code Signing`，未更换证书。
- Mac 安装产物和打包产物的 `app.asar` SHA-256 均为 `bc48d793c330e44552c4002c35e11e7101c14b942df70b46438828738cb4609d`。
- 网页单独构建至 `/tmp/billd-desk-web-sharp-20261006`，部署到服务器 49.51.202.11 的 `/var/www/billd-desk`。先上传并核对 20 个文件，再部署资源、原子替换 index，部署后 20 个文件哈希一致。保留 downloads 和旧哈希资源。
- 网页备份：`/home/ubuntu/billd-desk-sharp-backup-20261006-kevrewv_/web.tar.gz`。nginx、billd-desk 服务均 active，HTTPS index 和入口脚本与本次构建一致。
- 未更新正式 Android 或 Mac 下载包。以上客户端安装是本机和连接手机的验证部署。

## 运行结果

真实 Xiaomi 24122RKC7C（Android 16），物理尺寸 1440×3200。Mac 和网页连接使用已有密码及手机确认流程。

Mac 在首页配置 2160P、30 fps、12000 kbps 后连接手机，未在远控页再切换画质就收到 3200×1440，证明初始参数应用生效。连续滚动时单接收端两次统计如下：

| 指标 | 采样一 | 采样二 |
| --- | --- | --- |
| 实际尺寸 | 3200×1440 | 3200×1440 |
| 解码帧率 | 29.93 fps | 29.89 fps |
| 网络往返 | 100 ms | 7 ms |
| 接收码率 | 14.28 Mbps | 12.23 Mbps |
| 缓冲等待 | 165.59 ms | 139.57 ms |
| 单帧解码 | 7.03 ms | 7.19 ms |
| 采样丢帧 / 丢包 | 0 / 0% | 0 / 0% |
| 路径 | 直连 UDP | 直连 UDP |

启动或缓冲恢复阶段出现过 45.1 fps、356.49ms 缓冲的单秒解码采样；接收端清空积压时可以短时超过采集目标，因此不能将单秒解码统计当作硬性采集帧率上限。码率配置也是上限目标，不代表每秒实际吞吐量恒定。

网页新构建连接后直接收到 3200×1440，自动参数应用通过。切换 1080P 收到 2400×1080，再切回 2160P 收到 3200×1440。网页真实点击手机设置、关闭设置均通过 Android 界面回读确认。

同一连接横屏、竖屏分别收到 3200×1440 和 1440×3200，画面保持比例，切换未断线。临时旋转设置已恢复原值 `accelerometer_rotation=1`、`user_rotation=0`，最终为横屏。

Mac 与网页同时接收时，网页连续滚动两次采样分别为 35.99 / 27 fps、缓冲 31.28 / 927.82ms，尺寸均 3200×1440，丢帧与丢包均为 0。双连接仍会出现明显缓冲波动，不能声称流畅性问题已完全消除。结束额外网页连接后保留用户 Mac 连接，实际尺寸仍为 3200×1440；静止画面采样 1 fps、缓冲 167.68ms。

## 证据与边界

截图和安全截取的界面统计保存在忽略目录 `android-release/remote-sharpness-20261006/`：

- `mac-before.png`、`mac-after.png`：同一 Mac 大窗口修改前后的实际画面。
- `mac-dynamic-samples.txt`：单连接动态统计，含启动阶段和稳定阶段。
- `web-after.jpg`、`web-3200-dynamic.jpg`、`web-dynamic-samples.txt`：网页画面与双连接动态采样。
- `web-portrait.jpg`：竖屏实际画面。

Mac 800×500 主窗口的画质展开、选择、折叠和保存设备连接已实际操作。未新增验证 800×500 远控窗口统计面板滚动、Mac 接收端鼠标控制、Android 控制 Mac 的完整回归、Windows/Linux、多型号手机、跨网与长时间高负载。Android 视频内容“文本”消息未在本轮新增原生实现，不将选中该选项作为 Android 编码文字优化的证据。多接收端共享同一采集源，分辨率切换会影响其他接收端；最后已恢复原生尺寸。
