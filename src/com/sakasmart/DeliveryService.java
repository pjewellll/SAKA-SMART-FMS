package com.sakasmart;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/** Online delivery integrations. No credentials or codes are written to application logs. */
public final class DeliveryService {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private DeliveryService() {}

    private static String env(String key){return System.getenv().getOrDefault(key,"").trim();}
    public static boolean configured(String channel) {
        return switch(channel) {
            case "EMAIL" -> !env("SAKA_RESEND_API_KEY").isBlank() && !env("SAKA_RESEND_FROM").isBlank();
            case "SMS" -> !env("SAKA_TWILIO_ACCOUNT_SID").isBlank()
                && !env("SAKA_TWILIO_AUTH_TOKEN").isBlank() && !env("SAKA_TWILIO_FROM").isBlank();
            default -> false;
        };
    }
    public static void send(String channel,String destination,String code) throws Exception {
        if(!configured(channel))throw new IllegalStateException(channel+" recovery provider is not configured.");
        if("EMAIL".equals(channel))sendEmail(destination,code);
        else if("SMS".equals(channel))sendSms(destination,code);
        else throw new IllegalArgumentException("Unsupported delivery channel.");
    }
    private static String escapeJson(String s) {
        StringBuilder out=new StringBuilder();
        for(char c:s.toCharArray())switch(c){
            case '\\' -> out.append("\\\\");
            case '"' -> out.append("\\\"");
            case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r");
            default -> {if(c<32)out.append(String.format("\\u%04x",(int)c));else out.append(c);}
        }
        return out.toString();
    }
    private static void sendEmail(String destination,String code) throws Exception {
        String body="Your SAKA-SMART one-time verification code is "+code+". " +
            "It expires in "+RecoveryCodes.LIFETIME_MINUTES+" minutes. " +
            "If you did not request this, ignore the message and contact your farm administrator.";
        String payload="{\"from\":\""+escapeJson(env("SAKA_RESEND_FROM"))+"\",\"to\":[\""+
            escapeJson(destination)+"\"],\"subject\":\"SAKA-SMART verification code\",\"text\":\""+
            escapeJson(body)+"\"}";
        HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization","Bearer "+env("SAKA_RESEND_API_KEY"))
            .header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload,StandardCharsets.UTF_8)).build();
        HttpResponse<String> response=CLIENT.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()<200 || response.statusCode()>=300)
            throw new IllegalStateException("Email provider rejected the request (HTTP "+response.statusCode()+").");
    }
    private static String param(String key,String val) {
        return URLEncoder.encode(key,StandardCharsets.UTF_8)+"="+URLEncoder.encode(val,StandardCharsets.UTF_8);
    }
    private static void sendSms(String destination,String code) throws Exception {
        String sid=env("SAKA_TWILIO_ACCOUNT_SID");
        String auth=Base64.getEncoder().encodeToString((sid+":"+env("SAKA_TWILIO_AUTH_TOKEN")).getBytes(StandardCharsets.UTF_8));
        String message="SAKA-SMART verification code: "+code+". Valid for "+RecoveryCodes.LIFETIME_MINUTES+
            " minutes. Never share except when entering it in SAKA-SMART.";
        String body=param("To",destination)+"&"+param("From",env("SAKA_TWILIO_FROM"))+"&"+param("Body",message);
        HttpRequest request=HttpRequest.newBuilder(URI.create(
            "https://api.twilio.com/2010-04-01/Accounts/"+sid+"/Messages.json"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization","Basic "+auth)
            .header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
        HttpResponse<String> response=CLIENT.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()<200 || response.statusCode()>=300)
            throw new IllegalStateException("SMS provider rejected the request (HTTP "+response.statusCode()+").");
    }
}
