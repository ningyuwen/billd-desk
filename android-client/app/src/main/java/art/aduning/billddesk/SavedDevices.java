package art.aduning.billddesk;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Saved remote credentials are scoped to the server and encrypted with a device-bound key. */
final class SavedDevices {
    private static final String KEY_ALIAS = "billddesk.saved_devices.v1";
    private final Context context;

    SavedDevices(Context context) { this.context = context.getApplicationContext(); }

    private SharedPreferences preferences(String server) {
        String name = Base64.encodeToString(server.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return context.getSharedPreferences("saved_devices_" + name, Context.MODE_PRIVATE);
    }
    private JSONArray records(String server) {
        try { return new JSONArray(preferences(server).getString("devices", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }
    List<String> codes(String server) {
        List<String> result = new ArrayList<>(); JSONArray records = records(server);
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record != null && !record.optString("code").isEmpty()) result.add(record.optString("code"));
        }
        return result;
    }
    String lastCode(String server) { return preferences(server).getString("last_code", ""); }
    String password(String server, String code) throws Exception {
        JSONArray records = records(server);
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null || !code.equals(record.optString("code"))) continue;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128,
                    Base64.decode(record.getString("iv"), Base64.NO_WRAP)));
            cipher.updateAAD((server + "\n" + code).getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.decode(record.getString("secret"), Base64.NO_WRAP)),
                    StandardCharsets.UTF_8);
        }
        return "";
    }
    void save(String server, String code, String password) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        cipher.updateAAD((server + "\n" + code).getBytes(StandardCharsets.UTF_8));
        JSONObject record = new JSONObject().put("code", code)
                .put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .put("secret", Base64.encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP));
        JSONArray next = new JSONArray().put(record), previous = records(server);
        for (int i = 0; i < previous.length() && next.length() < 20; i++) {
            JSONObject item = previous.optJSONObject(i);
            if (item != null && !code.equals(item.optString("code"))) next.put(item);
        }
        preferences(server).edit().putString("devices", next.toString()).putString("last_code", code).apply();
    }
    void forget(String server, String code) {
        JSONArray next = new JSONArray(), previous = records(server);
        for (int i = 0; i < previous.length(); i++) {
            JSONObject item = previous.optJSONObject(i);
            if (item != null && !code.equals(item.optString("code"))) next.put(item);
        }
        SharedPreferences preferences = preferences(server);
        SharedPreferences.Editor editor = preferences.edit().putString("devices", next.toString());
        if (code.equals(lastCode(server))) editor.putString("last_code",
                next.length() == 0 ? "" : next.optJSONObject(0).optString("code"));
        editor.apply();
    }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        SecretKey key = (SecretKey) store.getKey(KEY_ALIAS, null);
        if (key != null) return key;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
}
