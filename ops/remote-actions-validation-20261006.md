# 安卓系统操作验证（2026-10-06）

## 变更

继续使用从 develop 拉出的 `p/ningyuwen/web-remote-stats`，保留此前未提交的统计和清晰度修复。已 fetch 核对 origin/develop，HEAD 和远端基线均为 `e2aa6ef9cf0891fcbda51383a3aee0e4b3328d8a`。本轮未提交、推送或合并。

Android 原有 `androidAction` 协议增加 notifications、quickSettings、dismissShade。均在已允许的当前共享会话中经现有无障碍服务执行，不增加权限。小米控制中心忽略标准 GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE，收起时若当前根窗口属于 SystemUI，改为系统返回；其他情况下使用标准收起动作。原生 Android 控制端加入通知、快捷设置、收起按钮，按钮组宽度按实际个数计算，横屏侧栏和底部滚动布局沿用原流程。

Mac、网页依据 Android 已有 offer 的 platform 字段显示右上角按钮，不对电脑显示安卓操作。控制模式且 DataChannel 打开时可用；观看模式及未连接时禁用。键盘使用 Alt+Shift（Mac Option+Shift），忽略重复 keydown，本地输入框、选择框和可编辑区域不执行快捷动作。

| 操作 | 字母 |
| --- | --- |
| 返回 | B |
| 桌面 | H |
| 多任务 | R |
| 通知栏 | N |
| 快捷设置（状态栏控制中心） | S |
| 收起系统栏 | C |

## 编译与部署

- Android 0.1.10 / versionCode 11：assembleRelease、lintRelease 成功，0 errors、8 warnings。已有发布签名覆盖安装，数据和保存设备保留。USB 曾由 transport 7 重连为 8，一次覆盖安装 EOF 后重试成功，未卸载或清数据。
- APK `android-release/0.1.10/BilldDesk-android-0.1.10-arm64.apk` 与设备回读 APK SHA-256 均为 `c274e4adfe59223dcf64b7e1758e9f0dab34d8c4ded66667bccf4d8c61004f45`。证书 SHA-256 仍为 `6f337db36a294193a835e2eaf04901da77fc7b51f6e5d4982df0573c7c2503fa`。
- Mac Vite 生产构建、arm64 electron-builder 成功，固定签名严格校验通过，正常退出旧程序、覆盖 `/Applications/BilldDesk.app`、LaunchServices 启动。安装与打包 app.asar SHA-256 均为 `540d5150dd02324c36f67d4001cab689ed89012199ccb9c6cd0417e14ed3aefe`。
- 网页独立构建 `/tmp/billd-desk-web-actions-20261006`，部署 49.51.202.11 `/var/www/billd-desk`，20 个文件哈希一致，先资源后原子替换 index，保留 downloads 和旧资源。备份 `/home/ubuntu/billd-desk-actions-backup-20261006-_ipfcq4v/web.tar.gz`。
- HTTPS index 与本次产物 SHA-256 均为 `4f94ae1ce5b8152af3846ae55e62d9f0ce8576c14d6e2438cf972ac1fffc723f`。nginx、billd-desk active。未发布新的正式下载安装包。
- 修改的 TypeScript/Vue 文件 ESLint 通过，Impeccable 检测无发现，git diff --check 通过。

## 实机操作

Xiaomi 24122RKC7C / Android 16，手机原有无障碍授权仍启用；系统共享确认明确选择整个屏幕。复用已有 Mac 和网页身份，经手机确认每个连接。

- 网页按钮：桌面后本地焦点为 com.miui.home Launcher；多任务后本地最近任务界面出现，源画面切为 1440×3200；返回后退出最近任务。通知栏、快捷设置均使本地焦点进入 NotificationShade，布局包含控制中心的移动数据等控件。
- 网页键盘 Alt+Shift+R 打开多任务，Alt+Shift+B 返回；通过真实键盘注入和手机布局确认。网页观看模式下六个按钮均 disabled，恢复后可用。
- Mac 右上角六按钮实际出现。Option+Shift+H 回桌面、Option+Shift+S 打开控制中心、Option+Shift+N 打开通知栏通过本地布局和窗口焦点确认。
- 标准收起在小米控制中心无效，修复后网页按钮与 Mac Option+Shift+C 均将焦点从 NotificationShade 返回 BilldDesk。最终 APK 再次经 Mac S/C 快捷键验证打开和收起成功。
- 最后保留 Mac 2160P / 30 fps / 12 Mbps 连接，画面仍为 3200×1440；网页测试页面已导航并重载至 remote 首页，额外连接结束。

## 系统栏不可见的原因与保护对照

已复现手机本地控制中心展开，但远端视频仍显示底层应用。代码使用 Android 14+ createConfigForDefaultDisplay，系统确认也选择共享整个屏幕，不是单应用共享。系统操作已执行，遗漏发生在共享画面中。

经用户允许，临时对这台手机执行保护开关对照。原值为 secure screen_share_protection_on=1，global disable_screen_share_protections_for_apps_and_notifications=0；只将前者改为 0，后者始终保持 0。同一 Mac 连接中，控制中心和通知栏均进入远程视频，无需重连或改动应用代码。随后将前者恢复为 1，并回读确认两个设置均与原值一致；再次打开控制中心，手机焦点为 NotificationShade，但 Mac 视频重新只显示底层 BilldDesk。此开启、关闭、恢复对照确认该小米系统的屏幕共享保护导致本次系统栏不可见，未验证其他厂商或系统版本。

测试结束后已收起系统栏，保留原 Mac 连接和原系统保护。保护开启时系统栏仍会被隐藏；本次没有永久关闭保护，也没有以应用代码绕过系统保护。对照截图位于忽略目录 `android-release/remote-actions-20261006/protection-comparison/`：xiaomi-protection-off-quick.png 为临时关闭时的控制中心，xiaomi-protection-restored-quick.png 为恢复保护后的画面。通知栏已实际查看确认，不保存通知内容截图。

证据在忽略目录 `android-release/remote-actions-20261006/`：web-actions.jpg、web-notifications.jpg、web-quick-settings.jpg、mac-actions-final.png 等。截图不包含连接密码，系统本地界面未作为对外截图发布。未验证第二台 Android 的原生控制端按钮、其他手机厂商、Windows/Linux 和小窗口工具栏换行；Mac 800×500 主窗口展开/折叠画质、选择参数并连接已实测，远控大窗口及手机方向变化下按钮布局已检查。键盘 B/H/R/N/S/C 中已实测代表性组合，未逐一覆盖全部平台的全部键盘组合。
