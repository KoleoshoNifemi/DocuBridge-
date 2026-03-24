package Group12;

import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.function.Consumer;

/**
 * CollabClient - connects one user's Editor to the CollabServer.
 *
 * Same-WiFi: enter room code e.g. BRIDGE-4821
 * Different WiFi: host runs  ngrok tcp 8765  and shares the address
 *                 e.g. 0.tcp.ngrok.io:12345 — teammates paste that into Join field.
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

    public void sendDelta(String deltaJson) {
        System.out.println("DEBUG sendDelta: isOpen=" + isOpen());
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

    private void applyRemoteDelta(String deltaJson, String fromUser) {
        System.out.println("DEBUG applyRemoteDelta called from " + fromUser);
        Platform.runLater(() -> {
            try {
                JSObject win = (JSObject) quillEngine.executeScript("window");
                win.setMember("_pendingDelta", deltaJson);
                quillEngine.executeScript(
                        "(function(){" +
                                "  if (!window._pendingDelta) return;" +
                                "  try {" +
                                "    quill.updateContents(JSON.parse(window._pendingDelta), 'api');" +
                                "  } catch(e) {" +
                                "    console.error('applyRemoteDelta failed: ' + e.message);" +
                                "  }" +
                                "  window._pendingDelta = null;" +
                                "})()"
                );
                System.out.println("DEBUG applyRemoteDelta: applied");
            } catch (Exception e) {
                System.err.println("Failed to apply remote delta: " + e.getMessage());
            }
        });
    }

    private void applyFullContent(String contentJson) {
        Platform.runLater(() -> {
            try {
                // Use JSObject.setMember to pass content directly — avoids JS string
                // escaping issues that silently break JSON.parse on certain characters.
                JSObject win = (JSObject) quillEngine.executeScript("window");
                win.setMember("_pendingFullContent", contentJson);
                quillEngine.executeScript(
                        "(function(){" +
                                "  if (!window._pendingFullContent) return;" +
                                "  try {" +
                                "    quill.setContents(JSON.parse(window._pendingFullContent), 'api');" +
                                "  } catch(e) {" +
                                "    console.error('applyFullContent failed: ' + e.message);" +
                                "  }" +
                                "  window._pendingFullContent = null;" +
                                "})()"
                );
                System.out.println("✓ Synced full document from server");
            } catch (Exception e) {
                System.err.println("Failed to apply full content: " + e.getMessage());
            }
        });
    }

    private void handleUserList(JSONArray users) {
        if (onUsersChanged == null) return;
        String[] userArray = new String[users.length()];
        for (int i = 0; i < users.length(); i++) userArray[i] = users.getString(i);
        Platform.runLater(() -> onUsersChanged.accept(userArray));
    }

    public boolean isApplyingRemote() { return applyingRemote; }

    private static String escapeForJs(String json) {
        return json
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n",  "\\n")
                .replace("\r",  "\\r")
                .replace("\t",  "\\t");
    }

    /**
     * Builds a ws:// URI from the host string.
     *
     * Handles:
     *   "localhost"           → ws://localhost:8765       (same machine)
     *   "192.168.1.5"         → ws://192.168.1.5:8765     (same WiFi)
     *   "0.tcp.ngrok.io:12345"→ ws://0.tcp.ngrok.io:12345 (ngrok, port already included)
     */
    public static CollabClient create(String serverHost, String username, String fileName, WebEngine engine) {
        try {
            // Strip any accidental protocol prefix
            serverHost = serverHost.replaceFirst("^https?://", "").replaceFirst("^wss?://", "").trim();

            // If host already includes a port (e.g. ngrok gives host:port), use as-is
            // Otherwise append the default CollabServer port
            boolean hasPort = serverHost.contains(":");
            String fullHost = hasPort ? serverHost : serverHost + ":" + CollabServer.PORT;

            URI uri = new URI("ws://" + fullHost);
            return new CollabClient(uri, username, fileName, engine);
        } catch (Exception e) {
            throw new RuntimeException("Could not create CollabClient: " + e.getMessage(), e);
        }
    }
}