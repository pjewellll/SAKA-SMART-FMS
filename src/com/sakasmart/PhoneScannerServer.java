package com.sakasmart;

import com.sun.net.httpserver.*;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Tiny embedded web server so a cellphone on the same Wi-Fi can act as the barcode scanner.
 *
 * - Serves ONE page (the camera scanner) and accepts ONE kind of request (a scanned barcode).
 * - Every URL contains a random session token; it exists only while the scanner window is open.
 * - Uses HTTPS (self-signed certificate created on first use) because phone browsers only allow
 *   camera access on secure pages. If the certificate cannot be created it falls back to HTTP,
 *   where the phone page still works for typing a barcode.
 * - The server only reports barcodes. All inventory decisions are made in the desktop app.
 */
final class PhoneScannerServer {
    record ScanResult(String status, String message) {}

    private static final String KEYSTORE_PASSWORD = "saka-smart-scanner";
    private static final int FIRST_PORT = 8443, LAST_PORT = 8453;
    private static final int MAX_BAD_REQUESTS = 40;

    private final Function<String, ScanResult> listener;
    private final String token = newToken();
    private final AtomicInteger badRequests = new AtomicInteger();
    private final SecureRandom random = new SecureRandom();
    private HttpServer server;
    private ExecutorService executor;
    private int port;
    private boolean secure;
    private String warning = "";

    PhoneScannerServer(Function<String, ScanResult> listener) {
        this.listener = listener;
    }

