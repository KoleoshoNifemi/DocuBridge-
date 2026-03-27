package Group12;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * CollabServer - WebSocket server for real-time document collaboration.
 *
 * ONE person on the team runs this (selects "Host"). Everyone else joins
 * using the room code shown to the host.
 *
 * Room codes encode the host's local IP so joiners don't need to know it.
 * Format: "BRIDGE-XXXX" where XXXX is derived from the host IP.
 *
 * For cross-network (different WiFis): host runs  ngrok tcp 8765
 * and shares the ngrok address (e.g. 0.tcp.ngrok.io:12345) instead.
 * Teammates paste that directly into the Join field.
 *
 * Message protocol (JSON):
 *   Client -> Server:
 *     { "type": "join",   "fileName": "myfile.docx", "username": "tharun" }
 *     { "type": "delta",  "fileName": "myfile.docx", "delta": <quill delta JSON> }
 *     { "type": "full",   "fileName": "myfile.docx", "content": <full delta JSON> }
 *     { "type": "cursor", "fileName": "myfile.docx", "index": 42, "length": 0 }
 *
 *   Server -> Client:
 *     { "type": "delta",    "fileName": "...", "delta": ..., "username": "..." }
 *     { "type": "full",     "fileName": "...", "content": ..., "username": "..." }
 *     { "type": "userlist", "fileName": "...", "users": ["tharun", "jane", ...] }
 *     { "type": "cursor",   "fileName": "...", "username": "...", "index": 42, "length": 0 }
 */
public class CollabServer extends WebSocketServer {

    public static final int PORT = 8765;

    //each document gets its own set of connected sockets (the "room")
    private final Map<String, Set<WebSocket>> documentRooms = Collections.synchronizedMap(new HashMap<>());
    //last known full content per document - used to sync late joiners
    private final Map<String, String>         latestContent  = Collections.synchronizedMap(new HashMap<>());
    //maps each connection to the username that joined on it
    private final Map<WebSocket, String>      usernames      = Collections.synchronizedMap(new HashMap<>());
    //maps each connection to the document room it's in
    private final Map<WebSocket, String>      connToFile     = Collections.synchronizedMap(new HashMap<>());

    public CollabServer() {
        super(new InetSocketAddress(PORT));
        //allow restarting the server quickly without waiting for the OS to release the port
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("✓ New connection from: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JSONObject msg = new JSONObject(message);
            //route to the right handler based on the "type" field
            switch (msg.getString("type")) {
                case "join"   -> handleJoin(conn, msg);
                case "delta"  -> handleDelta(conn, msg);
                case "full"   -> handleFull(conn, msg);
                case "cursor" -> handleCursor(conn, msg);
            }
        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
        }
    }

    private void handleJoin(WebSocket conn, JSONObject msg) {
        String requested = msg.getString("fileName");
        String username  = msg.getString("username");

        // If the requested room doesn't exist but another room is active, redirect the
        // joiner there automatically - they don't need to have the same file name.
        String fileName = requested;
        if (!documentRooms.containsKey(fileName) && !documentRooms.isEmpty()) {
            fileName = documentRooms.keySet().iterator().next();
            System.out.println("  → Redirected " + username + " from '" + requested + "' to '" + fileName + "'");
        }

        //register the connection in all three lookup maps
        usernames.put(conn, username);
        connToFile.put(conn, fileName);
        documentRooms.computeIfAbsent(fileName, k -> Collections.synchronizedSet(new HashSet<>())).add(conn);

        System.out.println("✓ " + username + " joined: " + fileName);

        //tell the client which room they actually ended up in (may differ from what they requested)
        JSONObject ack = new JSONObject();
        ack.put("type",     "joined");
        ack.put("fileName", fileName);
        conn.send(ack.toString());

        //if there's already content in this room, send it to the new joiner so they start in sync
        if (latestContent.containsKey(fileName)) {
            JSONObject syncMsg = new JSONObject();
            syncMsg.put("type",     "full");
            syncMsg.put("fileName", fileName);
            syncMsg.put("content",  latestContent.get(fileName));
            syncMsg.put("username", "server");
            conn.send(syncMsg.toString());
            System.out.println("  → Sent current doc state to " + username);
        }

        //update the user list shown to everyone in the room
        broadcastUserList(fileName);
    }

    private void handleDelta(WebSocket conn, JSONObject msg) {
        String fileName = msg.getString("fileName");
        String user = usernames.getOrDefault(conn, "unknown");
        //stamp the sender's username onto the message before forwarding
        msg.put("username", user);
        System.out.println("DEBUG server: delta from " + user);

        //forward the delta to everyone else in the room - not back to the sender
        Set<WebSocket> room = documentRooms.get(fileName);
        if (room != null) {
            synchronized (room) {
                for (WebSocket client : room) {
                    if (client != conn && client.isOpen()) {
                        client.send(msg.toString());
                        System.out.println("DEBUG server: forwarded to " + usernames.getOrDefault(client, "?"));
                    }
                }
            }
        }
    }

