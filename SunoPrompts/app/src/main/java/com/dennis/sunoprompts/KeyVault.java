package com.dennis.sunoprompts;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class KeyVault {
    private static final String ALIAS="SunoPromptsApiKeyV1";
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("suno_settings",Context.MODE_PRIVATE);
    }
    static String model(Context c) { return prefs(c).getString("model","gpt-4.1-mini"); }
    static void setModel(Context c,String s) { prefs(c).edit().putString("model",s.trim()).apply(); }
    private static SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        java.security.Key found=ks.getKey(ALIAS,null);
        if(found instanceof SecretKey) return (SecretKey)found;
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return kg.generateKey();
    }
    static void store(Context c,String value) throws Exception {
        if(value.trim().isEmpty()) { prefs(c).edit().remove("api_key").apply();return; }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(value.trim().getBytes(StandardCharsets.UTF_8));
        prefs(c).edit().putString("api_key",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply();
    }
    static String read(Context c) {
        String stored=prefs(c).getString("api_key","");
        if(stored.isEmpty())return "";
        try {
            String[] parts=stored.split(":",2);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.DEFAULT)));
            return new String(cipher.doFinal(Base64.decode(parts[1],Base64.DEFAULT)),StandardCharsets.UTF_8);
        }catch(Exception ignored) {return "";}
    }
}
