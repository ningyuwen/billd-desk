require('./dist/init/alias');
require('./dist/init/initFile');

const { connectMysql, default: sequelize } = require('./dist/config/mysql');

(async () => {
  await connectMysql();
  // Create missing tables; never drop or replace existing tables.
  await sequelize.sync();
  await sequelize.close();
  console.log('BilldDesk database schema initialized');
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
