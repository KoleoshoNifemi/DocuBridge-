package Group12;

import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * CollabClient - connects one user's Editor to the CollabServer.
 *
 * Same-WiFi: enter room code e.g. BRIDGE-4821
 * Different WiFi: host runs  ngrok tcp 8765  and shares the address
 *                 e.g. 0.tcp.ngrok.io:12345 - teammates paste that into Join field.
 */
public class CollabClient extends WebSocketClient {

    private final String username;
    private String fileName;
    private final WebEngine quillEngine;

    //callbacks wired up by Editor so the UI can react to server events
    private Consumer<String[]> onUsersChanged;
    private Consumer<String>   onFileNameChanged;
    //flag to prevent sending our own deltas back while we're applying someone else's
    private volatile boolean applyingRemote = false;
    //tracks who's currently in the room so we can detect departures and remove their cursors
    private List<String> knownUsers = new ArrayList<>();

    public CollabClient(URI serverUri, String username, String fileName, WebEngine quillEngine) {
        super(serverUri);
        this.username    = username;
        this.fileName    = fileName;
        this.quillEngine = quillEngine;
    }

    public void setOnUsersChanged(Consumer<String[]> callback) {
        this.onUsersChanged = callback;
    }

    public void setOnFileNameChanged(Consumer<String> callback) {
        this.onFileNameChanged = callback;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        //System.out.println("✓ Connected to CollabServer");
        //immediately announce ourselves so the server puts us in a room
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
            //dispatch to the right handler based on message type
            switch (msg.getString("type")) {
                case "joined"   -> handleJoined(msg.getString("fileName"));
                case "delta"    -> applyRemoteDelta(msg.getString("delta"), msg.getString("username"));
                case "full"     -> applyFullContent(msg.getString("content"));
                case "userlist" -> handleUserList(msg.getJSONArray("users"));
                case "cursor"   -> applyRemoteCursor(msg.getString("username"), msg.getInt("index"), msg.optInt("length", 0));
            }
        } catch (Exception e) {
            System.err.println("CollabClient message error: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        //System.out.println("CollabClient disconnected: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("CollabClient error: " + ex.getMessage());
    }

    public void sendCursor(int index, int length) {
        if (!isOpen()) return;
        JSONObject msg = new JSONObject();
        msg.put("type",     "cursor");
        msg.put("fileName", fileName);
        msg.put("index",    index);
        msg.put("length",   length);
        send(msg.toString());
    }

    public void sendDelta(String deltaJson) {
        //System.out.println("DEBUG sendDelta: isOpen=" + isOpen());
        //skip if we're currently applying a remote delta - otherwise we'd echo it back
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
        //System.out.println("DEBUG applyRemoteDelta called from " + fromUser);
        //all Quill JS calls must happen on the JavaFX application thread
        Platform.runLater(() -> {
            try {
                //pass the delta via setMember rather than string-interpolating it into JS
                //to avoid JSON escaping issues breaking the parse
                JSObject win = (JSObject) quillEngine.executeScript("window");
                win.setMember("_pendingDelta", deltaJson);
                quillEngine.executeScript(
                        "(function(){" +
                                "  if (!window._pendingDelta) return;" +
                                "  try {" +
                                "    var incoming = JSON.parse(window._pendingDelta);" +
                                "    quill.updateContents(incoming, 'api');" +
                                //if there's a local baseline stored (_originalDelta), compose
                                //the incoming delta onto it to keep the baseline up to date
                                "    if (window._originalDelta) {" +
                                "      try {" +
                                "        var Delta = Quill.import('delta');" +
                                "        var orig = new Delta(JSON.parse(window._originalDelta));" +
                                "        window._originalDelta = JSON.stringify(orig.compose(new Delta(incoming)));" +
                                "      } catch(ce) {}" +
                                "    }" +
                                "  } catch(e) {" +
                                "    console.error('applyRemoteDelta failed: ' + e.message);" +
                                "  }" +
                                "  window._pendingDelta = null;" +
                                "})()"
                );
                //System.out.println("DEBUG applyRemoteDelta: applied");
            } catch (Exception e) {
                System.err.println("Failed to apply remote delta: " + e.getMessage());
            }
        });
    }

    private void applyFullContent(String contentJson) {
        Platform.runLater(() -> {
            try {
                // Use JSObject.setMember to pass content directly - avoids JS string
                // escaping issues that silently break JSON.parse on certain characters.
                JSObject win = (JSObject) quillEngine.executeScript("window");
                win.setMember("_pendingFullContent", contentJson);
                quillEngine.executeScript(
                        "(function(){" +
                                "  if (!window._pendingFullContent) return;" +
                                "  try {" +
                                //also replace the local baseline so undo/redo tracks correctly
                                "    if (window._originalDelta) {" +
                                "      window._originalDelta = window._pendingFullContent;" +
                                "    }" +
                                "    quill.setContents(JSON.parse(window._pendingFullContent), 'api');" +
                                "  } catch(e) {" +
                                "    console.error('applyFullContent failed: ' + e.message);" +
                                "  }" +
                                "  window._pendingFullContent = null;" +
                                //focus so the user can start typing immediately after sync
                                "  quill.focus();" +
                                "})()"
                );
                //System.out.println("✓ Synced full document from server");
            } catch (Exception e) {
                System.err.println("Failed to apply full content: " + e.getMessage());
            }
        });
    }

    private void applyRemoteCursor(String fromUser, int index, int length) {
        Platform.runLater(() -> {
            try {
                // Pass username via setMember (handles special chars), but embed
                // the index as a literal number in the JS string - if passed via
                // setMember, JavaFX wraps it as a Java Integer object, making
                // typeof index === "object" instead of "number", which causes
                // Quill's getBounds to take the wrong branch and return null.
                JSObject win = (JSObject) quillEngine.executeScript("window");
                win.setMember("_cursorUser", fromUser);
                quillEngine.executeScript(
                    "(function(){" +
                    "  if (typeof window.updateRemoteCursor === 'function')" +
                    "    window.updateRemoteCursor(window._cursorUser, " + index + ");" +
                    "})()"
                );
            } catch (Exception e) {
                System.err.println("Failed to apply remote cursor: " + e.getMessage());
            }
        });
    }

    private void handleJoined(String serverFileName) {
        //server may redirect us to a different room than we asked for - keep our local fileName in sync
        if (!this.fileName.equals(serverFileName)) {
            //System.out.println("✓ Redirected to file: " + serverFileName);
            this.fileName = serverFileName;
            if (onFileNameChanged != null) {
                Platform.runLater(() -> onFileNameChanged.accept(serverFileName));
            }
        }
    }

    private void handleUserList(JSONArray users) {
        //build a set of current users for fast lookup
        Set<String> currentSet = new HashSet<>();
        for (int i = 0; i < users.length(); i++) currentSet.add(users.getString(i));

        //find anyone who was in knownUsers but isn't anymore - their cursor needs to be removed
        List<String> departed = new ArrayList<>();
        for (String u : knownUsers) {
            if (!currentSet.contains(u) && !u.equals(username)) departed.add(u);
        }
        if (!departed.isEmpty()) {
            Platform.runLater(() -> {
                for (String u : departed) {
                    try {
                        //use setMember so usernames with special characters don't break the JS
                        JSObject win = (JSObject) quillEngine.executeScript("window");
                        win.setMember("_removeCursorUser", u);
                        quillEngine.executeScript(
                            "if (typeof window.removeRemoteCursor === 'function')" +
                            "  window.removeRemoteCursor(window._removeCursorUser);"
                        );
                    } catch (Exception ignored) {}
                }
            });
        }

        //update our local snapshot of who's in the room
        knownUsers = new ArrayList<>(currentSet);

        if (onUsersChanged == null) return;
        //convert to a plain array for the callback (Editor uses it to update the UI panel)
        String[] userArray = new String[users.length()];
        for (int i = 0; i < users.length(); i++) userArray[i] = users.getString(i);
        Platform.runLater(() -> onUsersChanged.accept(userArray));
    }

    public boolean isApplyingRemote() { return applyingRemote; }

    //kept for reference, but content is now passed via setMember to avoid needing this
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
            //strip any accidental protocol prefix the user may have typed
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
