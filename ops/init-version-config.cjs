// Copy this file to the backend root and load server.env before running it.
require('./dist/init/alias');

const mysql = require('mysql2/promise');
const { MYSQL_CONFIG } = require('./dist/secret/secret');

const version = '0.0.1';
const downloadBase = 'https://desk.aduning.art/downloads';

(async () => {
  const db = await mysql.createConnection({
    host: MYSQL_CONFIG.host,
    port: MYSQL_CONFIG.port,
    user: MYSQL_CONFIG.username,
    password: MYSQL_CONFIG.password,
    database: MYSQL_CONFIG.database,
    charset: 'utf8mb4',
    timezone: '+08:00',
  });
  try {
    await db.beginTransaction();
    // Only add missing records; preserve existing release and update policies.
    const [release] = await db.execute(
      `INSERT INTO desk_version
        (version, show_version, \`force\`, disable, update_content, update_date,
         download_macos_dmg, download_windows_64_exe, remark, created_at, updated_at)
       SELECT ?, ?, 2, 2, ?, ?, ?, ?, '私有部署', NOW(), NOW()
       WHERE NOT EXISTS (
         SELECT 1 FROM desk_version WHERE version = ? AND deleted_at IS NULL
       )`,
      [
        version,
        version,
        '私有服务器桌面客户端（macOS Apple 芯片 / Windows 64 位）',
        '2026-10-05',
        `${downloadBase}/BilldDesk-mac-darwin-${version}-arm64-installer.dmg`,
        `${downloadBase}/BilldDesk-win-${version}-x64-installer.exe`,
        version,
      ]
    );
    const [config] = await db.execute(
      `INSERT INTO desk_config
        (type, field_a, field_b, field_c, remark, created_at, updated_at)
       SELECT 0, ?, ?, '1', '桌面版本检查配置', NOW(), NOW()
       WHERE NOT EXISTS (
         SELECT 1 FROM desk_config WHERE type = 0 AND deleted_at IS NULL
       )`,
      [version, version]
    );
    await db.commit();
    console.log(
      JSON.stringify({
        version,
        releaseInserted: release.affectedRows,
        configInserted: config.affectedRows,
      })
    );
  } catch (error) {
    await db.rollback();
    throw error;
  } finally {
    await db.end();
  }
})().catch((error) => {
  console.error(
    'Version configuration initialization failed:',
    error.code || error.name
  );
  process.exitCode = 1;
});
