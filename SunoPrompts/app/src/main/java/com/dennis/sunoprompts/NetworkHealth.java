package com.dennis.sunoprompts;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.LinkProperties;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;

/** No credentials or user data sent. Tests public OIDC metadata over TLS. */
final class NetworkHealth {
    private static final String HOST="auth.openai.com";
    static String test(Context context){
        StringBuilder info=new StringBuilder();
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)return "Android connectivity service unavailable.";
        Network active=cm.getActiveNetwork();
        if(active==null)return "No active Android network. Connect to Wi-Fi or mobile data.";
        NetworkCapabilities cap=cm.getNetworkCapabilities(active);
        if(cap!=null){
            String medium=cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN)?"VPN":
                cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":
                cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"Mobile data":"Other network";
            info.append("Network: ").append(medium);
            info.append(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)?" (Android validated)\n":
                " (not internet-validated)\n");
        }
        if(android.os.Build.VERSION.SDK_INT>=28){
            LinkProperties link=cm.getLinkProperties(active);
            if(link!=null && link.isPrivateDnsActive()){
                info.append("Private DNS: active");
                String dns=link.getPrivateDnsServerName();
                if(dns!=null&&!dns.isEmpty())info.append(" (").append(dns).append(")");
                info.append("\n");
            }
        }
        try {
            // Network.getAllByName performs lookup through Android's active network.
            InetAddress[] addresses=active.getAllByName(HOST);
            if(addresses.length==0)return info+"DNS returned no addresses for "+HOST+".";
            info.append("OpenAI DNS: resolved\n");
        }catch(UnknownHostException e){
            return info+"OpenAI DNS: FAILED for "+HOST+"\n\n"+
                "Try the other connection (mobile data or Wi-Fi). If a VPN, custom Private DNS or filtering app is enabled, try disabling it temporarily and retest. This is a network name-resolution failure, not an incorrect ChatGPT password.";
        }catch(Exception e){return info+"OpenAI DNS: test failed ("+e.getClass().getSimpleName()+").";}
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)active.openConnection(
                new URL("https://auth.openai.com/.well-known/openid-configuration"));
            c.setRequestMethod("GET");c.setConnectTimeout(12000);c.setReadTimeout(12000);
            c.setInstanceFollowRedirects(false);
            int status=c.getResponseCode();
            if(status!=200)
                return info+"OpenAI HTTPS: HTTP "+status+". The domain resolves but the service, VPN or network may block the request.";
            String body=ChatGptConnection.response(c);
            JSONObject metadata=new JSONObject(body);
            if(!"https://auth.openai.com".equals(metadata.optString("issuer")))
                return info+"OpenAI HTTPS: unexpected identity-provider response. Do not sign in on this network.";
            return info+"OpenAI HTTPS: CONNECTED\n\nThe official identity server is reachable. Return to Suno Prompts and try Continue with ChatGPT again.";
        }catch(javax.net.ssl.SSLException e){
            return info+"OpenAI HTTPS: TLS verification failed. Check VPN/content-filter settings and your phone's date/time. Do not bypass TLS security.";
        }catch(Exception e){
            return info+"OpenAI HTTPS: connection failed ("+e.getClass().getSimpleName()+"). Try a different network and retest.";
        }finally{if(c!=null)c.disconnect();}
    }
}
