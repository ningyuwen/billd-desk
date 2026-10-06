const { signAsync } = require('@electron/osx-sign');

module.exports = (options) =>
  signAsync({
    ...options,
    identity: '-',
    identityValidation: false,
    preAutoEntitlements: false,
    preEmbedProvisioningProfile: false,
    gatekeeperAssess: false,
    optionsForFile: (file) => ({
      ...options.optionsForFile(file),
      hardenedRuntime: false,
      timestamp: 'none',
    }),
  });
