package cn.bigfish.phone;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecretStore {
    private static final String ALIAS = "bigfish-settings-v1";
    private final Context context;

    public SecretStore(Context context) { this.context = context; }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }

    public synchronized Settings load() throws Exception {
        String stored = context.getSharedPreferences("secrets", Context.MODE_PRIVATE).getString("config", "");
        if (stored.isEmpty()) return new Settings(new JSONObject());
        JSONObject data = new JSONObject(stored);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(data.getString("iv"), Base64.NO_WRAP)));
        byte[] content = cipher.doFinal(Base64.decode(data.getString("data"), Base64.NO_WRAP));
        return new Settings(new JSONObject(new String(content, java.nio.charset.StandardCharsets.UTF_8)));
    }

    public synchronized void save(Settings settings) throws Exception {
        settings.validate();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(settings.json().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        JSONObject data = new JSONObject().put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP));
        if (!context.getSharedPreferences("secrets", Context.MODE_PRIVATE).edit().putString("config", data.toString()).commit())
            throw new IllegalStateException("配置写入失败");
    }
}
