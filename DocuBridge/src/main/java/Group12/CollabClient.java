package Group12;

import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.function.Consumer;

/**
 * CollabClient - connects one user's Editor to the CollabServer.
 *
 * Supports two connection modes automatically:
 *   ws://  — same WiFi (room code)
 *   wss:// — different networks via localhost.run SSH tunnel
 *
 * Same-WiFi usage:
 *   CollabClient.create("192.168.1.5", username, fileName, engine)
 *
 * localhost.run usage:
 *   Host runs: ssh -R 80:localhost:8765 nokey@localhost.run
 *   Host shares the URL it gives (e.g. abc123.lhr.life)
 *   Joiner enters that URL in the Join field
 *   CollabClient.create("abc123.lhr.life", username, fileName, engine)
 */
public class CollabClient extends WebSocketClient {

    private final String username;
    private final String fileName;
    private final WebEngine quillEngine;

    private Consumer<String[]> onUsersChanged;
    private volatile boolean applyingRemote = false;

    public CollabClient(URI serverUri, String username, String fileName, WebEngine quillEngine) {
        super(serverUri);
        this.username    = username;
        this.fileName    = fileName;
        this.quillEngine = quillEngine;
    }

    public void setOnUsersChanged(Consumer<String[]> callback) {
        this.onUsersChanged = callback;
    }

    // ── WebSocketClient callbacks ─────────────────────────────────────────────

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("✓ Connected to CollabServer");
        JSONObject joinMsg = new JSONObject();
        joinMsg.put("type",     "join");
        joinMsg.put("fileName", fileName);
        joinMsg.put("username", username);
        send(joinMsg.toString());
    }

    @Override
    public void onMessage(String message) {
        try {
            JSONObject msg = new JSONObject(message);
            switch (msg.getString("type")) {
                case "delta"    -> applyRemoteDelta(msg.getString("delta"), msg.getString("username"));
                case "full"     -> applyFullContent(msg.getString("content"));
                case "userlist" -> handleUserList(msg.getJSONArray("users"));
            }
        } catch (Exception e) {
            System.err.println("CollabClient message error: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("CollabClient disconnected: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("CollabClient error: " + ex.getMessage());
    }

    // ── Sending ───────────────────────────────────────────────────────────────

    public void sendDelta(String deltaJson) {
        if (applyingRemote || !isOpen()) return;
        JSONObject msg = new JSONObject();
        msg.put("type",     "delta");
        msg.put("fileName", fileName);
        msg.put("delta",    deltaJson);
        send(msg.toString());
    }

    public void sendFullContent(String contentJson) {
        if (!isOpen()) return;
        JSONObject msg = new JSONObject();
        msg.put("type",     "full");
        msg.put("fileName", fileName);
        msg.put("content",  contentJson);
        send(msg.toString());
    }

    // ── Receiving ─────────────────────────────────────────────────────────────

    private void applyRemoteDelta(String deltaJson, String fromUser) {
        Platform.runLater(() -> {
            applyingRemote = true;
            try {
                String escaped = escapeForJs(deltaJson);
                quillEngine.executeScript(
                        "(function(){" +
                                "  var delta = JSON.parse(\"" + escaped + "\");" +
                                "  quill.updateContents(delta, 'api');" +
                                "})();"
                );
            } catch (Exception e) {
                System.err.println("Failed to apply remote delta: " + e.getMessage());
            } finally {
                applyingRemote = false;
            }
        });
    }

    private void applyFullContent(String contentJson) {
        Platform.runLater(() -> {
            applyingRemote = true;
            try {
                String escaped = escapeForJs(contentJson);
                quillEngine.executeScript(
                        "(function(){" +
                                "  var delta = JSON.parse(\"" + escaped + "\");" +
                                "  quill.setContents(delta, 'api');" +
                                "})();"
                );
                System.out.println("✓ Synced full document from server");
            } catch (Exception e) {
                System.err.println("Failed to apply full content: " + e.getMessage());
            } finally {
                applyingRemote = false;
            }
        });
    }

    private void handleUserList(JSONArray users) {
        if (onUsersChanged == null) return;
        String[] userArray = new String[users.length()];
        for (int i = 0; i < users.length(); i++) userArray[i] = users.getString(i);
        Platform.runLater(() -> onUsersChanged.accept(userArray));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isApplyingRemote() { return applyingRemote; }

    private static String escapeForJs(String json) {
        return json
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n",  "\\n")
                .replace("\r",  "\\r")
                .replace("\t",  "\\t");
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Creates and returns a CollabClient connected to the given host.
     *
     * Automatically picks the right protocol:
     *   - "localhost" or plain IP (e.g. "192.168.1.5")  → ws://
     *   - hostname with dots (e.g. "abc123.lhr.life")   → wss://
     *   - host:port format (e.g. "abc123.lhr.life:443") → wss://
     *
     * For localhost.run, the SSH command gives you a URL like:
     *   https://abc123.lhr.life
     * Just paste "abc123.lhr.life" (without https://) into the Join field.
     */
    public static CollabClient create(String serverHost, String username, String fileName, WebEngine engine) {
        try {
            URI uri = buildUri(serverHost);
            CollabClient client = new CollabClient(uri, username, fileName, engine);

            // If using wss:// (localhost.run), set up SSL to trust the tunnel's cert
            if (uri.getScheme().equals("wss")) {
                client.setSocketFactory(buildTrustAllSSLContext().getSocketFactory());
            }

            return client;
        } catch (Exception e) {
            throw new RuntimeException("Could not create CollabClient: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the correct WebSocket URI from a host string.
     *
     * Rules:
     *   localhost / 127.0.0.1 / plain IP  → ws://host:8765
     *   anything else (domain name)        → wss://host:443
     *   host:port already specified        → use as-is with correct scheme
     */
    private static URI buildUri(String host) throws Exception {
        // Strip any leading protocol if someone accidentally pastes a full URL
        host = host.replaceFirst("^https?://", "").replaceFirst("^wss?://", "").trim();

        boolean hasPort   = host.contains(":");
        boolean isLocal   = host.equals("localhost") || host.equals("127.0.0.1") || host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");

        if (isLocal) {
            // Same-WiFi / local machine — plain WebSocket
            String portedHost = hasPort ? host : host + ":" + CollabServer.PORT;
            return new URI("ws://" + portedHost);
        } else {
            // External tunnel (localhost.run) — connects on port 80, TLS handled by tunnel
            String portedHost = hasPort ? host : host + ":80";
            return new URI("ws://" + portedHost);
        }
    }

    /**
     * Builds an SSL context that trusts all certificates.
     * Needed for localhost.run's tunnel certificates which may not be in the JVM truststore.
     */
    private static SSLContext buildTrustAllSSLContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new java.security.SecureRandom());
        return ctx;
    }
}