# BilldDesk 私有部署

2026-10-05 已部署到 `49.51.202.11`，入口为 https://desk.aduning.art。
源码分支为 `p/ningyuwen/deploy-investigation`。服务范围为网页版、API、Socket.IO 信令、MySQL、Redis 和 TURN 中继。

## 服务和配置

| 项目           | 当前配置                                                                     |
| -------------- | ---------------------------------------------------------------------------- |
| 网页           | `/var/www/billd-desk`，由 Nginx 提供 HTTPS                                   |
| API            | `https://desk.aduning.art/api` → `127.0.0.1:4200`                            |
| 信令           | `wss://desk.aduning.art`，实际路径 `/socket.io/`                             |
| 后端           | `/opt/billd-desk/server`，systemd `billd-desk.service`                       |
| Docker Compose | `/opt/billd-desk/ops/compose.yaml`，项目名 `billd-desk`                      |
| MySQL          | `127.0.0.1:13306`，数据库 `billd_desk`，用户 `billddesk`                     |
| Redis          | `127.0.0.1:16379`，数据库 0，启用密码和 AOF                                  |
| TURN           | `turn:49.51.202.11:3478`，中继 UDP `49160–49200`                             |
| Nginx          | `/etc/nginx/sites-available/billd-desk`                                      |
| HTTPS 证书     | `/etc/letsencrypt/live/desk.aduning.art/`，已启用定时续期和续期后 Nginx 重载 |
| 私密配置       | `/etc/billd-desk/compose.env`、`server.env`、`turnserver.conf`               |

腾讯云 DNS：`desk.aduning.art` 的 A 记录指向 `49.51.202.11`，使用直接解析。
腾讯云防火墙已增加 TCP 3478、UDP 3478、UDP 49160–49200；HTTP/HTTPS 使用原有 80/443 规则。
数据库和后端只监听回环地址。原有 `mirage-studio.service` 和 `www.aduning.art` 网站保持正常。

实际密码由部署时随机生成，未写入仓库。`*.example` 是模板，不能直接用于上线。
后端环境文件为 root 所有、权限 600；TURN 文件位于 root 权限 700 的配置目录。
网页 TURN 用户和密码在构建时写入浏览器资源，属于客户端可见的凭证；服务端已配置鉴权、分配数量、带宽限制和内网地址禁用。
当前 TURN 提供 UDP 中继，也接受 TCP 3478 上的分配请求；没有配置 TURNS 或 TCP 中继。

## 查看状态、重启和日志

在服务器执行：

```bash
systemctl status billd-desk --no-pager
sudo journalctl -u billd-desk -n 80 --no-pager
sudo docker compose --env-file /etc/billd-desk/compose.env -f /opt/billd-desk/ops/compose.yaml ps
sudo docker compose --env-file /etc/billd-desk/compose.env -f /opt/billd-desk/ops/compose.yaml logs --tail 80 coturn
sudo nginx -t
sudo systemctl restart billd-desk
```

## 重建网页版

本项目部署时使用 pnpm 9.15.9。`.env.production.local` 已生成并被 Git 忽略。
若重新准备工作目录，复制 `.env.production.example` 为 `.env.production.local`，填入与服务器 TURN 配置相同的凭证。

```bash
npm exec --yes --package=pnpm@9.15.9 -- pnpm install --frozen-lockfile --ignore-scripts --registry=https://registry.npmjs.org
npm exec --yes --package=pnpm@9.15.9 -- pnpm run build:prod
rsync -az --delete --exclude=downloads/ dist/ codex-49:/var/www/billd-desk/
```

`codex-49` 是本机已存在的 SSH 别名：用户 `ubuntu`，目标 `49.51.202.11`。
`--ignore-scripts` 适用于这次网页版构建；桌面端的 Electron 和原生输入控制依赖需要另外安装和验证。
旧的两个 `@nut-tree/*` 依赖已移除，保留源码实际使用的 `@nut-tree-fork/*`，解决旧依赖下载 404。

## 桌面安装包

使用同一份 `.env.production.local`，将私有 API、WSS 和 TURN 凭证内置到客户端。
在 macOS Apple 芯片机器执行：

```bash
npm exec --yes --package=pnpm@9.15.9 -- pnpm exec vite build
CSC_IDENTITY_AUTO_DISCOVERY=false npm exec --yes --package=pnpm@9.15.9 -- pnpm exec electron-builder --mac --arm64 --config electron-builder.private.json5 --publish never
CSC_IDENTITY_AUTO_DISCOVERY=false npm exec --yes --package=pnpm@9.15.9 -- pnpm exec electron-builder --win --x64 --config electron-builder.private.json5 --publish never
```

