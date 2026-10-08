function parseInvitation(value) {
  if (typeof value !== 'string' || value.length > 512) return null;
  const lines = value
    .trim()
    .split(/\r?\n/)
    .map((line) => line.trim());
  if (lines.length !== 4 || lines[0] !== 'BilldDesk 小程序连接') return null;
  const url =
    /^中继地址：[ \t]*(wss:\/\/[a-zA-Z0-9.-]+(?::\d+)?\/mini-control\/)$/.exec(
      lines[1]
    );
  const code = /^连接码：[ \t]*(\d{8})$/.exec(lines[2]);
  const password = /^临时密码：[ \t]*([a-f0-9]{24})$/.exec(lines[3]);
  return url && code && password
    ? { url: url[1], code: code[1], password: password[1] }
    : null;
}

module.exports = { parseInvitation };
