# Android 与 Mac 页面布局验证

日期：2026-10-06，Asia/Shanghai。本记录对应本次页面修改后的实际构建和覆盖安装。

## 分支与项目规则

- 本地 `develop` 跟踪 `origin/develop`；本次工作分支为 `p/ningyuwen/ui-layout`，从开工时获取的 `develop` 提交 `84495e0` 创建。原有未提交页面改动全部保留。
- 根目录 `AGENTS.md` 已写入持续授权：应用代码修改后自动编译、安装到对应验证端并执行运行验证；后续工作分支从 `develop` 创建，合并目标为 `develop`。
- 验证阶段未创建提交、推送、PR 或执行合并；验证完成后按用户指令提交本地代码。

## 页面修改

- Android 首页以连接设备、常用设备、共享此手机组织操作；画质、服务器和版本信息移入设置弹窗。常用设备的修改密码和忘记操作收进更多菜单。统一颜色、输入框、按钮和文字层级，保留保存设备及连接流程。
- Mac 简化侧栏、首页和设置页。连接优先，画质折叠；共享信息集中展示；服务器详情、项目链接和下载项按需展开；保留连接、共享、断开及已有配置。
- 实机发现 Android 横屏软键盘会遮挡原文字弹窗的发送按钮。补充单行输入、软键盘发送操作和回车发送，发送后关闭弹窗并收起键盘。未改变远程画面缩放、鼠标手势和传输引擎。
- 使用已安装的 Impeccable 进行布局收敛；未安装 Figma。

## 构建与部署

| 端 | 结果 |
| --- | --- |
| Android | JDK 17 执行 `./gradlew :app:assembleRelease :app:lintRelease` 成功。Lint 为 8 条 warning、0 条 error。最终补丁已重新构建、覆盖安装并启动；APK v2 签名验证通过。 |
| Android 设备 | 小米 24122RKC7C，Android 16，ADB `42b1d517`。版本保持 0.1.7，未清空应用数据，保存设备及 2160P / 60 fps / 30 Mbps 设置保留。 |
| Mac | 使用 pnpm 9.15.9 构建 Vue、Electron main 和 preload 成功，arm64 DMG 打包成功。应用签名严格校验通过，DMG 校验通过。版本保持 0.0.1。 |
| Mac 安装 | 退出旧进程，备份旧应用包至 `/tmp/billd-desk-ui-20261006/BilldDesk-before.app`，将本次应用包安装至 `/Applications/BilldDesk.app`，保留原应用数据。安装后 `app.asar` 与构建产物哈希一致。 |

Mac 首次打包遇到本机 Electron 33.2.1 缓存损坏。使用另一份校验可读的同版本缓存解压运行时，以 `--config.electronDist=/tmp/billd-desk-ui-20261006/electron-runtime-valid` 完成打包，并替换损坏的缓存。没有为此修改应用打包配置。既有 Sass 弃用及 bundle 大小提示不影响本次构建。

| 最终产物 | SHA-256 |
| --- | --- |
| 构建及手机回读 APK | `cc08cd97f871320284ce46e06b80b3c01947c4e484cc28882970cef93ad2d5a4` |
| 固定签名版构建及安装后的 Mac app.asar | `6a50b7118ef4e33b821c4d696b63b3e1e4e26a0b54b6b323bc7b8ddbad7827ce` |
| 固定签名版 Mac DMG | `1ab69ce9e66e57665538ceba91207889c32fc090d86e69a0358f46286dfcacc5` |

## 运行验证