这组命令不会执行 `standard-version`，不会自动修改版本号或提交代码。
桌面构建会覆盖本地 `dist/`；随后需要更新网页版时，先重新运行 `build:prod`。
桌面资源使用相对路径，以便 `file://` 加载；生产环境不自动打开开发者工具。

产物位于 `electron-release/0.0.1/`：

- `BilldDesk-mac-darwin-0.0.1-arm64-installer.dmg`：macOS Apple 芯片。
- `BilldDesk-win-0.0.1-x64-installer.exe`：Windows 64 位 NSIS 安装程序，可选择安装目录。

`electron-builder.private.json5` 使用依赖自带的 Node-API 原生二进制。
Mac 版通过 `ops/sign-mac-adhoc.cjs` 做本地 ad-hoc 签名，没有 Developer ID 证书和 Apple 公证。
Windows 版没有使用发布者证书签名。首次打开可能出现未知开发者或发布者提示。
Mac 被控端需要授权屏幕录制和输入控制，安装包不会自动授予权限。本机已在用户完成 Touch ID 验证后配置正式应用 `/Applications/BilldDesk.app`；macOS 27.0.1 对应设置项为“录屏与系统录音”和“设备控制和数据访问”，授权后完全退出并重开应用。

安装包的下载目录为 `/var/www/billd-desk/downloads/`；更新网页时保留此目录。

已上传的下载链接：

