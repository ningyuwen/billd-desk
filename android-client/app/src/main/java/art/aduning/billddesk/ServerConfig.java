package art.aduning.billddesk;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.URI;

final class ServerConfig {
    final String server, turn, user, password;
    ServerConfig(String server, String turn, String user, String password) {
        URI uri = URI.create(server.trim());
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/"))) {
            throw new IllegalArgumentException("请输入 HTTPS 服务器域名，例如 https://desk.aduning.art");
        }
        if (!turn.startsWith("turn:") && !turn.startsWith("turns:"))
            throw new IllegalArgumentException("中继地址需以 turn: 或 turns: 开头");
        this.server = server.trim().replaceAll("/+$", "");
        this.turn = turn.trim(); this.user = user.trim(); this.password = password;
    }
    static ServerConfig load(Context context) {
        SharedPreferences p = context.getSharedPreferences("server", Context.MODE_PRIVATE);
        return new ServerConfig(p.getString("url", BuildConfig.SERVER_URL), p.getString("turn", BuildConfig.TURN_URL),
                p.getString("user", BuildConfig.TURN_USER), p.getString("password", BuildConfig.TURN_PASSWORD));
    }
    void save(Context context) {
        context.getSharedPreferences("server", Context.MODE_PRIVATE).edit().putString("url", server)
                .putString("turn", turn).putString("user", user).putString("password", password).apply();
    }
}
