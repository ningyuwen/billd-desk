#!/usr/bin/env bash
# Run as root from a staged directory after explicit deployment authorization.
set -euo pipefail
stage_dir=$(cd -- "$(dirname -- "$0")" && pwd)
node_bin=$(command -v node)
if [ "$node_bin" != /usr/bin/node ]; then
    echo 'Review ExecStart: expected /usr/bin/node' >&2
    exit 1
fi
if ss -ltnH '( sport = :4311 )' | read -r _; then
    echo 'Port 4311 is already occupied; review before deployment' >&2
    exit 1
fi
if ! id -u billd-mini >/dev/null 2>&1; then
    useradd --system --home-dir /opt/billd-mini-relay --shell /usr/sbin/nologin billd-mini
fi
install -d -m 755 /opt/billd-mini-relay
install -m 644 "$stage_dir/server.mjs" "$stage_dir/package.json" "$stage_dir/package-lock.json" /opt/billd-mini-relay/
cd /opt/billd-mini-relay
npm ci --omit=dev --ignore-scripts --registry=https://registry.npmjs.org
install -m 644 "$stage_dir/billd-mini-relay.service.example" /etc/systemd/system/billd-mini-relay.service
nginx_file=/etc/nginx/sites-available/billd-desk
backup_file="${nginx_file}.before-mini-$(date +%Y%m%d%H%M%S)"
cp -p "$nginx_file" "$backup_file"
python3 - "$nginx_file" "$stage_dir/nginx-location.conf.example" <<'PY'
from pathlib import Path
import sys
path, snippet = map(Path, sys.argv[1:])
text = path.read_text()
if 'location /mini-control/' in text:
    raise SystemExit('An existing mini-control location requires manual review')
anchor = '    location /socket.io/ {'
if text.count(anchor) != 1 or 'server_name desk.aduning.art;' not in text:
    raise SystemExit('Unexpected Nginx configuration; review before deployment')
block = '\n'.join('    ' + line if line else '' for line in snippet.read_text().splitlines())
path.write_text(text.replace(anchor, block + '\n\n' + anchor, 1))
PY
if ! nginx -t; then
    cp -p "$backup_file" "$nginx_file"
    exit 1
fi
systemctl daemon-reload
if ! systemctl enable --now billd-mini-relay; then
    cp -p "$backup_file" "$nginx_file"
    exit 1
fi
if ! curl --fail --silent --retry 3 --retry-connrefused http://127.0.0.1:4311/health; then
    systemctl stop billd-mini-relay
    cp -p "$backup_file" "$nginx_file"
    exit 1
fi
if ! systemctl reload nginx; then
    cp -p "$backup_file" "$nginx_file"
    systemctl stop billd-mini-relay
    exit 1
fi
systemctl is-active billd-mini-relay nginx
printf 'Nginx backup: %s\n' "$backup_file"