    // ------------------------------------------------------------------ lifecycle
    void start() throws IOException {
        HttpsConfigurator https = null;
        try {
            https = httpsConfigurator();
        } catch (Exception ex) {
            warning = "Secure (HTTPS) mode is unavailable: " + ex.getMessage()
                + ". The phone camera may be blocked; typing a barcode still works.";
        }
        IOException last = null;
        for (int p = FIRST_PORT; p <= LAST_PORT && server == null; p++) {
            try {
                InetSocketAddress address = new InetSocketAddress(p);
                if (https != null) {
                    HttpsServer s = HttpsServer.create(address, 0);
                    s.setHttpsConfigurator(https);
                    server = s;
                    secure = true;
                } else {
                    server = HttpServer.create(address, 0);
                }
                port = p;
            } catch (IOException ex) {
                last = ex;
                server = null;
            }
        }
        if (server == null) throw new IOException("No free port between " + FIRST_PORT + " and "
            + LAST_PORT + (last == null ? "" : " (" + last.getMessage() + ")"));
        executor = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "saka-phone-scanner");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    void stop() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
        server = null;
    }

    boolean running() { return server != null; }
    boolean isSecure() { return secure; }
    String warning() { return warning; }
    int port() { return port; }

    /** Every address the phone could use (one per network adapter). */
    List<String> urls() {
        List<String> out = new ArrayList<>();
        for (String ip : localAddresses())
            out.add((secure ? "https://" : "http://") + ip + ":" + port + "/s/" + token);
        return out;
    }

    // ------------------------------------------------------------------ requests
    private void handle(HttpExchange ex) throws IOException {
        try {
            if (badRequests.get() >= MAX_BAD_REQUESTS) { send(ex, 429, "text/plain", "Too many attempts."); return; }
            String path = ex.getRequestURI().getPath();
            String prefix = "/s/" + token;
            boolean page = path.equals(prefix) || path.equals(prefix + "/");
            boolean scan = path.equals(prefix + "/scan");
            if (!page && !scan) {
                badRequests.incrementAndGet();
                send(ex, 404, "text/plain", "Not found.");
                return;
            }
            if (page && "GET".equals(ex.getRequestMethod())) {
                byte[] nonceBytes = new byte[12];
                random.nextBytes(nonceBytes);
                String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
                ex.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'none'; script-src 'nonce-" + nonce + "' https://cdn.jsdelivr.net; "
                    + "style-src 'unsafe-inline'; connect-src 'self'; media-src blob: 'self'; "
                    + "img-src data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
                send(ex, 200, "text/html; charset=utf-8", PAGE.replace("%%NONCE%%", nonce).replace("%%TOKEN%%", token));
                return;
            }
            if (scan && "POST".equals(ex.getRequestMethod())) {
                String code = readBody(ex, 200).trim();
                if (!validBarcode(code)) {
                    send(ex, 400, "application/json", json("ERROR", "That does not look like a barcode."));
                    return;
                }
                ScanResult result;
                try {
                    result = listener.apply(code);
                } catch (Exception e) {
                    result = new ScanResult("ERROR", "The computer could not process this scan.");
                }
                send(ex, 200, "application/json", json(result.status(), result.message()));
                return;
            }
            send(ex, 405, "text/plain", "Method not allowed.");
        } catch (Exception e) {
            try { send(ex, 500, "text/plain", "Server error."); } catch (Exception ignored) { }
        } finally {
            ex.close();
        }
    }

    static boolean validBarcode(String code) {
        if (code.length() < 3 || code.length() > 64) return false;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || c == '-' || c == '.' || c == '_' || c == '/' || c == '+' || c == '$' || c == '%' || c == ' ';
            if (!ok) return false;
        }
        return true;
    }

    private static String readBody(HttpExchange ex, int max) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readNBytes(max + 1);
            if (data.length > max) return "";
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private static String json(String status, String message) {
        StringBuilder sb = new StringBuilder("{\"status\":\"").append(status).append("\",\"message\":\"");
        for (char c : message.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 0x20) sb.append(' ');
            else sb.append(c);
        }
        return sb.append("\"}").toString();
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", type);
        h.set("Cache-Control", "no-store");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Referrer-Policy", "no-referrer");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
    }

    // ------------------------------------------------------------------ token + network
    private static String newToken() {
        String alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no look-alike characters
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        return sb.toString();
    }

    static List<String> localAddresses() {
        List<String> preferred = new ArrayList<>(), others = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                String name = (nic.getDisplayName() + " " + nic.getName()).toLowerCase(Locale.ROOT);
                boolean virtualName = name.contains("virtual") || name.contains("vmware") || name.contains("vbox")
                    || name.contains("hyper-v") || name.contains("vethernet") || name.contains("docker")
                    || name.contains("wsl") || name.contains("loopback") || name.contains("bluetooth");
                for (InetAddress a : Collections.list(nic.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress() || a.isLinkLocalAddress()) continue;
                    (virtualName || !a.isSiteLocalAddress() ? others : preferred).add(a.getHostAddress());
                }
            }
        } catch (SocketException ignored) { }
        List<String> all = new ArrayList<>(preferred);
        all.addAll(others);
        return all;
    }

    // ------------------------------------------------------------------ HTTPS certificate
    private HttpsConfigurator httpsConfigurator() throws Exception {
        Path dir = Paths.get(System.getProperty("user.home"), ".saka-smart");
        Files.createDirectories(dir);
        Path keystore = dir.resolve("scanner-keystore.p12");
        if (!Files.exists(keystore)) createKeystore(keystore);
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keystore)) { ks.load(in, KEYSTORE_PASSWORD.toCharArray()); }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, KEYSTORE_PASSWORD.toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), null, null);
        return new HttpsConfigurator(context);
    }

    /**
     * Creates the self-signed certificate. Tries, in order: the JDK's own keytool launcher, the same tool
     * started through this runtime's java (works in a trimmed installer runtime that has no keytool.exe),
     * and finally a keytool found on the PATH.
     */
    private static void createKeystore(Path keystore) throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path bin = Paths.get(System.getProperty("java.home"), "bin");
        Path keytool = bin.resolve(windows ? "keytool.exe" : "keytool");
        Path java = bin.resolve(windows ? "java.exe" : "java");
        StringBuilder san = new StringBuilder("san=dns:localhost,ip:127.0.0.1");
        for (String ip : localAddresses()) san.append(",ip:").append(ip);
        List<String> args = List.of("-genkeypair", "-alias", "saka-scanner",
            "-keyalg", "RSA", "-keysize", "2048", "-sigalg", "SHA256withRSA", "-validity", "825",
            "-storetype", "PKCS12", "-keystore", keystore.toString(),
            "-storepass", KEYSTORE_PASSWORD, "-keypass", KEYSTORE_PASSWORD,
            "-dname", "CN=SAKA-SMART Phone Scanner, O=SAKA-SMART", "-ext", san.toString());

        List<List<String>> attempts = new ArrayList<>();
        if (Files.isExecutable(keytool)) attempts.add(prefixed(List.of(keytool.toString()), args));
        if (Files.isExecutable(java)) attempts.add(prefixed(List.of(java.toString(),
            "--add-exports", "java.base/sun.security.tools.keytool=ALL-UNNAMED",
            "sun.security.tools.keytool.Main"), args));
        attempts.add(prefixed(List.of("keytool"), args));

        String lastError = "keytool was not found";
        for (List<String> command : attempts) {
            try {
                Files.deleteIfExists(keystore);
                Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
                String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (!p.waitFor(40, TimeUnit.SECONDS)) { p.destroyForcibly(); lastError = "keytool timed out"; continue; }
                if (p.exitValue() == 0 && Files.exists(keystore)) return;
                lastError = "keytool failed: " + output.trim();
            } catch (IOException ex) {
                lastError = "keytool could not be started";
            }
        }
        throw new IOException(lastError);
    }

    private static List<String> prefixed(List<String> prefix, List<String> args) {
        List<String> all = new ArrayList<>(prefix);
        all.addAll(args);
        return all;
    }

    // ------------------------------------------------------------------ phone page
    private static final String PAGE = """
<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<meta name="theme-color" content="#0a4d2d">
<title>SAKA-SMART Scanner</title>
<style>
*{box-sizing:border-box}
body{margin:0;font-family:system-ui,Segoe UI,Roboto,Arial,sans-serif;background:#0a4d2d;color:#fff;min-height:100vh}
header{padding:14px 16px 6px}
h1{margin:0;font-size:19px}
header p{margin:4px 0 0;font-size:13px;color:#bfe8cb}
#box{position:relative;margin:10px 14px;border-radius:16px;overflow:hidden;background:#000;aspect-ratio:4/3}
video{width:100%;height:100%;object-fit:cover;display:block}
.aim{position:absolute;left:12%;right:12%;top:34%;bottom:34%;border:3px solid #7CFF9E;border-radius:12px;box-shadow:0 0 0 999px rgba(0,0,0,.28)}
#msg{margin:6px 16px;font-size:14px;color:#d8f5df;min-height:20px}
#result{margin:8px 14px;padding:12px 14px;border-radius:12px;font-size:15px;font-weight:600;background:#14682f;display:none}
#result.err{background:#8e2430}
#result.ok{background:#1d8a45}
form{display:flex;gap:8px;margin:12px 14px 24px}
input{flex:1;min-width:0;padding:13px;border-radius:12px;border:0;font-size:16px}
button{padding:13px 18px;border-radius:12px;border:0;background:#7CFF9E;color:#073b22;font-weight:700;font-size:16px}
</style></head><body>
<header><h1>SAKA-SMART Barcode Scanner</h1>
<p>Point the camera at a product barcode. The result appears on the farm computer.</p></header>
<div id="box"><video id="v" playsinline muted autoplay></video><div class="aim"></div></div>
<div id="msg">Starting camera...</div>
<div id="result"></div>
<form id="f"><input id="code" autocomplete="off" autocapitalize="characters" placeholder="Or type the barcode number"><button type="submit">Send</button></form>
<script nonce="%%NONCE%%">
(function () {
  var SEND_URL = '/s/%%TOKEN%%/scan';
  var video = document.getElementById('v'), msg = document.getElementById('msg');
  var resultEl = document.getElementById('result'), input = document.getElementById('code');
  var last = '', lastAt = 0;

  function show(text, cls) {
    resultEl.style.display = 'block';
    resultEl.className = cls || '';
    resultEl.textContent = text;
  }
  function send(raw) {
    var code = (raw || '').trim();
    if (!code) return;
    var now = Date.now();
    if (code === last && now - lastAt < 4000) return;
    last = code; lastAt = now;
    if (navigator.vibrate) navigator.vibrate(80);
    show('Sending ' + code + '...', '');
    fetch(SEND_URL, {method: 'POST', headers: {'Content-Type': 'text/plain'}, body: code})
      .then(function (r) { return r.json(); })
      .then(function (j) { show(j.message || 'Sent.', j.status === 'ERROR' ? 'err' : 'ok'); })
      .catch(function () { show('Could not reach the computer. Check that both are on the same Wi-Fi.', 'err'); });
  }
  document.getElementById('f').addEventListener('submit', function (e) {
    e.preventDefault(); send(input.value); input.value = '';
  });

  function cameraProblem(err) {
    msg.textContent = 'Camera not available (' + (err && err.name ? err.name : 'blocked') +
      '). Allow camera access in the browser, or type the barcode below.';
  }
  function startNative() {
    var wanted = ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128', 'code_39', 'code_93', 'itf', 'codabar', 'qr_code'];
    BarcodeDetector.getSupportedFormats().then(function (supported) {
      var use = wanted.filter(function (f) { return supported.indexOf(f) >= 0; });
      var detector = new BarcodeDetector({formats: use});
      var busy = false;
      setInterval(function () {
        if (busy || video.readyState < 2) return;
        busy = true;
        detector.detect(video).then(function (codes) {
          if (codes.length) send(codes[0].rawValue);
        }).catch(function () {}).then(function () { busy = false; });
      }, 250);
      msg.textContent = 'Ready. Hold the barcode inside the green box.';
    });
  }
  function startFallback() {
    msg.textContent = 'Loading scanner...';
    var s = document.createElement('script');
    s.src = 'https://cdn.jsdelivr.net/npm/@zxing/library@0.21.3/umd/index.min.js';
    s.onload = function () {
      try {
        var reader = new ZXing.BrowserMultiFormatReader();
        var cb = function (res) { if (res) send(res.getText()); };
        if (reader.decodeFromConstraints) reader.decodeFromConstraints({video: {facingMode: 'environment'}}, 'v', cb);
        else reader.decodeFromVideoDevice(undefined, 'v', cb);
        msg.textContent = 'Ready. Hold the barcode inside the green box.';
      } catch (err) { cameraProblem(err); }
    };
    s.onerror = function () {
      msg.textContent = 'Automatic scanning needs internet on this phone. You can still type the barcode below.';
    };
    document.head.appendChild(s);
  }

  if (!window.isSecureContext || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    msg.textContent = 'The camera needs a secure (https) page. Open the https link and accept the warning, or type the barcode below.';
    return;
  }
  if ('BarcodeDetector' in window) {
    navigator.mediaDevices.getUserMedia({video: {facingMode: {ideal: 'environment'}}, audio: false})
      .then(function (stream) { video.srcObject = stream; return video.play(); })
      .then(startNative)
      .catch(cameraProblem);
  } else {
    startFallback();
  }
})();
</script></body></html>
""";
}
