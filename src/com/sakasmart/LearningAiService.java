package com.sakasmart;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SAKA AI transport: no external Java libraries or bundled credentials.
 *
 * Production: set SAKA_AI_ENDPOINT to an authenticated, rate-limited HTTPS proxy
 * that stores provider credentials on the server. The proxy accepts:
 * {"message":"...","language":"Auto|English|Tagalog","history":[{"role":"user|assistant","text":"..."}]}
 * and returns {"reply":"..."}.
 *
 * LOCAL TESTING ONLY: set GEMINI_API_KEY in the launch environment. Never add it
 * to Git, distribute it with the Java app, or ask users to share it.
 */
final class LearningAiService {
    record Turn(String role, String text) {}
    private LearningAiService() {}

    static boolean configured() {
        return !env("SAKA_AI_ENDPOINT").isBlank() || !env("GEMINI_API_KEY").isBlank();
    }

    static String configurationText() {
        return configured() ? "AI is configured. Internet is required for answers."
            : "AI not configured yet. Offline guides are available below.";
    }

    static String ask(String question, String language, List<Turn> previous) throws Exception {
        if (question == null || question.isBlank()) throw new IllegalArgumentException("Please type a farming question.");
        if (question.length() > 1800) throw new IllegalArgumentException("Please shorten your question to 1,800 characters.");
        if (!List.of("Auto", "English", "Tagalog").contains(language)) language="Auto";
        String endpoint = env("SAKA_AI_ENDPOINT");
        if (!endpoint.isBlank()) return proxyAsk(endpoint, question.trim(), language, previous);
        String key=env("GEMINI_API_KEY");
        if (key.isBlank()) throw new IllegalStateException("SAKA AI is not configured yet. You can still read the offline guides.");
        return geminiAsk(key, question.trim(), language, previous);
    }

