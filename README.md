# DocuBridge

A full-featured, collaborative desktop word processor built with JavaFX and Quill.js - featuring real-time multi-user editing over WebSocket, live document translation via Azure Translator, cloud-backed file storage on Azure SQL Server, and rich `.docx` export via Apache POI.

Created by Tharunkaarthik Gopinath, Abdulrehman Nasir, Shehryar Usman, and Koleosho Nifemi.

---

## Features

### Rich Text Editing
- Embedded Quill.js editor inside a JavaFX `WebView` - browser-quality editing in a native desktop app
- **Character formatting:** bold, italic, underline, strikethrough, subscript, superscript
- **Font family:** Arial, Courier New, Georgia, Times New Roman
- **Font size:** 8–92px (dropdown with common presets or manual entry)
- **Text color:** 15-color palette with live color bar preview
- **Highlight/background color:** 14-color palette with "No Highlight" option to clear
- **Paragraph alignment:** Left, Center, Right, Justify
- **Headers:** H1–H6 with "No Header" option
- **Lists:** bulleted and numbered, with Backspace-to-exit on empty list item
- **Images:** insert via file chooser (JPG, PNG, GIF, BMP, WebP), embedded as base64 data URIs, auto-scaled to max 500px wide, text-wrapping float
- **Hyperlinks:** insert/edit via Ctrl+K or Insert menu, auto-link detection on Space/Enter, opens in system browser
- **Undo/redo:** 1000-step history stack (user actions only)

### Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| Ctrl+B / I / U | Bold / Italic / Underline |
| Ctrl+K | Insert or remove hyperlink |
| Ctrl+Z | Undo |
| Ctrl+Y / Ctrl+Shift+Z | Redo |
| Ctrl+C / X / V | Copy / Cut / Paste |
| Ctrl+F | Find & Replace |
| Ctrl+Shift+= | Increase font size |
| Ctrl+Shift+- | Decrease font size |
| Ctrl+Backspace | Delete word before cursor |
| Ctrl+Delete | Delete word after cursor |
| Shift+Enter | Soft line break (within paragraph) |

### Find & Replace
- Ctrl+F opens a floating panel (always on top, non-blocking)
- Live match highlighting in yellow as you type (400ms debounce)
- Optional regex mode toggle
- Match counter (e.g. `3 / 15`) with Prev/Next navigation
- Replace single match or Replace All
- Background-threaded search to keep the UI responsive
- Escape to close, Enter to jump to next match

