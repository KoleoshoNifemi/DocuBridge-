package Group12;

import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.function.Consumer;

/**
 * CollabClient - connects one user's Editor to the CollabServer.
 *
 * Usage:
 *   CollabClient client = new CollabClient(serverUri, username, fileName, quillEngine);
 *   client.connect();
 *
 * The editor.html page calls window.collabBridge.sendDelta(deltaJson) on every local change.
 * Incoming deltas from the server are applied via quill.updateContents() in the WebView.
 */

public class CollabClient extends WebSocketClient {

    private final String username;
    private final String fileName;
    private final WebEngine quillEngine;

    // Called when the active user list changes — use to update the UI
    private Consumer<String[]> onUsersChanged;

    // Flag to prevent echo: when we're applying a remote delta, don't re-send it
    private volatile boolean applyingRemote = false;

    public CollabClient(URI serverUri, String username, String fileName, WebEngine quillEngine) {
        super(serverUri);
        this.username   = username;
        this.fileName   = fileName;
        this.quillEngine = quillEngine;
    }

    public void setOnUsersChanged(Consumer<String[]> callback) {
        this.onUsersChanged = callback;
    }

    // ── WebSocketClient callbacks ─────────────────────────────────────────────

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("✓ Connected to CollabServer");

        // Announce ourselves and which file we're editing
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
            String type = msg.getString("type");

            switch (type) {
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

    /**
     * Called by the JS bridge (editor.html) whenever the local user makes a change.
     * deltaJson is a Quill Delta as a JSON string, e.g. {"ops":[{"insert":"hello"}]}
     */
    public void sendDelta(String deltaJson) {
        if (applyingRemote || !isOpen()) return;

        JSONObject msg = new JSONObject();
        msg.put("type",     "delta");
        msg.put("fileName", fileName);
        msg.put("delta",    deltaJson);
        send(msg.toString());
    }

    /**
     * Send the full document content to the server so late joiners can sync.
     * Called from Editor.java periodically (e.g. on autosave).
     */
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
                // Escape the JSON string safely for injection into JS
                String escaped = escapeForJs(deltaJson);
                quillEngine.executeScript(
                    "(function(){" +
                    "  var delta = JSON.parse(\"" + escaped + "\");" +
                    "  quill.updateContents(delta, 'api');" +   // 'api' source prevents re-triggering text-change
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
        for (int i = 0; i < users.length(); i++) {
            userArray[i] = users.getString(i);
        }

        Platform.runLater(() -> onUsersChanged.accept(userArray));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isApplyingRemote() {
        return applyingRemote;
    }

    /**
     * Escapes a JSON string for safe injection into a JS string literal wrapped in double quotes.
     */
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
     * Convenience factory. serverHost is just an IP or hostname, e.g. "192.168.1.5"
     */
    public static CollabClient create(String serverHost, String username, String fileName, WebEngine engine) {
        try {
            URI uri = new URI("ws://" + serverHost + ":" + CollabServer.PORT);
            return new CollabClient(uri, username, fileName, engine);
        } catch (Exception e) {
            throw new RuntimeException("Invalid server URI: " + e.getMessage(), e);
        }
    }
}
