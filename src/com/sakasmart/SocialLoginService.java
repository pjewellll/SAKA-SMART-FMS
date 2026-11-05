package com.sakasmart;

import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Interactive browser OAuth; never requests the user's Google/Facebook password. */
final class SocialLoginService {
    record Profile(String provider,String subject,String name,String email) {}
    private SocialLoginService() {}
    static boolean configured(String provider){
        return switch(provider){
            case "GOOGLE" -> !env("SAKA_GOOGLE_CLIENT_ID").isBlank();
            case "FACEBOOK" -> !env("SAKA_FACEBOOK_APP_ID").isBlank() && !env("SAKA_FACEBOOK_APP_SECRET").isBlank();
            default -> false;
        };
    }
    private static String env(String key){return System.getenv().getOrDefault(key,"").trim();}
    private static String url(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
    private static String random(int length){byte[] b=new byte[length];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private static String pkce(String verifier) throws Exception {
        byte[] b=MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static String jsonField(String json,String key){
        Matcher m=Pattern.compile("\\\""+Pattern.quote(key)+"\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(json);
        if(!m.find())return "";
        return m.group(1).replace("\\\\","\\").replace("\\\"","\"");
    }
    private static String queryParam(String query,String name){
        if(query==null)return "";
        for(String p:query.split("&")){
            String[] pair=p.split("=",2);
            if(URLDecoder.decode(pair[0],StandardCharsets.UTF_8).equals(name))return pair.length==2?URLDecoder.decode(pair[1],StandardCharsets.UTF_8):"";
        }
        return "";
    }
    static Profile authenticate(String provider) throws Exception {
        if(!configured(provider))throw new IllegalStateException(provider+" sign-in is not configured. See SOCIAL_LOGIN_SETUP_V12.txt.");
        // Google currently rejects the desktop token exchange when the issued client secret is omitted.
        // This is an installed-app credential, not a secure server-side secret. Keep PKCE enabled.
        String googleClientSecret="GOOGLE".equals(provider)?env("SAKA_GOOGLE_CLIENT_SECRET"):"";
        if("GOOGLE".equals(provider) && googleClientSecret.isBlank())
            throw new IllegalStateException("Google Desktop OAuth Client Secret is missing. " +
                "Set SAKA_GOOGLE_CLIENT_SECRET in the terminal that launches SAKA-SMART.");
        String state=random(24),verifier=random(32);
        InetAddress loopback=InetAddress.getByName("127.0.0.1");
        // Facebook requires a pre-registered exact callback URI, unlike Google Desktop OAuth.
        int port="FACEBOOK".equals(provider)?8765:0;
        HttpServer server=HttpServer.create(new InetSocketAddress(loopback,port),0);
        String redirect=("FACEBOOK".equals(provider)?"http://localhost:":"http://127.0.0.1:")+
            server.getAddress().getPort()+"/oauth/callback";
        CompletableFuture<String> outcome=new CompletableFuture<>();
        server.createContext("/oauth/callback",exchange->{
            String code=queryParam(exchange.getRequestURI().getRawQuery(),"code");
            String returnedState=queryParam(exchange.getRequestURI().getRawQuery(),"state");
            String err=queryParam(exchange.getRequestURI().getRawQuery(),"error");
            boolean accepted=state.equals(returnedState)&&!code.isBlank()&&err.isBlank();
            // OAuth requires this localhost callback. Try to close its browser tab,
            // while the Swing window itself is brought forward by Main.java.
            // Most modern browsers allow closing only script-opened tabs; if the
            // browser refuses, the page remains a harmless, minimal fallback.
            String page=accepted?
                "<h2>Returning to SAKA-SMART...</h2>"+
                "<p>Finishing sign-in in the desktop application. You may close this tab if it stays open.</p>"+
                "<script>setTimeout(function(){window.close();},400);</script>":
                "<h2>Sign-in was not completed</h2>"+
                "<p>Return to SAKA-SMART to try again. You may close this tab.</p>";
            String html="<!doctype html><html lang='en'><head><meta charset='UTF-8'>"+
                "<meta name='viewport' content='width=device-width,initial-scale=1'>"+
                "<title>SAKA-SMART | Secure Sign-In</title>"+
                "<style>body{font:16px Segoe UI,Arial,sans-serif;background:#f4f9ed;color:#124b2b;"+
                "text-align:center;margin:0;padding:12vh 20px}h2{font-size:25px}p{color:#4b6656}</style>"+
                "</head><body>"+page+"</body></html>";
            byte[] data=html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control","no-store");
            exchange.getResponseHeaders().set("Referrer-Policy","no-referrer");
            exchange.getResponseHeaders().set("X-Content-Type-Options","nosniff");
            exchange.sendResponseHeaders(200,data.length);
            try(var out=exchange.getResponseBody()){out.write(data);}
            if(accepted)outcome.complete(code);else outcome.completeExceptionally(new SecurityException("OAuth sign-in was cancelled or failed state verification."));
        });
        server.start();
        try{
            String client=env("GOOGLE".equals(provider)?"SAKA_GOOGLE_CLIENT_ID":"SAKA_FACEBOOK_APP_ID");
            String authorize;
            if("GOOGLE".equals(provider)){
                authorize="https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id="+url(client)+
                    "&redirect_uri="+url(redirect)+"&scope="+url("openid email profile")+
                    "&state="+url(state)+"&code_challenge="+url(pkce(verifier))+"&code_challenge_method=S256&prompt=select_account";
            }else{
                authorize="https://www.facebook.com/v23.0/dialog/oauth?client_id="+url(client)+
                    "&redirect_uri="+url(redirect)+"&state="+url(state)+"&scope="+url("public_profile,email")+
                    "&response_type=code";
            }
            if(!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                throw new IllegalStateException("Default browser is unavailable on this computer.");
            Desktop.getDesktop().browse(URI.create(authorize));
            String code=outcome.get(180,TimeUnit.SECONDS);
            String tokenEndpoint="GOOGLE".equals(provider)?"https://oauth2.googleapis.com/token":"https://graph.facebook.com/v23.0/oauth/access_token";
            String payload="client_id="+url(client)+"&code="+url(code)+"&redirect_uri="+url(redirect);
            if("GOOGLE".equals(provider)) {
                payload+="&grant_type=authorization_code&code_verifier="+url(verifier);
                payload+="&client_secret="+url(googleClientSecret);
            } else payload+="&client_secret="+url(env("SAKA_FACEBOOK_APP_SECRET"));
            HttpRequest request=HttpRequest.newBuilder(URI.create(tokenEndpoint)).timeout(Duration.ofSeconds(20))
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            HttpResponse<String> response=HTTP.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()/100!=2){
                // Display only Google's error category and description, never tokens or authorization codes.
                String errorCode=jsonField(response.body(),"error");
                String description=jsonField(response.body(),"error_description");
                if(errorCode.isBlank())errorCode="unknown_error";
                if(description.length()>180)description=description.substring(0,180)+"...";
                throw new IllegalStateException(provider+" token exchange failed (HTTP "+response.statusCode()+"): "+
                    errorCode+(description.isBlank()?"":" - "+description));
            }
            String token=jsonField(response.body(),"access_token");
            if(token.isBlank())throw new SecurityException("OAuth provider did not return an access token.");
            String profileUrl="GOOGLE".equals(provider)?"https://openidconnect.googleapis.com/v1/userinfo":"https://graph.facebook.com/v23.0/me?fields=id,name,email";
            HttpRequest profileReq=HttpRequest.newBuilder(URI.create(profileUrl)).timeout(Duration.ofSeconds(20))
                .header("Authorization","Bearer "+token).GET().build();
            HttpResponse<String> details=HTTP.send(profileReq,HttpResponse.BodyHandlers.ofString());
            if(details.statusCode()/100!=2)throw new SecurityException("OAuth provider profile validation failed.");
            String sub=jsonField(details.body(),"GOOGLE".equals(provider)?"sub":"id");
            if(sub.isBlank())throw new SecurityException("Provider did not return a stable account identifier.");
            return new Profile(provider,sub,jsonField(details.body(),"name"),jsonField(details.body(),"email"));
        }finally{server.stop(0);}
    }
}
