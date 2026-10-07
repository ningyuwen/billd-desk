#!/usr/bin/env python3
"""Export and restore BilldDesk private configuration using passphrase-encrypted age."""
import argparse
import getpass
import hashlib
import io
import json
import os
from pathlib import Path
import pty
import re
import select
import shlex
import shutil
import signal
import subprocess
import sys
import tarfile
import tempfile
import termios
import time
import uuid

EXPECTED_IDENTITY = '8D167221FED1C3D3B775D052BD7671E79FC348A6'
MAC_FILES = ('identity.json', 'certificate.pem', 'identity.p12',
             'keychain-password', 'billd-desk.keychain-db')
REQUIRED = {'project/.env.production.local'} | {'mac/' + n for n in MAC_FILES}
ANDROID_FILES = {'android/signing.local.properties', 'android/android-release.jks'}
OPTIONAL = ANDROID_FILES | {'android/android-keystore-password'}
MAX_BYTES = 16 * 1024 * 1024


def fail(message):
    raise ValueError(message)


def private_write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if path.is_symlink():
        fail('拒绝写入符号链接：' + str(path))
    with path.open('xb') as f:
        os.chmod(path, 0o600)
        f.write(data)


def password(args, confirm=False):
    if args.passphrase_file:
        path = args.passphrase_file.expanduser()
        if path.stat().st_mode & 0o077:
            fail('解密密码文件应仅当前用户可读（chmod 600）。')
        value = path.read_text().strip()
    else:
        value = getpass.getpass('迁移包解密密码（不会上传服务器）：')
        if confirm and value != getpass.getpass('再次输入解密密码：'):
            fail('两次密码不一致。')
    if len(value) < 20 or '\n' in value or '\r' in value:
        fail('请使用至少 20 个字符的独立强密码。')
    return value


def run_age(arguments, secret):
    """Feed age's documented interactive prompts through a private, non-echoing TTY.

    Never pass the secret in argv/environment or expose the TTY transcript.
    """
    binary = shutil.which('age')
    if not binary:
        fail('缺少 age，请先执行 brew install age。')
    pid, fd = pty.fork()
    if pid == 0:
        attrs = termios.tcgetattr(0)
        attrs[3] &= ~termios.ECHO
        termios.tcsetattr(0, termios.TCSANOW, attrs)
        os.execv(binary, [binary] + arguments)
    transcript = b''
    prompts = 0
    deadline = time.monotonic() + 120
    finished = False
    try:
        while time.monotonic() < deadline:
            ready, _, _ = select.select([fd], [], [], 0.2)
            if ready:
                try:
                    chunk = os.read(fd, 4096)
                except OSError:
                    chunk = b''
                if not chunk:
                    break
                transcript = (transcript + chunk)[-8192:]
                match = re.search(rb'(?:Enter|Confirm) passphrase[^\r\n]*:', transcript)
                if match:
                    prompts += 1
                    if prompts > 2:
                        fail('加密工具请求了意外的额外输入。')
                    os.write(fd, secret.encode('utf-8') + b'\n')
                    transcript = transcript[match.end():]
        else:
            fail('加密工具运行超时。')
        _, status = os.waitpid(pid, 0)
        finished = True
        if not os.WIFEXITED(status) or os.WEXITSTATUS(status) != 0:
            fail('age 操作失败：请检查密码、文件完整性及 age 版本。')
    finally:
        os.close(fd)
        if not finished:
            try:
                os.kill(pid, signal.SIGKILL)
                os.waitpid(pid, 0)
            except ProcessLookupError:
                pass


