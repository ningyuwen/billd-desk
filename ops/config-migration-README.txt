BilldDesk 加密配置迁移工具

一、下载与密码

下载地址（必须使用 HTTPS）：
  https://desk.aduning.art/private/billd-desk-migration.zip
下载用户名：billddesk

打开地址后，输入“下载访问密码”。下载的 ZIP 包包含：
  billd-desk-config.age               加密配置，包含真实凭证及原签名材料
  config-migration.py                 本地导入工具，不包含秘密
  config-migration-README.txt         本说明
  mac-development-migration.txt       完整环境迁移清单

“下载访问密码”和“迁移包解密密码”不同。
两个密码及本次 ZIP 的 SHA-256 保存在原 Mac 的：
  ~/.config/billd-desk/migration/download-credentials.txt
请通过自己的私密方式带到新 Mac，不上传此密码文件。
服务器仅保存配置密文和下载密码哈希，不保存解密密码。

下载后，先将 ZIP 的 SHA-256 与私密密码文件中的参考值对比，再解压：
  shasum -a 256 ~/Downloads/billd-desk-migration.zip

二、新 Mac 导入

1. 获取项目源码。当前主开发分支是 develop：
     git clone --branch develop git@github.com:ningyuwen/billd-desk.git
   若已有工作目录，保留已有工作，不直接清空或强制重置。

2. 确保 python3 和 age 可用：
     python3 --version
     age --version
   age 可通过 Homebrew 安装：brew install age。
   如果没有 Python 3/Command Line Tools，先按本机环境准备。

3. 在解压后的迁移工具目录，先预检查（将路径替换为新 Mac 项目路径）：
     python3 config-migration.py import --archive billd-desk-config.age --project /新Mac项目绝对路径/billd-desk --dry-run
   提示时输入“迁移包解密密码”，终端不回显密码。
   预检查只读取和验证，不修改配置或系统钥匙串。

4. 确认目标路径后执行正式导入：
     python3 config-migration.py import --archive billd-desk-config.age --project /新Mac项目绝对路径/billd-desk
   若 Android SDK 位于非默认路径，加参数 --sdk /实际SDK绝对路径。

工具会：
  - 验证文件清单、SHA-256 和原 Mac 签名证书指纹。
  - 将原有被覆盖文件备份到用户私有目录。
  - 写入 .env.production.local 和 .env.development.local。
  - 从原 P12 恢复专用 Mac 签名钥匙串，保留原证书及私钥。
  - 在临时验证程序上实际签名并校验，不修改现有 BilldDesk App。
  - 更新 identity.json 中的用户绝对路径。
  - 恢复 Android JKS、密码备份和 signing.local.properties，调整 storeFile。
  - 检测 Android SDK，生成对应 local.properties。
  - 使用目录 0700、配置文件 0600，不显示密码，不修改系统信任设置。
  - 操作失败时恢复本轮已替换的配置。

如已有不同 Mac 签名身份，工具会拒绝覆盖；请先单独处理冲突。
备份位置：~/.config/billd-desk/migration/backups/<本轮编号>/
restore-map.json 记录原配置的备份位置；signing/ 保留原签名目录副本。
不使用此工具迁移 SSH 私钥、数据库密钥、系统权限或设备历史数据。

三、导入后的步骤

导入配置后仍需按 mac-development-migration.txt：
  - 安装 Node.js、pnpm 9.15.9；Android 开发还需要 JDK 和 Android SDK。
  - 按锁文件安装项目依赖，桌面端不加 --ignore-scripts。
  - 编译对应端，核对签名，覆盖安装到验证设备。
  - 在新 Mac 授予 BilldDesk 录屏和输入控制权限。
  - 在手机确认新 Mac 的 USB 调试授权。
  - 验证真实连接、画面和远程操作。

四、原 Mac 更新迁移包

从项目根目录执行：
  python3 ops/config-migration.py export --output /私有目录/新配置包.age
输入并确认独立强密码（至少 20 个字符），脚本仅输出密文路径及哈希。
导出默认包括 Mac 和 Android；仅需要 Mac 时可加 --mac-only。
--passphrase-file 可从权限 0600 的本地文件读取解密密码，避免自动化时暴露密码。
已存在的输出不会覆盖；源码、密码和明文配置都不应上传公开下载目录。

本次脚本开发不改变应用运行逻辑，也不提交、推送或发布应用安装包。