### Real-Time Collaboration
- Built-in WebSocket server (`CollabServer`) on port 8765, hosted by the session owner
- **Same network:** share your local IP as the room code
- **Cross-network:** set up an [ngrok](https://ngrok.com) TCP tunnel and share the ngrok address (in-app setup guide included)
- Delta-based sync using Quill's operational transform format - only diffs sent over the wire
- Full document sync sent to new joiners on connect
- 150ms outgoing delta polling, 50ms incoming apply loop
- **Live remote cursors:** each collaborator's cursor shown as a colored caret labeled with their username, 15-color deterministic palette
- Real-time user presence list in the title bar and Collab menu
- Automatic room redirect: joining a non-existent room routes to the active session

### Live Translation
- Powered by **Azure Translator** (Cognitive Services)
- Supported languages: English, French, Spanish, German, Greek, Portuguese
- Translates the entire document in real time as you type - debounced at 1 second of idle
- Formatting fully preserved across languages: bold, italic, color, size, headers, lists, alignment are untouched
- **Delta-aware:** translates only text runs; paragraph structure (newlines, attributes) is extracted before sending to Azure and stitched back in after
- **Source preservation:** the original-language content is always saved internally so switching languages always retranslates from the source, never from an already-translated version
- "No Translation" option restores the original text instantly
- Menu button displays `⇄ Language` when active
- Works best in a collaboration session (host or join) for continuous live updates
- Toolbar hint warns when not in a collab session

### Cloud Authentication & File Storage
- Azure SQL Server backend for user accounts and document storage
- Passwords hashed with **BCrypt** (jbcrypt) - plaintext never stored
- Login and sign-up validation: 3-char minimum username, 6-char minimum password
- Server retry on login/signup: if the database is still starting up, the app retries up to 15 times (2s apart) with a live countdown - no false "wrong password" errors
- Per-user file isolation: each user only sees their own documents
- Create, open, save, and delete files from the in-app file browser
- Auto-save every 5 seconds while a file is open

### Word Document Export & Import
- Export to `.docx` via **Apache POI**, preserving all rich formatting:
  - Bold, italic, underline, strikethrough
  - Font family, size, color, and highlight
  - Subscript and superscript
  - Paragraph alignment and headers
  - Bulleted and numbered lists
  - Embedded images (auto-scaled to max 500px)
- Import `.docx` files back as plain text
- Exported files saved to `~/Documents/DocuBridge/`

### Clipboard
- Custom `ClipboardHandler` bridges the system clipboard with Quill across the JavaFX–WebView boundary
- Copy/cut preserves rich HTML formatting
- Paste detects HTML vs plain text and uses the appropriate Quill API

---

## Architecture

```
Main ──► AuthenticationUI ──► DatabaseManager (Azure SQL)
  │
  └──► Editor ──► Toolbar              (callbacks via HashMap<String, Runnable/BiConsumer>)
            │──► WebView/WebEngine ──► editor.html ──► quill.js
            │──► ClipboardHandler      (JavaFX clipboard ↔ Quill JS)
            │──► WordSearch            (Find & Replace dialog)
            │──► TranslationManager ──► TranslationService (Azure Translator API)
            └──► CollabClient          (WebSocket client)
                      │
                 CollabServer          (WebSocket server, port 8765)
```

### Java ↔ JavaScript Bridge
Java calls into Quill directly:
```java
webEngine.executeScript("quill.format('bold', true)");
```
Outgoing user deltas are collected in a hidden `<textarea id="deltaComm">` polled every 150ms. Incoming collab deltas are written to `<textarea id="incomingComm">` and applied by a JS `setInterval` every 50ms. This polling-based IPC avoids the fragility of JSObject callbacks across the WebView boundary.

### Collaboration Protocol

| Message | Direction | Key Fields |
|---|---|---|
| `join` | Client → Server | `fileName`, `username` |
| `joined` | Server → Client | `fileName` |
| `delta` | Bidirectional | `fileName`, `delta` (Quill delta JSON) |
| `full` | Server → Client | `fileName`, `content` (full delta) |
| `userlist` | Server → Client | `users` (string array) |
| `cursor` | Bidirectional | `fileName`, `username`, `index`, `length` |

### Translation Pipeline
1. Translation poller fires after 1s of no changes
2. `syncParaFormatsToOriginal()` copies current paragraph attributes (header, align, list) into `_originalDelta`
3. `translateDeltaAsync()` extracts text runs from delta ops, splits each on `\n`, batches all segments in one Azure API call
4. Translated segments are stitched back into the delta with `\n` characters and original formatting attributes restored
5. `applyTranslatedDelta()` applies the result via `quill.setContents()`, restoring cursor position afterward

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
| Translation | Azure Translator (Cognitive Services) |
| HTTP client | OkHttp3 |
| JSON | org.json |

---

## Getting Started

### Prerequisites
- Java 25+
- Maven 3.8+
- A `config.properties` file in `DocuBridge/` (not committed - see below)

### Configuration
Create `DocuBridge/config.properties`:
```properties
db.url=jdbc:sqlserver://<host>:1433;database=<db>;user=<user>;password=<pass>;encrypt=true;...
azure.translator.key=<your-key>
azure.translator.region=<your-region>
azure.translator.endpoint=https://api.cognitive.microsofttranslator.com/
```

### Run
```bash
cd DocuBridge
mvn clean compile
mvn javafx:run
```

### Hosting a Collaboration Session
1. Open or create a file, then go to **Collab → Session settings** and select **Host**
2. The room code dialog shows your local IP for same-network collaborators
3. For cross-network: follow the in-app ngrok setup guide to get a tunnel address
4. Share the address with your collaborators

### Joining a Collaboration Session
1. On the file selector, select **Join a Session** and enter the room code or ngrok address
2. Or open a file first, then go to **Collab → Session settings** and select **Join**

### Using Live Translation
1. Host or join a collaboration session
2. Open the **Translation** menu in the toolbar and pick a target language
3. The document translates in real time as you type
4. Select **No Translation** to restore the original text

---

## Project Structure

```
DocuBridge/
├── config.properties               # Local config - NOT committed (add to .gitignore)
├── src/main/java/Group12/
│   ├── Main.java                   # App entry point, scene management, auto-save
│   ├── Editor.java                 # WebView controller, Quill bridge, shortcuts, translation poller
│   ├── Toolbar.java                # MenuBar, formatting toolbar, UI state refresh
│   ├── ClipboardHandler.java       # JavaFX ↔ Quill clipboard bridge
│   ├── AuthenticationUI.java       # Login & sign-up UI with server retry logic
│   ├── DatabaseManager.java        # Azure SQL CRUD: users, files, content
│   ├── CollabClient.java           # WebSocket client, delta/cursor send
│   ├── CollabServer.java           # WebSocket server, room management, broadcast
│   ├── TranslationManager.java     # Translation orchestration, delta-aware batching
│   ├── TranslationService.java     # Azure Translator HTTP client (single + batch)
│   ├── WordDocumentManager.java    # Apache POI .docx export and plain-text import
│   └── WordSearch.java             # Find & Replace dialog, regex, match navigation
└── src/main/resources/quill/
    ├── editor.html                 # Quill init, IPC layer, remote cursors, auto-link
    ├── quill.js                    # Bundled Quill.js v1.x
    ├── quill.snow.css              # Snow theme styles
    └── image-tools.js              # Image resize overlay
```
