#!/usr/bin/env python3
"""Create once and reuse a private, local-only BilldDesk signing identity."""
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile

os.umask(0o077)
root = Path.home() / ".config" / "billd-desk" / "signing"
root.mkdir(parents=True, exist_ok=True, mode=0o700)
config = root / "identity.json"
if config.exists():
    print(f"Existing identity preserved: {config}")
    raise SystemExit(0)
if any(root.iterdir()):
    raise SystemExit(f"Incomplete signing setup at {root}; inspect it before retrying.")

def run(*args):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if result.returncode:
        raise SystemExit(result.stderr.strip() or "Signing setup command failed")
    return result.stdout

password = secrets.token_urlsafe(48)
password_file = root / "keychain-password"
password_file.write_text(password)
keychain = root / "billd-desk.keychain-db"
cert = root / "certificate.pem"
backup = root / "identity.p12"
with tempfile.TemporaryDirectory(prefix="billd-signing-") as temporary:
    temporary = Path(temporary)
    key = temporary / "private.pem"
    extensions = temporary / "openssl.cnf"
    extensions.write_text("""[req]
distinguished_name=subject
x509_extensions=codesign
prompt=no
[subject]
CN=BilldDesk Local Code Signing
O=BilldDesk Local Development
[codesign]
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature
extendedKeyUsage=critical,codeSigning
subjectKeyIdentifier=hash
""")
    run("openssl", "req", "-new", "-x509", "-newkey", "rsa:3072", "-nodes",
        "-sha256", "-days", "3650", "-config", str(extensions),
        "-keyout", str(key), "-out", str(cert))
    run("openssl", "pkcs12", "-export", "-inkey", str(key), "-in", str(cert),
        "-name", "BilldDesk Local Code Signing", "-out", str(backup),
        "-passout", f"file:{password_file}")
    run("security", "create-keychain", "-p", password, str(keychain))
    run("security", "set-keychain-settings", "-lut", "21600", str(keychain))
    run("security", "unlock-keychain", "-p", password, str(keychain))
    run("security", "import", str(backup), "-k", str(keychain), "-P", password,
        "-T", "/usr/bin/codesign")
    # Limit private-key access to signing tools in this dedicated keychain.
    run("security", "set-key-partition-list", "-S", "apple-tool:,apple:,codesign:",
        "-s", "-k", password, str(keychain))

fingerprint = run("openssl", "x509", "-in", str(cert), "-noout", "-fingerprint", "-sha1")
identity = fingerprint.strip().split("=")[-1].replace(":", "")
config.write_text(json.dumps({"identity": identity, "keychain": str(keychain),
                              "passwordFile": str(password_file)}, indent=2) + "\n")
print(f"Created persistent local signing identity: {identity}")
print(f"Private keychain and encrypted backup: {root}")
print("No system trust settings were changed. Keep this directory private and backed up.")
