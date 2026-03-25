# DocuBridge

A full-featured, collaborative desktop word processor built with JavaFX and Quill.js — featuring real-time multi-user editing over WebSocket, cloud-backed file storage on Azure SQL Server, and rich `.docx` export via Apache POI.

Made by Tharunkaarthik Gopinath, Abdulrehman Nasir, Shehyrar Usman, and Koleosho Nifemi.

---

## Features

### Rich Text Editing
- Embedded Quill.js editor inside a JavaFX `WebView`, delivering a browser-quality editing experience inside a native desktop app
- Full formatting support: **bold**, *italic*, underline, strikethrough, sub/superscript
- Font family selector (Arial, Courier New, Georgia, Times New Roman) and font size picker (8–92px)
- Text and background color pickers
- Paragraph alignment (left, center, right, justify)
- Headers (H1–H3), ordered and unordered lists with nested indentation
- Inline image insertion with drag-to-resize handles
- Hyperlink insertion and editing
- Undo/redo with a 1000-step history stack

### Real-Time Collaboration
- Built-in WebSocket server (`CollabServer`) hosted by the session owner on port 8765
- Clients connect via `CollabClient` using a room code (local IP) or an ngrok tunnel for cross-network sessions
- Delta-based synchronization using Quill's operational transform protocol — only diffs are sent over the wire, not full document state
- Live user presence list broadcast to all connected peers
- Automatic room redirect: joining clients are routed to the active file if only one room is running
- 80ms delta polling loop for near-real-time responsiveness

### Cloud Authentication & File Storage
- Azure SQL Server backend storing user accounts and document contents
- Secure password storage using **BCrypt** hashing (jbcrypt)
- User registration and login with validation (3-char min username, 6-char min password)
- Per-user file management: create, open, save, delete documents stored in the cloud
- Auto-save every 5 seconds while the editor is open

### Word Document Export
- Export any document to `.docx` format using **Apache POI**, preserving rich formatting (bold, italic, underline, color, font, size, alignment)
- Parses Quill delta JSON and maps formatting attributes to POI `XWPFRun` / `XWPFParagraph` properties
- Import `.docx` files back into the editor as plain text
- Files saved to `~/Documents/DocuBridge/` locally

### Find & Replace
- Ctrl+F opens a floating Find dialog with live match highlighting (yellow) directly in the editor
- Regex pattern support
- Next/Previous navigation with a match counter (e.g., `3 / 15`)
- Background-threaded search to keep the UI responsive
- Escape to dismiss; Enter to jump to next match

### Clipboard Bridge
- Custom `ClipboardHandler` bridges the system clipboard with Quill's internal clipboard via `JSObject`
- Ctrl+C/X/V work correctly across the JavaFX–WebView boundary
- Preserves rich text (HTML) on copy, pastes HTML or plain text as appropriate

---

## Architecture

```
Main ──► AuthenticationUI ──► DatabaseManager (Azure SQL)
  │
  └──► Editor ──► Toolbar         (receives callbacks via HashMap<String, Runnable>)
            │──► WebView/WebEngine ──► editor.html ──► quill.js
            │──► ClipboardHandler  (JavaFX clipboard ↔ Quill JS via JSObject)
            │──► WordSearch        (Find & Replace dialog)
            └──► CollabClient      (WebSocket client)
                      │
                 CollabServer      (WebSocket server, port 8765)
```

### Java ↔ JavaScript Bridge
All formatting commands follow a direct execution pattern:
```java
webEngine.executeScript("quill.format('bold', true)");
```
Collaboration deltas are passed back to Java via a hidden `<textarea id="deltaComm">` polled every 80ms — a lightweight, reliable IPC mechanism that avoids the complexity of JavaFX `JSObject` callbacks.

### Collaboration Protocol (JSON over WebSocket)
| Message Type | Direction | Payload |
|---|---|---|
| `join` | Client → Server | `{ fileName, username }` |
| `delta` | Bidirectional | `{ fileName, delta: QuillDelta }` |
| `full` | Server → Client | `{ fileName, content: QuillDelta }` |
| `userlist` | Server → Client | `{ users: string[] }` |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Desktop UI | JavaFX 22.0.2 |
| Build | Maven (Java 25) |
| Rich text editor | Quill.js v1.x (bundled) |
| Web integration | JavaFX WebView / WebEngine |
| Real-time sync | Java-WebSocket 1.5.4 |
| Cloud database | Azure SQL Server (mssql-jdbc 12.4.2) |
| Password security | jBCrypt 0.4 |
| Document export | Apache POI 5.2.3 |
| JSON handling | org.json |

---

## Getting Started

### Prerequisites
- Java 25+
- Maven 3.8+

### Run
```bash
cd DocuBridge
mvn clean compile
mvn javafx:run
```

### Hosting a Collaboration Session
1. Open or create a file and click **Collaborate → Host Session**
2. Share the generated room code (your local IP) with collaborators on the same network
3. For cross-network sessions, set up [ngrok](https://ngrok.com) and share the tunnel address

### Joining a Collaboration Session
1. On the file selector screen, click **Join Session**
2. Enter the room code or ngrok address provided by the host

---

## Project Structure

```
DocuBridge/
├── src/main/java/Group12/
│   ├── Main.java               # App entry point, window & collab orchestration
│   ├── Editor.java             # WebView controller, Quill bridge, delta sync
│   ├── Toolbar.java            # MenuBar & formatting toolbar
│   ├── ClipboardHandler.java   # JavaFX ↔ Quill clipboard bridge
│   ├── AuthenticationUI.java   # Login & registration UI
│   ├── DatabaseManager.java    # Azure SQL CRUD operations
│   ├── CollabClient.java       # WebSocket collaboration client
│   ├── CollabServer.java       # WebSocket collaboration server
│   ├── WordDocumentManager.java# Apache POI .docx export/import
│   └── WordSearch.java         # Find & Replace dialog
└── src/main/resources/quill/
    ├── editor.html             # Quill bootstrap & IPC layer
    ├── quill.js                # Bundled Quill.js library
    ├── quill.snow.css          # Snow theme stylesheet
    └── image-tools.js          # Image resize overlay
```