    private static String proxyAsk(String endpoint, String question, String language, List<Turn> previous) throws Exception {
        URI uri = URI.create(endpoint);
        String scheme=Objects.toString(uri.getScheme(),"");
        boolean local = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (!("https".equalsIgnoreCase(scheme) || (local && "http".equalsIgnoreCase(scheme))))
            throw new IllegalArgumentException("SAKA_AI_ENDPOINT must use HTTPS (or HTTP for localhost testing).");
        Map<String,Object> request=new LinkedHashMap<>();
        request.put("message",question);request.put("language",language);request.put("history",turns(previous));
        HttpRequest http=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(40))
            .header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(Json.write(request))).build();
        HttpResponse<String> response=client().send(http,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw serviceError(response.statusCode());
        Object parsed=Json.read(response.body());
        if(!(parsed instanceof Map<?,?> obj))throw new IllegalStateException("AI server response was not valid JSON.");
        Object reply=obj.get("reply");
        if(!(reply instanceof String s) || s.isBlank())throw new IllegalStateException("AI server returned an empty reply.");
        return compact(s);
    }

    private static String geminiAsk(String key, String question, String language, List<Turn> previous) throws Exception {
        String model=env("SAKA_GEMINI_MODEL");
        if(model.isBlank())model="gemini-2.5-flash";
        if(!model.matches("[a-zA-Z0-9_.-]+"))throw new IllegalArgumentException("Invalid SAKA_GEMINI_MODEL.");
        URI uri=URI.create("https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent");
        Map<String,Object> request=new LinkedHashMap<>();
        request.put("systemInstruction",Map.of("parts",List.of(Map.of("text",instruction(language)))));
        List<Object> contents=new ArrayList<>();
        // At most 3 previous exchanges. Only questions and chat replies are sent;
        // never send private farm, accounting, login, inventory or database records.
        if(previous!=null)for(Turn t:previous.subList(Math.max(0,previous.size()-6),previous.size())){
            if(t==null || t.text()==null || t.text().isBlank())continue;
            String role="assistant".equals(t.role())?"model":"user";
            contents.add(Map.of("role",role,"parts",List.of(Map.of("text",compact(t.text())))));
        }
        contents.add(Map.of("role","user","parts",List.of(Map.of("text",question))));
        request.put("contents",contents);
        request.put("generationConfig",Map.of("maxOutputTokens",1100,"temperature",0.35));
        HttpRequest http=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(40))
            .header("Content-Type","application/json")
            .header("x-goog-api-key",key)
            .POST(HttpRequest.BodyPublishers.ofString(Json.write(request))).build();
        HttpResponse<String> response=client().send(http,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw serviceError(response.statusCode());
        Object root=Json.read(response.body());
        Object candidates=prop(root,"candidates");
        if(!(candidates instanceof List<?> arr) || arr.isEmpty())
            throw new IllegalStateException("The AI did not return an answer. Please try a different question.");
        Object parts=prop(prop(arr.get(0),"content"),"parts");
        StringBuilder result=new StringBuilder();
        if(parts instanceof List<?> list)for(Object part:list){Object t=prop(part,"text");if(t instanceof String s)result.append(s);}
        if(result.isEmpty())throw new IllegalStateException("The AI could not answer that question. Please rephrase it.");
        return compact(result.toString());
    }

    private static List<Object> turns(List<Turn> previous){
        List<Object> result=new ArrayList<>();
        if(previous!=null)for(Turn t:previous.subList(Math.max(0,previous.size()-6),previous.size()))
            if(t!=null && t.text()!=null && !t.text().isBlank())result.add(Map.of("role",t.role(),"text",compact(t.text())));
        return result;
    }
    private static String instruction(String language){
        String lang=switch(language){
            case "Tagalog" -> "Respond mostly in clear, simple Filipino/Tagalog; use familiar farming terms and explain technical English words.";
            case "English" -> "Respond in simple, clear English for people with no farming experience.";
            default -> "Match the farmer's language naturally: English, Filipino/Tagalog, or Taglish. Reply naturally in the user's chosen language.";
        };
        return "You are SAKA AI, an educational farming assistant for beginners in the Philippines. " + lang + " " +
            "Give practical, numbered steps where helpful. Ask for region, soil, crop, available space, weather, and budget when needed rather than guessing. " +
            "Never pretend you inspected a plant, animal, farm, photo, live weather or local government advisory. " +
            "State uncertainty plainly; avoid making up sources, diagnoses, exact fertilizer or pesticide dosages, withdrawal periods, or veterinary treatment. " +
            "For suspected plant disease or pest infestation suggest safe observation and a local agriculture extension office; " +
            "for ill animals recommend a licensed veterinarian; for pesticide application refer to the product label and local agricultural authorities. " +
            "Do not suggest mixing chemicals or unsafe use of pesticides. Do not claim the information is guaranteed or replace professional advice. " +
            "Keep the response concise, welcoming and educational. Do not claim you accessed SAKA-SMART's private farm records.";
    }
    private static HttpClient client(){return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();}
    private static String env(String n){return Objects.toString(System.getenv(n),"").trim();}
    private static Object prop(Object obj,String name){return obj instanceof Map<?,?> m?m.get(name):null;}
    private static String compact(String s){return s.length()>7000?s.substring(0,7000)+"\n[Response shortened]":s;}
    private static Exception serviceError(int status){
        return switch(status){
            case 401,403 -> new IllegalStateException("AI authentication was rejected. Check the service configuration.");
            case 404 -> new IllegalStateException("AI model or endpoint was not found. Check the configured model/service.");
            case 429 -> new IllegalStateException("AI usage limit reached. Please try again later.");
            default -> new IllegalStateException("AI service is unavailable (HTTP "+status+"). Please try again.");
        };
    }

    /** Small JSON reader/writer for the REST response. No extra JAR is required. */
    static final class Json {
        private final String s;private int i;
        private Json(String s){this.s=s;}
        static Object read(String s){Json j=new Json(s);Object o=j.value(0);j.space();if(j.i!=s.length())throw new IllegalArgumentException("Invalid JSON response");return o;}
        private void space(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}
        private char peek(){space();if(i>=s.length())throw new IllegalArgumentException("Unexpected end of JSON");return s.charAt(i);}
        private Object value(int depth){if(depth>32)throw new IllegalArgumentException("Invalid JSON nesting");char c=peek();
            if(c=='"')return string();
            if(c=='{'){i++;Map<String,Object> m=new LinkedHashMap<>();if(peek()=='}'){i++;return m;}
                do{String k=string();if(peek()!=':')throw new IllegalArgumentException("Expected :");i++;m.put(k,value(depth+1));char sep=peek();i++;if(sep=='}')return m;if(sep!=',')throw new IllegalArgumentException("Expected ,");}while(true);}
            if(c=='['){i++;List<Object> a=new ArrayList<>();if(peek()==']'){i++;return a;}
                do{a.add(value(depth+1));char sep=peek();i++;if(sep==']')return a;if(sep!=',')throw new IllegalArgumentException("Expected ,");}while(true);}
            if(s.startsWith("true",i)){i+=4;return true;}if(s.startsWith("false",i)){i+=5;return false;}if(s.startsWith("null",i)){i+=4;return null;}
            int start=i;while(i<s.length()&&"-+0123456789.eE".indexOf(s.charAt(i))>=0)i++;
            if(start==i)throw new IllegalArgumentException("Invalid JSON value");return Double.parseDouble(s.substring(start,i));
        }
        private String string(){if(peek()!='"')throw new IllegalArgumentException("Expected JSON string");i++;StringBuilder b=new StringBuilder();
            while(i<s.length()){char c=s.charAt(i++);if(c=='"')return b.toString();if(c!='\\'){b.append(c);continue;}
                if(i==s.length())break;char esc=s.charAt(i++);switch(esc){
                    case '"'->b.append('"');case '\\'->b.append('\\');case '/'->b.append('/');case 'n'->b.append('\n');case 'r'->b.append('\r');case 't'->b.append('\t');case 'b'->b.append('\b');case 'f'->b.append('\f');
                    case 'u'->{if(i+4>s.length())throw new IllegalArgumentException("Invalid JSON escape");b.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;}
                    default->throw new IllegalArgumentException("Invalid JSON escape");
                }
            }throw new IllegalArgumentException("Unterminated JSON string");
        }
        static String write(Object o){if(o==null)return "null";if(o instanceof String t)return '"'+escape(t)+'"';
            if(o instanceof Number||o instanceof Boolean)return o.toString();
            if(o instanceof Map<?,?> m){List<String> parts=new ArrayList<>();m.forEach((k,v)->parts.add(write(k.toString())+":"+write(v)));return "{"+String.join(",",parts)+"}";}
            if(o instanceof List<?> l){List<String> parts=new ArrayList<>();for(Object x:l)parts.add(write(x));return "["+String.join(",",parts)+"]";}
            throw new IllegalArgumentException("Unsupported JSON type");}
        private static String escape(String s){StringBuilder b=new StringBuilder();for(int j=0;j<s.length();j++){char c=s.charAt(j);
                switch(c){case '"'->b.append("\\\"");case '\\'->b.append("\\\\");case '\n'->b.append("\\n");case '\r'->b.append("\\r");case '\t'->b.append("\\t");default->{if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}
            }return b.toString();}
    }
}
