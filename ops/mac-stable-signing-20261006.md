# Mac 固定签名验证（2026-10-06）

本机没有可用 Apple 开发者签名身份，本次创建固定的本地自签名证书并接入私有 Mac 打包流程。用途为本机开发与验证，不包含 Developer ID 或 Apple 公证。

## 身份与保存

- 证书名称：`BilldDesk Local Code Signing`，RSA 3072，SHA-256，只有代码签名用途，有效期十年。
- 证书 SHA-1 指纹：`8D167221FED1C3D3B775D052BD7671E79FC348A6`。
- 身份目录：`~/.config/billd-desk/signing/`，目录权限 0700，文件权限 0600。专用钥匙串与加密 P12 备份均在项目外；未改变系统信任设置或全局钥匙串搜索列表。
- `ops/setup-mac-local-signing.py` 只创建一次；重复执行已确认保留证书不变。`ops/sign-mac-local.cjs` 明确指定证书指纹和专用钥匙串，不自动生成新身份或回退临时签名。
- 原始未加密 PEM 私钥仅在临时目录使用，导入钥匙串后随临时目录移除。密码与备份同属私有目录，须安全备份整个目录，不提交或随产物分发。

## 构建与签名证明

桌面 Vite 构建及 arm64 DMG 打包通过，应用 `codesign --verify --deep --strict` 和 DMG `hdiutil verify` 均通过。

身份要求为：

```text
identifier "com.billddesk.app" and certificate root = H"8d167221fed1c3d3b775d052bd7671e79fc348a6"
```

另外复制应用，修改验证副本的封装资源并重新签名，两个不同代码哈希均满足上述同一要求：

| 应用 | CDHash |
| --- | --- |
| 实际安装产物 | `44735caeb1df96d33bc5797304396b40d1324c41` |
| 修改资源的验证副本 | `b3254ed6aa88a371296487be02dbc43ee55c6cc3` |

重新授权后，将修改资源的验证副本临时覆盖安装到同一路径，确认代码哈希变为 `b3254ed6aa88a371296487be02dbc43ee55c6cc3`；通过 LaunchServices 正常启动并从安卓连接。18:13:49 请求 `59741.3` 的主体为 `com.billddesk.app`，结果 `authValue=2, authReason=4`，安卓持续收到并渲染画面，未要求重新授权。随后恢复原发布产物，验证用额外资源未保留在最终安装应用中。

## 部署与录屏验证边界

已保留旧应用至 `/tmp/billd-desk-ui-20261006/stable-signing/BilldDesk-before-stable-sign.app`，覆盖安装 `/Applications/BilldDesk.app`，未清空应用数据，并通过 LaunchServices 正常启动。界面及服务器连接正常。

17:48:30 从安卓发起真实连接，TCC 请求 `23656.4` 的主体是 `com.billddesk.app`。系统仍用旧授权的 `cdhash H"22756306f8308264867db0c522dae68b458cb5e8"` 匹配新证书身份并拒绝录屏，说明需要用户为新身份重新授权一次。此时未收到安卓画面，不能声称录屏已恢复。

用户确认关闭再开启权限后，于 18:07:39 重试：请求 `36331.4` 的主体仍为 `com.billddesk.app`，系统仍按旧代码哈希匹配，结果 `authValue=0, authReason=5`；安卓没有收到画面。开关操作没有迁移旧身份记录。

随后通过系统自带 `tccutil reset ScreenCapture com.billddesk.app` 只重置 BilldDesk 的录屏决定，命令成功。其他应用、其他权限未重置，未直接写权限数据库。重新启动和连接后，旧代码哈希不匹配错误不再出现，但新身份尚未授权，录屏仍被拒绝。

已在系统设置的录屏页面点击添加并选中 `/Applications/BilldDesk.app`（创建/修改时间为本次 17:46），由用户点击“打开”。用户没有看到再次认证弹窗；回读系统设置确认 BilldDesk 开关已开启。

退出并通过 LaunchServices 重开原发布应用后，18:12:55 录屏请求 `57771.3` 的主体为 `com.billddesk.app`，结果 `authValue=2, authReason=4`。安卓接收和渲染真实画面：4 秒采样 224/224 帧，56 fps，0 丢帧。随后上述不同代码哈希覆盖更新的录屏验证也通过；该轮静态桌面采样 32/32 帧，8 fps，0 丢帧，不能将采样值视作保证帧率。

恢复原发布应用后，18:14:23 请求 `61057.3` 再次以 BilldDesk 自身身份获得录屏授权，安卓 4 秒采样接收并渲染 186/186 帧。最终安装应用的 `app.asar` 与发布产物一致，CDHash 恢复为 `44735caeb1df96d33bc5797304396b40d1324c41`；验证连接已主动断开。

上述成功不依赖终端继承其他应用权限。麦克风与输入监听权限未授予；本轮只确认录屏及身份兼容性，不宣称这些权限或远程输入已验证。

身份兼容证据和本次 TCC 请求摘录保存于 Git 忽略目录 `android-release/ui-layout-20261006/mac-stable-sign-identity.txt`、`mac-stable-sign-tcc.log`。构建和打包日志位于 `/tmp/billd-desk-ui-20261006/mac-build-stable-sign.log`、`mac-package-stable-sign.log`。

依据：Apple [TN2206](https://developer.apple.com/library/archive/technotes/tn2206/) 关于自签名身份及指定要求的说明。公开分发仍须使用 Apple Developer ID 与公证。