| 场景 | 结果和证明边界 |
| --- | --- |
| Android 首页 | 实机检查竖屏、横屏、滚动；设置、连接、常用设备及共享操作可达。密码默认遮蔽。 |
| Android 设置 | 设置入口、画质弹窗、服务器弹窗及键盘显示检查通过。打开常用设备更多菜单并取消，未删除设备或更换密码。 |
| Mac 默认窗口 | 实际安装包的 800×500 首页、设置页、最近连接空状态检查通过；密码隐藏、画质、服务器详情及项目与下载展开可用，滚动可达下方内容。主窗口在原 Electron 配置中固定为 800×500，因此未改变窗口尺寸约束。 |
| 保存设备连接 | 安卓常用设备一键连接 Mac 成功。后续系统日志调查确认：首次失败由新版 ad-hoc 签名不满足旧录屏授权的代码要求引起。成功验证时应用由终端直接执行，TCC 实际授权主体为 `com.openai.codex`，因此该成功不证明 BilldDesk 独立启动后的授权已恢复。详见 `mac-screen-capture-diagnosis-20261006.md`。 |
| 画面接收 | 安卓实际收到 3342×2160 画面，WebRTC 日志有首帧、分辨率及持续接收/渲染记录，画质弹窗显示实际帧率。请求 60 fps 不等于实际达到 60 fps；本次没有进行性能基准测试。 |
| 远程点击 | 通过安卓点击 Mac 的设置侧栏，Mac 实际切换到设置页。 |
| 远程文字 | 最终 APK 下，分别通过回车和软键盘蓝色「发送」按钮发送 `Desk`，Mac 输入框实际出现 `Desk`，弹窗自动关闭。验证文字随后清除。 |
| 显示与断开 | 检查横屏全屏、竖屏全屏及窗口模式切换。竖屏底部工具栏可横向滚动到完整断开按钮。断开后两端恢复待连接状态；安卓自动旋转设置恢复为验证前值。 |
| 日志 | 最终安卓进程的 AndroidRuntime 中未发现 FATAL EXCEPTION；终端启动的 Mac 进程没有 `获取屏幕失败`，但有 TURN 403 候选连接裁剪及主动断开对应的 DataChannel 日志，不能概括为完全无异常。首次失败的 TCC 拒绝另有系统日志证据。 |

测试结束后退出并正常重启 Mac 客户端，停止测试采集，保留两端新版应用。未验证 Windows、Linux、浏览器端、Android 被控链路和长时间稳定性；本记录不代表正式渠道发布。

后续权限调查补充：17:41 普通启动复测仍因新旧 cdhash 不匹配被 TCC 拒绝。当时独立启动的 Mac 被控录屏问题尚未修复；此前成功的画面及操作验证仅适用于终端启动、授权归属 Codex 的进程。固定签名后的修复及复验结果见文末。

## 证据与产物

截图与筛选后的构建、渲染日志保存于忽略目录 `android-release/ui-layout-20261006/`，截图密码已遮蔽。远程文字键盘截图含用户桌面，留在任务临时目录，未纳入项目证据目录。

- 最终安卓 APK：`android-release/ui-layout-20261006/BilldDesk-android-ui-layout-0.1.7.apk`
- Mac DMG：`electron-release/0.0.1/BilldDesk-mac-darwin-0.0.1-arm64-installer.dmg`
- 首页：`android-home-final.png`、`mac-home.png`
- 设置与折叠：`android-settings.png`、`android-server-keyboard.png`、`mac-settings.png`、`mac-quality-scrolled.png`、`mac-settings-details.png`
- 连接和输入：`mac-active.png`、`mac-remote-input-final.png`、`mac-disconnected.png`

Vue 页面静态检查、Android release Lint 及最终 `git diff --check` 均通过。截图中继地址和设备代码仅作本地验证展示，没有保存连接密码或密钥。

## 固定签名后的录屏复验

2026-10-06 已创建并接入固定本地证书。旧授权仅切换开关没有更新签名记录，针对 BilldDesk 重置录屏决定后，由用户重新添加正式安装应用。正常启动、安卓实际画面、不同代码哈希的同证书覆盖更新和恢复发布产物后的重连均通过；TCC 授权主体为 BilldDesk。此次结果替代前述录屏仍失败的状态，详见 `ops/mac-stable-signing-20261006.md`。本轮没有重测新签名下的输入控制。