- [macOS Apple 芯片 DMG](https://desk.aduning.art/downloads/BilldDesk-mac-darwin-0.0.1-arm64-installer.dmg)
- [Windows 64 位 EXE](https://desk.aduning.art/downloads/BilldDesk-win-0.0.1-x64-installer.exe)
- [SHA-256 校验文件](https://desk.aduning.art/downloads/SHA256SUMS.txt)

Mac 镜像完整性和 app 签名校验通过；服务器上的两个安装包已核对 SHA-256，HTTPS 下载地址可访问。

2026-10-05 修复了首页复制按钮：移除对不存在文本框的访问，改为复制当前设备的代码和临时密码，成功写入剪贴板后才提示成功。
本机 Mac 正式版已更新，实测剪贴板内容与界面设备信息一致；Mac 和 Windows 安装包已重新生成，桌面版本仍为 `0.0.1`。
源码修复位置为 `src/views/remote/index.vue`，按钮现在可通过鼠标或键盘触发。

2026-10-05 已完成安卓控制本机 Mac 的实测。桌面端修复 `keyboard.type` 的数组参数传递（展开为独立参数），使安卓文字消息可以正常输入；屏幕源不存在或采集失败时，清除被控状态并提示检查权限、完全退出后重开。
Mac 和 Windows 安装包已再次生成，桌面版本仍为 `0.0.1`；本机正式 Mac 应用包含该修复。Windows 仅完成交叉打包，未在 Windows 实机验证。

### 下载速度排查（2026-10-05）

实例位于美国硅谷，套餐峰值带宽 30Mbps，流量包未耗尽。
当前 Nginx 没有配置 `limit_rate`，服务器 CPU、磁盘没有出现瓶颈。
本机 HTTPS Range 下载实测约 30–45 KB/s；跨境网络线路是主要嫌疑，尚未通过路由和丢包测量确认。
国内用户可使用本地生成的安装包；长期加速可增加国内下载源，或迁移至国内访问线路更好的服务器。

### 安卓客户端

安卓客户端属于独立仓库：https://github.com/galaxy-s10/billd-desk-flutter。
已检查该仓库全部 30 个提交；当前主分支没有应用源码。
删除源码前的提交 `5369c04fb7d99f86415bf948829628456a432b16`（版本 0.25.0）仍缺少 `lib/main.dart` 引用的：

- `lib/stores/app.dart`
- `lib/utils/index.dart`、`lib/utils/request.dart`
- `lib/views/connect/connect.dart`、`lib/views/msg/msg.dart`、`lib/views/setting/setting.dart`

该版本还缺少 Manifest 声明的 `MainActivity` 和 `MyAccessibilityService`。
2024 年提交中的 Flutter 代码为直播应用，不能作为远程桌面客户端使用。
因此新增了独立的原生客户端 `android-client/`，沿用当前私有服务的 API、Socket.IO、WebRTC 和控制消息。
版本 `0.1.4`，包名 `art.aduning.billddesk`，Android 8.0 以上、ARM64，发布签名 APK 约 13 MiB。
支持手机共享屏幕并确认远程控制请求、无障碍触控与文字输入，以及手机连接其他设备。
不包含文件传输、系统声音传输、开机自启或无人值守控制；构建和使用方法见 `android-client/README.md`。
已完成 release 编译和 Lint（0 errors，8 warnings），已通过 USB 安装到 Android 16 手机并连接私有服务器。
网页控制手机已验证连接密码、手机确认、连续画面、点击、英文键盘输入和退格。
安卓控制端先通过合成桌面验证视频、点击/拖动坐标和文字 DataChannel，随后完成真实 Mac 的视频、点击聚焦、英文文字 `Desk` 输入及退格删除验证。手机一次 4 秒采样接收并渲染 77 帧（约 19.2 fps），用户确认看到真实 Mac 桌面。验证工具的手机截图仍为黑色，因此没有用截图替代用户确认和帧接收证据。Windows 控制、中文文字输入及其他手机型号未实机验证。
`0.1.1` 增加连接后自动横屏全屏、横竖屏/窗口切换，以及按服务器保存设备号和加密密码；保存设备可一键重连，支持修改和忘记。通过对端验证并收到视频轨道后保存，保存记录不进行云同步或迁移备份，连接仍需经过私有服务器验证密码；Android Keystore AES-GCM 密钥只在本机使用。
已在原手机覆盖升级，实测横竖屏切换、首页和系统栏隐藏、连接后隐藏键盘；关闭并重新打开 App 后，已保存的 Mac 可一键重连，视频约 19.7 fps。新版 release 编译和 Lint 通过（0 errors，8 warnings），发布签名校验通过。
`0.1.2` 的横屏全屏四个按钮改为右侧竖排，视频区域为 2227×1440，较旧版显示面积增加约 31%；竖屏仍在底部。默认连接电脑使用目标 1080P、30 fps、最高 6 Mbps 和文字内容提示，控制通道打开时发送设置，已实测 Mac 编码参数 1670×1080、30 fps、6000000 bps、text；手机稳定画面采样约 29 fps，部分 4 秒采样渲染丢帧为 0；方向和窗口切换时可短暂波动。修正横屏文字弹窗的键盘全屏覆盖，已验证远程点击聚焦与文字 `Desk` 输入。
`0.1.3` 增加连接中的画质设置：默认目标 1440P、60 fps、最高 12 Mbps，可选最高 2160P、60 fps、30 Mbps，重启后保留选择，弹窗区分目标参数与最近实际接收的尺寸、帧率。桌面采集初始化允许最高 60 fps，并修复重连时码率重复乘 1000 的单位问题；Mac 与 Windows 安装包同步重建，桌面版本仍为 `0.0.1`。Windows 尚未实机验证。2160P 指高度，宽度随屏幕比例变化；网络和编码器会影响实际画质。
新版 release 编译、发布签名和 Lint 通过（0 errors，9 warnings），已覆盖安装到原手机。Mac 录屏与输入控制授权已按新版签名重新登记；采集流程成功，源上限为 3456×2234、60 fps，请求 2160P/60 后实际采集 3342×2160，短时采样约 56 fps。真实手机随后确认接收 3342×2160，接收采样约 57 fps，若干稳定渲染采样约 55.7–56.7 fps；30 fps 和 30 Mbps 上限即时切换通过，不重建连接。码率为上限，连接初期及负载变化会影响实际帧率。
已验证 2160P 下的远程点击和文字输入、横竖屏及窗口切换、重启和覆盖安装后的画质及加密连接信息恢复。画质弹窗持续更新真实接收帧率，保持打开时读数由 51 更新到 58 fps。当前原手机保存目标 2160P/60/20 Mbps；回读已安装 APK 与发布副本 SHA-256 一致。测试结束后断开连接并停止采集。
`0.1.4` 增加双指缩放（100%–400%）和平移，以及“复位”按钮；单指仍控制电脑，点击坐标按缩放和平移反算。实测真实 Mac 的放大后点击和文字输入、最大/最小缩放、取消手势、横竖屏与窗口切换和键盘后的缩放保留；纯双指操作未向电脑发送鼠标事件。release 编译和 Lint 通过（0 errors，10 warnings），同一签名覆盖安装，回读已安装 APK 与发布副本 SHA-256 一致。
下载地址：`https://desk.aduning.art/downloads/BilldDesk-android-0.1.4-arm64.apk`，本地副本位于 `android-release/0.1.4/` 和用户 `Downloads/`。旧版 `0.1.0`、`0.1.1`、`0.1.2`、`0.1.3` 下载仍保留。

## 重建后端

后端源码来自 https://github.com/galaxy-s10/billd-desk-server，固定提交：
`c73983e543341c08ce9e4fb7c52446be50b6c6a2`。
`server.patch` 增加本域名和桌面 `file://` 页面的 CORS、版本查询接口，并支持回环监听及关闭不需要的直播后台任务。

在本仓库根目录执行，以下目录必须尚不存在：

```bash
git clone https://github.com/galaxy-s10/billd-desk-server.git /tmp/billd-desk-server-rebuild
git -C /tmp/billd-desk-server-rebuild checkout c73983e543341c08ce9e4fb7c52446be50b6c6a2
git -C /tmp/billd-desk-server-rebuild apply "$PWD/ops/server.patch"
cp ops/secret.ts.example /tmp/billd-desk-server-rebuild/src/secret/secret.ts
cd /tmp/billd-desk-server-rebuild
npm exec --yes --package=pnpm@9.15.9 -- pnpm install --frozen-lockfile --ignore-scripts --registry=https://registry.npmjs.org
npm exec --yes --package=pnpm@9.15.9 -- pnpm exec tsc -P tsconfig.prod.json
rsync -az --delete dist/ codex-49:/opt/billd-desk/server/dist/
ssh codex-49 'sudo systemctl restart billd-desk'
```

直接执行 `tsc` 并检查退出码；上游 `build` 脚本包含 `|| true`，不能用其退出码判断编译是否成功。
后端运行时通过 `server.env` 读取 MySQL、Redis、JWT 凭证。编译产物没有内嵌这些密码。

首次安装时，将 `init-database.cjs` 复制到后端根目录，加载 `server.env` 后运行；它仅创建缺失表。
本次已初始化完毕。不要执行上游 `mysql:prod`：其初始化链路会删除重建表并注入直播演示数据。

### 初始化桌面版本配置

桌面客户端启动时查询当前版本和更新策略；仅创建空表会出现“找不到版本配置信息！”提示。
安装包上传完成后，执行：

```bash
scp ops/init-version-config.cjs codex-49:/opt/billd-desk/server/
ssh codex-49 'cd /opt/billd-desk/server && sudo node --env-file=/etc/billd-desk/server.env init-version-config.cjs'
```

脚本通过事务补齐缺失的 `desk_version` 和 `desk_config` 记录，重复执行不会覆盖已有记录。
当前桌面版本为 `0.0.1`，最低版本和最新版本均为 `0.0.1`，开启版本检查，不强制更新、不禁用客户端。
版本记录只填写已发布的 macOS Apple 芯片和 Windows 64 位下载地址；安卓 `0.1.0` 使用独立版本号。
之后发布桌面新版本时，应维护版本记录和更新策略，不能只修改安装包文件。

Compose 已锁定本次使用的镜像摘要。MySQL 和 Redis 使用 Docker 持久卷；更新容器时保留持久卷。
重新部署到其他服务器时，需修改域名、IP、TURN 的公网/内网映射，并单独准备上述私密配置和 HTTPS 证书。

## 当前验证范围

- 前端生产构建和后端 TypeScript 编译通过。
- 公网 HTTPS 返回 200，Chrome 页面显示“已准备好连接”，高级设置指向私有 API、WSS、TURN 地址。
- `/api/desk_version/latest` 和 `/find_by_version?version=0.0.1` 返回已发布的桌面版本与下载地址；`/check?version=0.0.1` 返回当前无需更新。
- 公网 WSS 建立 Socket.IO 连接成功。
- 公网 TURN 匿名分配被要求鉴权；正确凭证分配成功，并在配置端口范围内完成双向 UDP 中继；TCP 3478 可连接。
- 后端、容器、Nginx 正常运行，原有网站返回 200。

已生成本部署专用的 Mac Apple 芯片和 Windows 64 位安装包。
Mac 版已启动验证，显示“已准备好连接”，私有服务地址正确，原生键鼠模块在 Electron 内可加载。
Windows 版完成交叉打包和文件检查，尚未在 Windows 实机启动。
安卓控制真实 Mac 的画面、点击和英文文字输入已实测通过；文件传输、其他输入法和 Windows 实机控制仍待验证。
旧客户端只能修改服务 URL，仍使用旧的 TURN 用户和密码；仅更改 URL 不足以接入本部署的中继。
被控电脑需要使用按本仓库环境配置构建的客户端，再授予系统屏幕和输入控制权限。
当前没有配置第三方登录、邮件、支付、对象存储，也未创建后台管理员。

Mac 包目前为临时签名，重新构建会改变代码签名要求。若更新后录屏开关显示开启，但系统日志仍报告旧签名不匹配，需在系统设置移除旧的 BilldDesk 权限记录，再通过添加按钮选择 `/Applications/BilldDesk.app`，重新授予录屏与输入控制权限，并完全退出重开应用。仅重启或切换原开关可能仍沿用旧签名。正式对外分发应使用稳定的开发者签名和公证。