    private void handleFull(WebSocket conn, JSONObject msg) {
        //just store the latest full content snapshot - no need to broadcast, this is for syncing late joiners
        latestContent.put(msg.getString("fileName"), msg.getString("content"));
    }

    private void handleCursor(WebSocket conn, JSONObject msg) {
        String fileName = connToFile.get(conn);
        if (fileName == null) return;
        //attach the username so receivers know whose cursor to render
        msg.put("username", usernames.getOrDefault(conn, "unknown"));
        Set<WebSocket> room = documentRooms.get(fileName);
        if (room == null) return;
        //broadcast cursor position to everyone else in the room
        synchronized (room) {
            for (WebSocket client : room) {
                if (client != conn && client.isOpen()) client.send(msg.toString());
            }
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        //clean up both lookup maps when a connection drops
        String username = usernames.remove(conn);
        String fileName = connToFile.remove(conn);

        if (fileName != null) {
            Set<WebSocket> room = documentRooms.get(fileName);
            if (room != null) {
                room.remove(conn);
                if (room.isEmpty()) {
                    //no one left - drop the room entirely so it doesn't linger
                    documentRooms.remove(fileName);
                    System.out.println("Room closed (empty): " + fileName);
                } else {
                    //still people in the room - update their user list
                    broadcastUserList(fileName);
                }
            }
        }
        System.out.println("✗ Disconnected: " + (username != null ? username : "unknown"));
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("WebSocket error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║  DocuBridge CollabServer started     ║");
        System.out.println("║  Listening on port " + PORT + "             ║");
        System.out.println("╚══════════════════════════════════════╝");
    }

    private void broadcastUserList(String fileName) {
        Set<WebSocket> room = documentRooms.get(fileName);
        if (room == null) return;

        //build the list of display names from whoever's currently in the room
        JSONArray users = new JSONArray();
        synchronized (room) {
            for (WebSocket client : room) users.put(usernames.getOrDefault(client, "unknown"));
        }

        JSONObject listMsg = new JSONObject();
        listMsg.put("type",     "userlist");
        listMsg.put("fileName", fileName);
        listMsg.put("users",    users);

        //send to everyone - clients use this to render the "who's here" panel
        synchronized (room) {
            for (WebSocket client : room) { if (client.isOpen()) client.send(listMsg.toString()); }
        }
    }

    //room code helpers

    //maps room codes (e.g. "BRIDGE-4821") to the host IP they resolve to
    private static final Map<String, String> roomCodes = Collections.synchronizedMap(new HashMap<>());

    public static String generateRoomCode() {
        // DatagramSocket trick: ask the OS which local IP it would use to reach an
        // external address. No data is actually sent - it just picks the right interface.
        try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
            socket.connect(java.net.InetAddress.getByName("8.8.8.8"), 80);
            String ip = socket.getLocalAddress().getHostAddress();
            System.out.println("✓ Host IP: " + ip);
            return ip;
        } catch (Exception e) {
            System.err.println("Could not determine local IP, falling back to localhost");
            return "localhost";
        }
    }

    /**
     * Resolves a room code or direct address to a host.
     * Supports:
     *   "BRIDGE-XXXX"          → registered room code → host IP
     *   "192.168.1.5"          → direct IP (passed through)
     *   "0.tcp.ngrok.io:12345" → ngrok address (passed through)
     */
    public static String resolveHostFromCode(String input) {
        if (input == null || input.isBlank()) return null;
        //check the room code registry first
        if (roomCodes.containsKey(input)) return roomCodes.get(input);
        //if it looks like an IP or host:port, pass it through directly
        if (input.contains(".") || input.contains(":")) return input;
        return null;
    }

    //entry point for running the server standalone

    //singleton - only one server instance should ever be running at a time
    private static CollabServer instance;

    public static void startServer() {
        if (instance != null) return;
        instance = new CollabServer();
        instance.start();
        System.out.println("CollabServer starting on port " + PORT + "...");
    }

    public static void stopServer() {
        if (instance == null) return;
        try {
            //1000ms timeout gives in-flight messages a chance to finish
            instance.stop(1000);
            System.out.println("✓ CollabServer stopped.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            instance = null;
            roomCodes.clear();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        CollabServer server = new CollabServer();
        server.start();
        System.out.println("Press Ctrl+C to stop.");
        //park the main thread so the server keeps running until killed
        Thread.currentThread().join();
    }
}
