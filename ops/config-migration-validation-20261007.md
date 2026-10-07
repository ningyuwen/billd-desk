# 加密配置迁移验证（2026-10-07）

新增 `config-migration.py`、迁移说明和 Nginx 配置，未改变应用运行代码。
密文迁移 ZIP 已部署到 `https://desk.aduning.art/private/billd-desk-migration.zip`。
用户名为 `billddesk`；下载密码与解密密码分别随机生成，均保存在本机项目外的权限 0600 文件中。
服务器持有 age 密文和下载密码的 SHA-512 crypt 哈希，不持有包的解密密码。

## 本机验证

- 使用 age 1.3.2 导出真实环境配置、原 Mac 签名材料及 Android 发布签名材料，共 9 个配置文件。
- 在独立临时用户目录及模拟项目目录执行 `--dry-run`，未写入配置或创建钥匙串。
- 在该隔离目录执行实际导入：从原 P12 重建专用钥匙串，用原证书指纹签名临时程序并通过严格签名校验。
- 新用户名路径正确写入 Mac `identity.json` 和 Android `storeFile`。
- 重复导入保留原配置备份；未改本机原签名目录或现有 BilldDesk App。
- 非法归档路径、符号链接、重复成员、文件哈希篡改、错误解密密码及密文篡改均被拒绝。
- 注入安装末尾失败后，旧环境配置恢复，新配置和签名目录未残留。
- 本机全局钥匙串搜索列表仍仅包含原 login 钥匙串。
- Python 语法检查及 Git 差异空白检查通过。

## 服务器部署和 HTTP 验证

- ZIP 位于 `/srv/billd-desk-migration/billd-desk-migration.zip`，目录 root:www-data / 0750，文件 root:www-data / 0640。
- 密码哈希位于 `/etc/nginx/billd-desk-migration.htpasswd`，root:www-data / 0640，位于所有网站根目录之外。
- Nginx 仅暴露指定 HTTPS ZIP 地址，要求 Basic Authentication，禁止目录访问和其他私有路径。
- 配置通过 `nginx -t` 后 reload；下载响应带 `Cache-Control: no-store`，并配置请求速率限制。
- 未认证 GET 返回 401，错误密码 GET 返回 401，正确密码 GET 返回 200。
- `/private/`、`/private/billd-desk-config.age`、`/downloads/billd-desk-migration.zip` 返回 404。
- 原网页入口返回 200，原公开 TXT 返回 200 且仍声明 UTF-8。
- 下载 ZIP 与本机 ZIP 字节一致，下载后的 age 包解密、清单及证书校验通过。

本次 ZIP SHA-256：

```text
e99f4ad3d3144b71bc2c95dba5b4358c9735c7e1e521ec35983e5f087c8ce714
```

本次 Nginx 配置备份：`/var/backups/billd-migration-ye5620dq/`。
本机私密凭证及同一 ZIP 校验值位于 `~/.config/billd-desk/migration/download-credentials.txt`，未写入仓库或上传服务器。

## 证明边界

以上实际签名与导入发生在原 Mac 的隔离目录，尚未在另一台真实 Mac 上验证。
SDK 缺失的提示分支已验证；新 Mac 的 SDK 检测、依赖安装、应用构建、系统授权和真实连接仍需在那里完成。
客户端应用代码未修改，本轮没有重新构建、覆盖安装或运行两端 App；服务器验证针对新增下载服务。
无 Git 提交、推送或应用下载渠道更新。