def certificate_identity(data):
    result = subprocess.run(['openssl', 'x509', '-noout', '-fingerprint', '-sha1'],
                            input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        fail('无法读取 Mac 签名证书。')
    return result.stdout.decode().strip().split('=')[-1].replace(':', '').upper()


def validate_files(files):
    if not REQUIRED <= files.keys() or not files.keys() <= REQUIRED | OPTIONAL:
        fail('迁移包文件清单不完整或包含非允许文件。')
    if files.keys() & OPTIONAL and not ANDROID_FILES <= files.keys():
        fail('Android 签名配置和 JKS 必须同时提供。')
    identity = json.loads(files['mac/identity.json'])
    if not isinstance(identity, dict) or identity.get('identity') != EXPECTED_IDENTITY:
        fail('迁移包不是本项目的原 Mac 签名身份。')
    if certificate_identity(files['mac/certificate.pem']) != EXPECTED_IDENTITY:
        fail('证书与原签名指纹不一致。')
    if not files['mac/keychain-password'].strip():
        fail('迁移包缺少钥匙串密码。')
    for name in ANDROID_FILES & files.keys():
        if not files[name]:
            fail('Android 签名材料为空。')
    if ANDROID_FILES <= files.keys():
        text = files['android/signing.local.properties'].decode('utf-8')
        for field in ('storeFile', 'storePassword', 'keyAlias', 'keyPassword'):
            if not re.search(r'^' + field + r'\s*=\s*\S', text, re.M):
                fail('Android 发布签名配置缺少必要字段。')


def export_bundle(args):
    project = args.project.expanduser().resolve()
    home = args.home.expanduser().resolve()
    files = {'project/.env.production.local': (project / '.env.production.local').read_bytes()}
    for name in MAC_FILES:
        files['mac/' + name] = (home / '.config/billd-desk/signing' / name).read_bytes()
    if not args.mac_only:
        prop = project / 'android-client/signing.local.properties'
        text = prop.read_text()
        match = re.search(r'^storeFile\s*=\s*(.+)$', text, re.M)
        if not match:
            fail('未找到 Android storeFile。')
        key = Path(match.group(1).strip()).expanduser()
        if not key.is_absolute():
            key = project / 'android-client/app' / key
        files['android/signing.local.properties'] = prop.read_bytes()
        files['android/android-release.jks'] = key.read_bytes()
        backup = home / '.codex/private/billd-desk/android-keystore-password'
        if backup.is_file():
            files['android/android-keystore-password'] = backup.read_bytes()
    validate_files(files)
    if sum(map(len, files.values())) > MAX_BYTES:
        fail('配置文件超过大小限制。')
    secret = password(args, confirm=True)
    output = args.output.expanduser().absolute()
    if output.exists():
        fail('输出文件已存在，请使用新文件名。')
    output.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    manifest = {'schema': 1, 'identity': EXPECTED_IDENTITY,
                'files': {n: hashlib.sha256(d).hexdigest() for n, d in files.items()}}
    files['manifest.json'] = (json.dumps(manifest, indent=2) + '\n').encode()
    with tempfile.TemporaryDirectory(prefix='billd-export-') as temporary:
        archive = Path(temporary) / 'config.tar.gz'
        with tarfile.open(archive, 'w:gz') as tar:
            for name, data in sorted(files.items()):
                info = tarfile.TarInfo(name)
                info.size, info.mode = len(data), 0o600
                tar.addfile(info, io.BytesIO(data))
        run_age(['-p', '-o', str(Path(temporary) / 'config.age'), str(archive)], secret)
        private_write(output, (Path(temporary) / 'config.age').read_bytes())
    print('加密迁移包已生成：' + str(output))
    print('SHA-256：' + hashlib.sha256(output.read_bytes()).hexdigest())


def read_bundle(archive):
    files = {}
    total = 0
    with tarfile.open(archive, 'r:gz') as tar:
        for member in tar:
            if (member.name not in REQUIRED | OPTIONAL | {'manifest.json'} or
                    member.name in files or not member.isfile()):
                fail('拒绝非法路径、链接、重复或非允许的归档成员。')
            total += member.size
            if member.size < 0 or total > MAX_BYTES:
                fail('迁移包解压内容超过大小限制。')
            files[member.name] = tar.extractfile(member).read()
    manifest = json.loads(files.pop('manifest.json', b'{}'))
    if (not isinstance(manifest, dict) or manifest.get('schema') != 1 or manifest.get('identity') != EXPECTED_IDENTITY or
            manifest.get('files') != {n: hashlib.sha256(d).hexdigest() for n, d in files.items()}):
        fail('迁移包清单或文件哈希不匹配。')
    validate_files(files)
    return files


def check_destination(path):
    for ancestor in [path] + list(path.parents):
        if ancestor.is_symlink():
            fail('目标路径含符号链接，未写入：' + str(path))
    if '\n' in str(path) or '\r' in str(path):
        fail('目标路径含非法换行。')


def command(arguments):
    try:
        result = subprocess.run(arguments, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60)
    except subprocess.TimeoutExpired:
        fail('签名验证命令超时；未显示可能包含凭证的命令参数。')
    if result.returncode:
        fail('签名验证命令失败：' + arguments[0] + ' ' + arguments[1] +
             '。未显示可能包含凭证的诊断输出。')
    return result.stdout.decode('utf-8', errors='replace')


def restore_keychain(root, files):
    """Recreate the dedicated keychain from the original P12, not a new certificate."""
    secret = files['mac/keychain-password'].decode().strip()
    keychain = root / 'billd-desk.keychain-db'
    original_search = shlex.split(command(['security', 'list-keychains', '-d', 'user']))
    try:
        command(['security', 'create-keychain', '-p', secret, str(keychain)])
        command(['security', 'set-keychain-settings', '-lut', '21600', str(keychain)])
        command(['security', 'unlock-keychain', '-p', secret, str(keychain)])
        command(['security', 'import', str(root / 'identity.p12'), '-k', str(keychain),
                 '-P', secret, '-T', '/usr/bin/codesign'])
        command(['security', 'set-key-partition-list', '-S', 'apple-tool:,apple:,codesign:',
                 '-s', '-k', secret, str(keychain)])
        # Prove that both the certificate AND its private key can sign on this Mac.
        with tempfile.TemporaryDirectory(prefix='billd-sign-check-') as temporary:
            target = Path(temporary) / 'sign-check'
            shutil.copyfile('/usr/bin/true', target)
            command(['codesign', '--force', '--sign', EXPECTED_IDENTITY,
                     '--keychain', str(keychain), str(target)])
            command(['codesign', '--verify', '--strict', str(target)])
    finally:
        current_search = shlex.split(command(['security', 'list-keychains', '-d', 'user']))
        if current_search != original_search:
            command(['security', 'list-keychains', '-d', 'user', '-s'] + original_search)


def import_bundle(args):
    if sys.platform != 'darwin':
        fail('Mac 配置导入必须在 macOS 上执行。')
    project = args.project.expanduser().resolve()
    home_input = args.home.expanduser().absolute()
    project_input = args.project.expanduser().absolute()
    check_destination(home_input)
    check_destination(project_input)
    home = home_input.resolve()
    if not (project / 'package.json').is_file() or not (project / 'android-client').is_dir():
        fail('--project 必须指向 BilldDesk 源码目录。')
    archive = args.archive.expanduser().resolve()
    if archive.stat().st_size > MAX_BYTES:
        fail('加密包超过大小限制。')
    secret = password(args)
    with tempfile.TemporaryDirectory(prefix='billd-import-') as temporary:
        decrypted = Path(temporary) / 'config.tar.gz'
        run_age(['-d', '-o', str(decrypted), str(archive)], secret)
        files = read_bundle(decrypted)
    signing = home / '.config/billd-desk/signing'
    old_identity = signing / 'identity.json'
    if old_identity.is_file():
        if json.loads(old_identity.read_text()).get('identity', '').upper() != EXPECTED_IDENTITY:
            fail('新电脑已有不同的 Mac 签名身份，未覆盖；请先单独处理。')
    targets = {project / '.env.production.local': files['project/.env.production.local'],
               project / '.env.development.local': files['project/.env.production.local']}
    if ANDROID_FILES <= files.keys():
        key_root = home / '.codex/private/billd-desk'
        text = files['android/signing.local.properties'].decode('utf-8')
        text = re.sub(r'^storeFile\s*=.*$',
                      lambda _: 'storeFile=' + str(key_root / 'android-release.jks'), text, flags=re.M)
        targets[project / 'android-client/signing.local.properties'] = text.encode()
        targets[key_root / 'android-release.jks'] = files['android/android-release.jks']
        if 'android/android-keystore-password' in files:
            targets[key_root / 'android-keystore-password'] = files['android/android-keystore-password']
        sdk = (args.sdk.expanduser() if args.sdk else home / 'Library/Android/sdk').resolve()
        if sdk.is_dir():
            targets[project / 'android-client/local.properties'] = ('sdk.dir=' + str(sdk) + '\n').encode()
        else:
            print('未检测到 Android SDK，导入后需安装并配置 android-client/local.properties。')
    for path in list(targets) + [signing]:
        check_destination(path)
        if path in targets and path.exists() and not path.is_file():
            fail('文件目标被目录占用：' + str(path))
    print('已验证迁移包，Mac 原证书指纹：' + EXPECTED_IDENTITY)
    for path in sorted(map(str, targets)):
        print('配置目标：' + path)
    print('签名目标：' + str(signing))
    if args.dry_run:
        print('预检查完成，未修改目标文件或钥匙串。')
        return
    # Back up every overwritten file BEFORE any installation. Each run gets its
    # own private backup; restoration data never enters the repository or server.
    backup = home / '.config/billd-desk/migration/backups' / uuid.uuid4().hex
    backup.mkdir(parents=True, mode=0o700)
    backup.chmod(0o700)
    existing = {}
    for index, path in enumerate(targets):
        if path.exists():
            saved = backup / ('file-' + str(index))
            private_write(saved, path.read_bytes())
            existing[path] = saved
    had_signing = signing.exists()
    if had_signing:
        shutil.copytree(signing, backup / 'signing', symlinks=True)
    private_write(backup / 'restore-map.json', json.dumps(
        {str(p): str(saved) for p, saved in existing.items()}, indent=2).encode())
    signing.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    staging = Path(tempfile.mkdtemp(prefix='.signing-import-', dir=signing.parent))
    installed_signing = False
    changed = []
    try:
        for name in MAC_FILES:
            if name not in ('billd-desk.keychain-db', 'identity.json'):
                private_write(staging / name, files['mac/' + name])
        restore_keychain(staging, files)
        identity = {'identity': EXPECTED_IDENTITY,
                    'keychain': str(signing / 'billd-desk.keychain-db'),
                    'passwordFile': str(signing / 'keychain-password')}
        private_write(staging / 'identity.json', (json.dumps(identity, indent=2) + '\n').encode())
        if had_signing:
            signing.rename(backup / 'previous-signing')
        staging.rename(signing)
        installed_signing = True
        for path, data in targets.items():
            path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            fd, temporary = tempfile.mkstemp(prefix='.billd-import-', dir=path.parent)
            try:
                with os.fdopen(fd, 'wb') as f:
                    f.write(data)
                os.replace(temporary, path)
                changed.append(path)
            finally:
                if os.path.exists(temporary):
                    os.unlink(temporary)
        # Ensure the relocated keychain is still accessible at its final path.
        command(['security', 'unlock-keychain', '-p', files['mac/keychain-password'].decode().strip(),
                 str(signing / 'billd-desk.keychain-db')])
    except Exception:
        for path in reversed(changed):
            if path in existing:
                path.write_bytes(existing[path].read_bytes())
                path.chmod(0o600)
            else:
                path.unlink()
        if installed_signing:
            shutil.rmtree(signing)
        if (backup / 'previous-signing').exists():
            (backup / 'previous-signing').rename(signing)
        raise
    finally:
        if staging.exists():
            shutil.rmtree(staging)
    print('配置导入完成，原配置备份：' + str(backup))
    print('Mac 原身份的实际签名校验通过；仍需安装依赖、构建应用和授权录屏/输入控制。')


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='action', required=True)
    for name in ('export', 'import'):
        child = commands.add_parser(name)
        child.add_argument('--project', type=Path, default=Path(__file__).resolve().parent.parent)
        child.add_argument('--home', type=Path, default=Path.home(), help='默认当前用户目录；可用于隔离验证')
        child.add_argument('--passphrase-file', type=Path, help='可选的本机私密密码文件；不上传服务器')
        if name == 'export':
            child.add_argument('--output', type=Path, required=True)
            child.add_argument('--mac-only', action='store_true')
        else:
            child.add_argument('--archive', type=Path, required=True)
            child.add_argument('--sdk', type=Path)
            child.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    try:
        (export_bundle if args.action == 'export' else import_bundle)(args)
    except (ValueError, OSError, tarfile.TarError, json.JSONDecodeError) as error:
        # Do not render exception values from JSON/tar parsers or OS subprocesses,
        # which could quote private bundle contents. Our own ValueErrors are safe.
        if type(error) is ValueError:
            print('失败：' + str(error), file=sys.stderr)
        else:
            print('失败：文件读取或配置解析异常（' + type(error).__name__ + '）。', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
