package com.dennis.sunoprompts;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Official Sign in with ChatGPT OSS OAuth flow: system browser + 127.0.0.1 loopback, PKCE and OIDC. */
final class ChatGptConnection {
    private static final String AUTH="https://auth.openai.com/api/accounts/authorize";
    private static final String TOKEN="https://auth.openai.com/api/accounts/oauth/token";
    private static final String JWKS="https://auth.openai.com/.well-known/jwks.json";
    private static final String RESOURCE="https://api.openai.com/v1";
    private static final String ISSUER="https://auth.openai.com";
    private static final String ALIAS="SunoPromptsChatGptVault1";
    private static final SecureRandom RNG=new SecureRandom();
    private final Context context;
    private final SharedPreferences pref;
    private final Object refreshLock=new Object();
    private ServerSocket listener;
    interface Callback {
        void done(String account);
        void fail(String message);
    }

    ChatGptConnection(Context c) {
        context=c.getApplicationContext();
        pref=context.getSharedPreferences("siwc_v03",Context.MODE_PRIVATE);
    }
    boolean connected() {return readTokens()!=null;}
    String account() {
        JSONObject j=readTokens();
        return j==null?"Not connected":j.optString("email","ChatGPT connected");
    }
    boolean canUsePlan(){
        JSONObject j=readTokens();
        if(j==null)return false;
        return hasScope(j.optString("scope"),"chatgpt.tokens.use.direct");
    }
    void disconnect() {
        // Local sign-out. Remote revocation must be performed in ChatGPT Settings.
        pref.edit().remove("tokens").remove("pending_client_id").apply();
    }
    private String hostId() {
        String host=pref.getString("host_id","");
        if(!host.isEmpty())return host;
        host="urn:uuid:"+UUID.randomUUID();
        pref.edit().putString("host_id",host).commit();
        return host;
    }
    private static String random() {
        byte[] b=new byte[32];RNG.nextBytes(b);
        return Base64.encodeToString(b,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
    }
    private static String hash(String value)throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(digest,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
    }
    private static String enc(String value)throws Exception{return URLEncoder.encode(value,"UTF-8");}
    private static boolean hasScope(String space,String value){
        for(String part:space.split("\\s+"))if(value.equals(part))return true;
        return false;
    }
    private static SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        java.security.Key old=ks.getKey(ALIAS,null);
        if(old instanceof SecretKey)return (SecretKey)old;
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return generator.generateKey();
    }
    private void persist(JSONObject value)throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] data=cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));
        String stored=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+
            Base64.encodeToString(data,Base64.NO_WRAP);
        if(!pref.edit().putString("tokens",stored).commit())throw new Exception("Credential storage failed");
    }
    private JSONObject readTokens() {
        String stored=pref.getString("tokens","");
        if(stored.isEmpty())return null;
        try {
            String[] parts=stored.split(":",2);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),
                new GCMParameterSpec(128,Base64.decode(parts[0],Base64.DEFAULT)));
            return new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1],Base64.DEFAULT)),
                StandardCharsets.UTF_8));
        }catch(Exception ignored){return null;}
    }
    static String response(HttpURLConnection connection) throws Exception {
        InputStream stream=connection.getResponseCode()<400?connection.getInputStream():connection.getErrorStream();
        if(stream==null)return "";
        try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192];int count;
            while((count=in.read(b))!=-1){
                out.write(b,0,count);
                if(out.size()>2_000_000)throw new Exception("Response too large");
            }
            return out.toString("UTF-8");
        }
    }
    private static JSONObject postForm(String url,String form)throws Exception{
        // Retrying DNS lookup failures is safe: the request was never delivered.
        // Never retry a timed-out OAuth POST blindly: the one-use code or rotating
        // refresh token may already have been consumed by the server.
        UnknownHostException lastDnsError=null;
        for(int attempt=0;attempt<3;attempt++){
            HttpURLConnection c=null;
            try {
                c=(HttpURLConnection)new URL(url).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setDoOutput(true);
                c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
                try(OutputStream out=c.getOutputStream()) {
                    out.write(form.getBytes(StandardCharsets.UTF_8));
                }
                String body=response(c);
                if(c.getResponseCode()!=200){
                    String code="";
                    try{
                        JSONObject e=new JSONObject(body).optJSONObject("error");
                        if(e!=null)code=e.optString("code",e.optString("message",""));
                        if(code.isEmpty())code=new JSONObject(body).optString("error","");
                    }catch(Exception ignored){}
                    if("invalid_grant".equals(code))
                        throw new Exception("That sign-in code could not be used. Start a new ChatGPT sign-in; your saved prompts are safe.");
                    throw new Exception("ChatGPT authentication HTTP "+c.getResponseCode()+
                        (code.isEmpty()?"":" ("+code+")")+". Try reconnecting.");
                }
                return new JSONObject(body);
            }catch(UnknownHostException dns){
                lastDnsError=dns;
                if(attempt<2){
                    try{Thread.sleep((attempt+1)*1300L);}
                    catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();throw new Exception("Sign-in interrupted");
                    }
                }
            }finally{if(c!=null)c.disconnect();}
        }
        throw new UnknownHostException("auth.openai.com (DNS unavailable after 3 attempts)");
    }
    private static JSONObject fetchJson(String uri,String bearer)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(uri).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(15000);
        if(bearer!=null)c.setRequestProperty("Authorization","Bearer "+bearer);
        try {
            String body=response(c);
            if(c.getResponseCode()!=200)throw new Exception("Remote request failed: HTTP "+c.getResponseCode());
            return new JSONObject(body);
        }finally{c.disconnect();}
    }

    /** Browser will call back into this listener. Runs on a worker thread. */
    void signIn(android.app.Activity activity,Callback cb) {
        ServerSocket socket=null;
        try {
            synchronized(this) {
                if(listener!=null&&!listener.isClosed())throw new Exception("A sign-in is already in progress");
                socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));
                socket.setSoTimeout(1000);
                listener=socket;
            }
            final ServerSocket server=socket;
            final String state=random(),nonce=random(),verifier=random();
            final String redirect="http://127.0.0.1:"+server.getLocalPort()+"/auth/callback";
            final JSONObject old=readTokens();
            final String previousId=old==null?pref.getString("pending_client_id",""):old.optString("client_id","");
            final String clientId=previousId.isEmpty()?"dynamic_agent_client":previousId;
            Uri.Builder u=Uri.parse(AUTH).buildUpon()
                .appendQueryParameter("client_id",clientId)
                .appendQueryParameter("ext_agent_host_id",hostId())
                .appendQueryParameter("response_type","code")
                .appendQueryParameter("redirect_uri",redirect)
                .appendQueryParameter("scope","openid profile email offline_access resource.invoke chatgpt.tokens.use.direct")
                .appendQueryParameter("resource",RESOURCE)
                .appendQueryParameter("state",state)
                .appendQueryParameter("nonce",nonce)
                .appendQueryParameter("code_challenge_method","S256")
                .appendQueryParameter("code_challenge",hash(verifier));
            if(previousId.isEmpty())u.appendQueryParameter("agent_name_hint","Suno Prompts");
            else if(old!=null && !old.optString("id_token").isEmpty())
                u.appendQueryParameter("id_token_hint",old.optString("id_token"));
            activity.runOnUiThread(()->{
                try {
                    Intent i=new Intent(Intent.ACTION_VIEW,u.build());
                    activity.startActivity(i);
                }catch(Exception e){
                    stopListener();
                    cb.fail("No browser is available to complete ChatGPT sign-in.");
                }
            });
            long deadline=System.currentTimeMillis()+4*60_000;
            while(System.currentTimeMillis()<deadline && !server.isClosed()){
                try(Socket s=server.accept()) {
                    if(!s.getInetAddress().isLoopbackAddress())continue;
                    s.setSoTimeout(6000);
                    BufferedReader reader=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
                    String line=reader.readLine();
                    if(line==null||line.length()>4096)continue;
                    String[] request=line.split(" ");
                    if(request.length<2||!"GET".equals(request[0]))continue;
                    String header;
                    while((header=reader.readLine())!=null && !header.isEmpty()){}
                    Uri uri=Uri.parse("http://127.0.0.1"+request[1]);
                    if(!"/auth/callback".equals(uri.getPath())){reply(s,"Unknown callback");continue;}
                    String receivedState=uri.getQueryParameter("state");
                    if(receivedState==null || !MessageDigest.isEqual(
                        state.getBytes(StandardCharsets.UTF_8),receivedState.getBytes(StandardCharsets.UTF_8))) {
                        reply(s,"Invalid sign-in state. Return to Suno Prompts.");
                        throw new Exception("Sign-in state did not match. No credentials were stored.");
                    }
                    String error=uri.getQueryParameter("error");
                    if(error!=null) {
                        reply(s,"Permission not granted. Return to Suno Prompts.");
                        throw new Exception("ChatGPT connection was declined.");
                    }
                    String code=uri.getQueryParameter("code");
                    if(code==null||code.isEmpty())throw new Exception("No authorisation code returned");
                    String callbackId=uri.getQueryParameter("client_id");
                    if(previousId.isEmpty() && (callbackId==null||!callbackId.startsWith("oaiapp_")))
                        throw new Exception("OpenAI did not issue a valid app registration");
                    if(!previousId.isEmpty() && callbackId!=null && !previousId.equals(callbackId))
                        throw new Exception("Different client ID in callback");
                    reply(s,"Authorisation returned. Open Suno Prompts to see whether the secure connection finishes; this page does not confirm connection.");
                    final String issuedId=previousId.isEmpty()?callbackId:previousId;
                    // Retain registration ID across recoverable network failures, but never mark it connected until OIDC validation succeeds.
                    if(previousId.isEmpty())pref.edit().putString("pending_client_id",issuedId).commit();
                    JSONObject token=postForm(TOKEN,"grant_type=authorization_code&client_id="+enc(issuedId)+
                        "&code="+enc(code)+"&code_verifier="+enc(verifier)+"&redirect_uri="+enc(redirect)+
                        "&resource="+enc(RESOURCE));
                    String idToken=token.optString("id_token");
                    JSONObject identity=verifyIdToken(idToken,issuedId,nonce);
                    if(old!=null && !previousId.isEmpty() && !identity.getString("sub").equals(old.optString("sub")))
                        throw new Exception("Account does not match this registration");
                    String granted=token.optString("scope","");
                    if(!hasScope(granted,"chatgpt.tokens.use.direct"))
                        throw new Exception("Connected for identity only; ChatGPT plan usage was not authorised");
                    if(token.optString("access_token").isEmpty() || token.optString("refresh_token").isEmpty())
                        throw new Exception("Required access or refresh token missing");
                    JSONObject saved=new JSONObject();
                    saved.put("client_id",issuedId);saved.put("sub",identity.getString("sub"));
                    saved.put("email",identity.optString("email","ChatGPT account"));
                    saved.put("scope",granted);saved.put("id_token",idToken);
                    saved.put("access_token",token.getString("access_token"));
                    saved.put("refresh_token",token.getString("refresh_token"));
                    saved.put("expires_at",System.currentTimeMillis()+token.optLong("expires_in",3600)*1000L);
                    persist(saved);
                    pref.edit().remove("pending_client_id").apply();
                    cb.done(saved.getString("email"));
                    return;
                }catch(java.net.SocketTimeoutException ignored) { /* listen until expiry */ }
            }
            throw new Exception("Sign-in timed out. Try again from the app.");
        }catch(UnknownHostException dns){
            cb.fail("OpenAI sign-in returned successfully, but the phone's DNS could not resolve auth.openai.com for the secure token exchange. We retried three times without sending credentials. In Suno Prompts, run Test OpenAI connection. Try switching between Wi-Fi and mobile data, or check VPN / Private DNS filtering, then sign in again. No prompts were changed.");
        }catch(Exception e){cb.fail(e.getMessage()==null?"Could not connect ChatGPT":e.getMessage());}
        finally{stopListener();}
    }
    synchronized void stopListener() {
        if(listener!=null){try{listener.close();}catch(Exception ignored){}listener=null;}
    }
    private static void reply(Socket s,String message)throws Exception {
        String html="<html><meta name='viewport' content='width=device-width'><body style='background:#111729;color:white;font:18px sans-serif;padding:30px'><h2>Suno Prompts</h2><p>"+message+"</p></body></html>";
        byte[] bytes=html.getBytes(StandardCharsets.UTF_8);
        s.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"+
            "Cache-Control: no-store\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        s.getOutputStream().write(bytes);s.getOutputStream().flush();
    }
    private static byte[] b64(String s){return Base64.decode(s,Base64.URL_SAFE|Base64.NO_WRAP);}
    private static JSONObject verifyIdToken(String jwt,String client,String nonce)throws Exception {
        String[] split=jwt.split("\\.");
        if(split.length!=3)throw new Exception("Invalid identity token");
        JSONObject header=new JSONObject(new String(b64(split[0]),StandardCharsets.UTF_8));
        JSONObject claims=new JSONObject(new String(b64(split[1]),StandardCharsets.UTF_8));
        if(!"RS256".equals(header.optString("alg")))throw new Exception("Unexpected ID token signature type");
        if(!ISSUER.equals(claims.optString("iss")))throw new Exception("Untrusted token issuer");
        Object aud=claims.opt("aud");
        boolean audience=client.equals(aud);
        if(aud instanceof JSONArray) {
            JSONArray ar=(JSONArray)aud;
            for(int i=0;i<ar.length();i++)if(client.equals(ar.optString(i)))audience=true;
        }
        if(!audience)throw new Exception("Identity token audience mismatch");
        if(!nonce.equals(claims.optString("nonce")))throw new Exception("Identity token nonce mismatch");
        long now=System.currentTimeMillis()/1000;
        if(claims.optLong("exp",0)<=now || claims.optLong("iat",now+1)>now+120)
            throw new Exception("Expired or invalid identity token");
        if(claims.optString("sub").isEmpty())throw new Exception("No subject in identity token");
        JSONObject jwks=fetchJson(JWKS,null);
        JSONArray keys=jwks.getJSONArray("keys");
        String kid=header.optString("kid");
        boolean verified=false;
        for(int i=0;i<keys.length();i++){
            JSONObject k=keys.getJSONObject(i);
            if(!kid.equals(k.optString("kid")) || !"RSA".equals(k.optString("kty")))continue;
            PublicKey pub=KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                new BigInteger(1,b64(k.getString("n"))),new BigInteger(1,b64(k.getString("e")))));
            Signature sig=Signature.getInstance("SHA256withRSA");
            sig.initVerify(pub);sig.update((split[0]+"."+split[1]).getBytes(StandardCharsets.US_ASCII));
            if(sig.verify(b64(split[2])))verified=true;
        }
        if(!verified)throw new Exception("Identity token signature was not verified");
        return claims;
    }
    String accessToken()throws Exception {
        synchronized(refreshLock) {
            JSONObject j=readTokens();
            if(j==null||!hasScope(j.optString("scope"),"chatgpt.tokens.use.direct"))
                throw new Exception("Connect your ChatGPT plan first.");
            if(System.currentTimeMillis()+120_000<j.optLong("expires_at",0))
                return j.getString("access_token");
            String refresh=j.optString("refresh_token");
            if(refresh.isEmpty())throw new Exception("ChatGPT session expired. Sign in again.");
            JSONObject updated=postForm(TOKEN,"grant_type=refresh_token&client_id="+
                enc(j.getString("client_id"))+"&refresh_token="+enc(refresh)+"&resource="+enc(RESOURCE));
            if(updated.optString("access_token").isEmpty())
                throw new Exception("Unable to renew ChatGPT access. Sign in again.");
            String newScope=updated.optString("scope",j.optString("scope"));
            if(!hasScope(newScope,"chatgpt.tokens.use.direct"))
                throw new Exception("ChatGPT plan permission was withdrawn.");
            j.put("access_token",updated.getString("access_token"));
            j.put("refresh_token",updated.optString("refresh_token",refresh));
            j.put("scope",newScope);
            j.put("expires_at",System.currentTimeMillis()+updated.optLong("expires_in",3600)*1000L);
            persist(j);
            return j.getString("access_token");
        }
    }
}
