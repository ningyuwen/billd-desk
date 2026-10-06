const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { signAsync } = require('@electron/osx-sign');

module.exports = async (options) => {
  const configPath = path.join(os.homedir(), '.config/billd-desk/signing/identity.json');
  if (!fs.existsSync(configPath)) {
    throw new Error('Missing persistent Mac identity. Run python3 ops/setup-mac-local-signing.py first.');
  }
  const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
  if (!/^[A-Fa-f0-9]{40}$/.test(config.identity)) {
    throw new Error('Invalid Mac signing certificate fingerprint.');
  }
  const password = fs.readFileSync(config.passwordFile, 'utf8').trim();
  try {
    execFileSync('/usr/bin/security', ['unlock-keychain', '-p', password, config.keychain], { stdio: 'pipe' });
  } catch {
    throw new Error('Cannot unlock the dedicated BilldDesk signing keychain.');
  }
  await signAsync({
    ...options,
    identity: config.identity,
    keychain: config.keychain,
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
};
