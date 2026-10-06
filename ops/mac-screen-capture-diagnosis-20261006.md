# Mac 首次屏幕采集失败原因

2026-10-06，Asia/Shanghai。调查范围为页面优化版本安装后的首次采集失败，以及随后成功验证的权限归属。

## 结论

首次失败时，macOS TCC 仍使用旧版 BilldDesk 的代码签名要求校验新版。新旧包均为 ad-hoc 签名，指定要求绑定各自产物的 cdhash；新版不满足旧版要求，TCC 拒绝录屏请求。系统设置中 BilldDesk 开关显示开启，与运行中新版被拒绝并不矛盾。

后续成功验证采用了终端直接执行应用二进制的启动方式。TCC 日志将其权限归属判定为 `com.openai.codex`，而非 `com.billddesk.app`。这次确实验证了画面和操作链路，但不能证明 BilldDesk 独立启动后的录屏授权已恢复。之前将成功原因归结为完全重启，证据不足。

## 失败证据

17:18:19，应用 PID 89824 的请求 `89824.4`：

```text
service=kTCCServiceScreenCapture
AUTHREQ_SUBJECT: msgID=89824.4, subject=com.billddesk.app
Failed to match existing code requirement for subject com.billddesk.app and service kTCCServiceScreenCapture
  cdhash H"22756306f8308264867db0c522dae68b458cb5e8"
  cdhash H"a13e2ccad327f9b85270b8a369439e72387cb296"
AUTHREQ_RESULT: msgID=89824.4, authValue=0, authReason=5
```

同类签名不匹配还出现在 17:16:14、17:19:09 和 17:19:49。17:19:49 对用户重启后的 PID 11396 再次检查，也有同类不匹配，因此仅重启并未在当时消除问题。

本次调查再次通过 LaunchServices 普通启动应用，17:41:03 的 PID 97537、请求 `97537.3` 重现相同签名不匹配，TCC 返回 `authValue=0, authReason=5`，授权主体明确为 `com.billddesk.app`。安卓等待连接，未收到新画面。这证明问题目前仍存在，不能视为已修复。

对保留的旧包和实际安装的新包执行 `codesign -dvvv -r-`，结果与 TCC 记录精确对应：

| 包 | 签名 | 指定要求 |
| --- | --- | --- |
| `/tmp/billd-desk-ui-20261006/BilldDesk-before.app` | ad-hoc，无 TeamIdentifier | `cdhash H"22756306f8308264867db0c522dae68b458cb5e8"` |
| `/Applications/BilldDesk.app` | ad-hoc，无 TeamIdentifier | `cdhash H"a13e2ccad327f9b85270b8a369439e72387cb296"` |

本项目 `ops/sign-mac-adhoc.cjs` 使用 `identity: '-'`。Apple 的 [TN3127](https://developer.apple.com/documentation/technotes/tn3127-inside-code-signing-requirements) 说明，ad-hoc 的指定要求绑定特定版本代码，隐私授权按代码要求识别应用。

## 成功验证的边界

17:20:53，终端启动的应用 PID 22000：

```text
service=kTCCServiceScreenCapture
AUTHREQ_ATTRIBUTION: requesting=com.billddesk.app, responsible=com.openai.codex
AUTHREQ_SUBJECT: msgID=22000.4, subject=com.openai.codex
AUTHREQ_RESULT: msgID=22000.4, authValue=2, authReason=4
```

这解释了同一新版二进制随后能够完成采集，但独立应用的旧签名授权问题仍不能据此判定已解决。原验证记录已补充这一限制。

## 应用提示与处理方向

`electron-main/index.ts` 的 `getScreenStream` 将屏幕源获取异常及空屏幕源统一映射成「请开启录屏权限」；未区分已授权、签名要求不匹配和其他采集错误，因此该提示过于笼统。上述 TCC 和新旧签名证据提供了此次明确原因。

长期应使用稳定的 Apple Development 或 Developer ID 签名身份，以维持兼容的代码要求；单纯保持应用名和 bundle ID 不足以使 ad-hoc 新版本继承旧授权。本次只调查和记录，没有更换签名或修改系统权限，也没有修改应用代码。

后续授权验证应由 Finder、Dock 或 LaunchServices 正常启动安装后的应用，并确认 TCC 授权主体为 BilldDesk。不要使用终端直接执行二进制的结果证明应用自身授权。

复测后已清除安卓本次待连接状态，并保留普通启动的 Mac 应用；未开关、重置或编辑系统权限记录。

筛选后的原始证据位于 `android-release/ui-layout-20261006/mac-screen-capture-tcc-evidence.log`；完整系统日志仅保存在任务临时目录，未加入仓库。

## 后续修复

本调查记录描述更换签名前的状态。随后已接入固定本地证书，针对 BilldDesk 清除旧录屏记录并由用户重新添加正式应用；正常启动、安卓画面接收及不同代码哈希的同证书覆盖更新均验证通过。详见 `mac-stable-signing-20261006.md`，录屏失败状态已由该结果替代。
