package Group12;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Priority;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;
import javafx.util.Duration;
import javafx.stage.FileChooser;
import netscape.javascript.JSObject;
import org.json.JSONArray;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

public class Editor {
    private String name;
    private WebView webView;
    private WebEngine quill;
    private BorderPane mainLayout;
    private Toolbar toolBar;
    private double dpi;
    private VBox editorWrapper;
    private ClipboardHandler clipboardHandler;
    private TranslationManager translationManager;
    private String originalText = "";

    private WordSearch wordSearch;
    private CollabClient collabClient;
    private String serverHost;
    private String username;
    private boolean bridgeAttached = false;
    private int lastSentCursorIndex  = -2;
    private int lastSentCursorLength = 0;
    private int     lastTranslatedVersion = -1;   // _translationVersion at last completed translation
    private boolean translating          = false; // true while an async translation is in-flight

    private double readDPI() {
        return Screen.getPrimary().getDpi();
    }

    private void initializeWebView() {
        webView = new WebView();
        quill = webView.getEngine();
        String quillJsPath = getClass().getResource("/quill/editor.html").toExternalForm();
        quill.getLoadWorker().stateProperty().addListener((obs, old, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                waitForQuillReady();
            }
        });
        quill.load(quillJsPath);
        webView.setPrefHeight(800);
        webView.setMaxWidth(8.5 * dpi);
        webView.setMinHeight(600);
    }

    private void waitForQuillReady() {
        PauseTransition pause = new PauseTransition(Duration.millis(100));
        pause.setOnFinished(event -> {
            Boolean quillReady = (Boolean) quill.executeScript("typeof quill !== 'undefined' && quill !== null");
            if (Boolean.TRUE.equals(quillReady)) {
                System.out.println("✓ Quill is ready");
                clipboardHandler = new ClipboardHandler(webView);
                initializeShortcuts();

                JSObject win = (JSObject) quill.executeScript("window");
                win.setMember("collabBridgeReady", true);

                bridgeAttached = false;
                scheduleAttachBridge();

            } else {
                waitForQuillReady();
            }
        });
        pause.play();
    }

    private void scheduleAttachBridge() {
        if (bridgeAttached) return;

        PauseTransition check = new PauseTransition(Duration.millis(200));
        check.setOnFinished(e -> {
            if (bridgeAttached) return;

            if (collabClient != null && collabClient.isOpen()) {
                System.out.println("✓ collabClient is open, attaching bridge");
                attachJsBridge();
            } else {
                scheduleAttachBridge();
            }
        });
        check.play();
    }

    private void StyleContainer() {
        editorWrapper = new VBox();
        editorWrapper.setAlignment(Pos.TOP_CENTER);
        editorWrapper.setPadding(Insets.EMPTY);
        editorWrapper.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(webView, Priority.ALWAYS);
        webView.setMaxHeight(Double.MAX_VALUE);
        editorWrapper.getChildren().add(webView);
        editorWrapper.setFillWidth(false);

        editorWrapper.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (!webView.isFocused()) {
                webView.requestFocus();
                quill.executeScript("if (window.quill) { quill.focus(); }");
            }
        });
    }

    private void initializeLayout() {
        mainLayout = new BorderPane();
        mainLayout.setTop(toolBar.getView());
        mainLayout.setCenter(editorWrapper);
        mainLayout.setStyle("-fx-background-color: #f1f3f4;");
        mainLayout.setPadding(Insets.EMPTY);
    }

    private void forceRepaint() {
        final double savedScrollY = currentScrollY();
        PauseTransition delay = new PauseTransition(Duration.millis(50));
        delay.setOnFinished(e -> {
            quill.executeScript(
                    "var editor = document.querySelector('.ql-editor');" +
                            "if (editor) { editor.style.opacity = '0.99'; setTimeout(function() { editor.style.opacity = '1'; }, 1); }"
            );
            double w = webView.getWidth();
            webView.setPrefWidth(w + 0.01);
            Platform.runLater(() -> {
                webView.setPrefWidth(w);
                quill.executeScript("window.scrollTo(0, " + savedScrollY + ");");
            });
        });
        delay.play();
    }

    private double currentScrollY() {
        try {
            Object scrollVal = quill.executeScript("window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0");
            if (scrollVal instanceof Number) return ((Number) scrollVal).doubleValue();
        } catch (Exception ignored) {}
        return 0.0;
    }

    public Editor(String name, Runnable saveAs, Runnable save, Runnable newFile, Runnable openFile,
                  Runnable showCollabDialog, Runnable stopHosting, Runnable disconnectCollab,
                  TranslationManager translationManager) {
        this.name = name;
        this.translationManager = translationManager;
        System.out.println("DEBUG: Editor created with translationManager=" + (translationManager != null ? "yes" : "null"));
        dpi = readDPI();
        toolBar = new Toolbar(
                giveFunctionsNoParams(saveAs, save, newFile, openFile, showCollabDialog, stopHosting, disconnectCollab),
                giveFunctionsWithParams(),
                giveReturnFunctions()
        );
        initializeWebView();
        StyleContainer();
        initializeLayout();
    }

    public void enableCollab(String serverHost, String username, Consumer<String[]> onUsersChanged) {
        this.serverHost = serverHost;
        this.username   = username;
        bridgeAttached  = false;

        collabClient = CollabClient.create(serverHost, username, name, quill);
        if (onUsersChanged != null) collabClient.setOnUsersChanged(onUsersChanged);

        new Thread(() -> {
            try {
                System.out.println("DEBUG: Connecting to collab server...");
                boolean connected = collabClient.connectBlocking();
                if (connected) {
                    System.out.println("✓ Collab connected to " + serverHost);
                } else {
                    System.err.println("✗ Could not connect to CollabServer at " + serverHost);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "collab-connect").start();
    }

    private void attachJsBridge() {
        bridgeAttached = true;
        lastTranslatedVersion = -1;
        translating           = false;
        System.out.println("✓ JS collab bridge attached");

        Object reg = quill.executeScript(
                "(function(){" +
                        "  if (typeof quill === 'undefined') return 'quill_not_found';" +
                        "  quill.on('text-change', function(delta, oldDelta, source) {" +
                        "    document.title = 'TC:' + source + ':' + (window._tcFromJava = (window._tcFromJava||0)+1);" +
                        "    if (source !== 'user') return;" +
                        "    var el = document.getElementById('deltaComm');" +
                        "    if (!el) return;" +
                        "    var arr = JSON.parse(el.value || '[]');" +
                        "    arr.push(JSON.stringify(delta));" +
                        "    el.value = JSON.stringify(arr);" +
                        "  });" +
                        "  return 'listener_added';" +
                        "})()"
        );
        System.out.println("DEBUG attachJsBridge listener registration: " + reg);
        lastTranslatedVersion = -1;
        translating           = false;
        startDeltaPoller();
        startCursorPoller();
        startTranslationPoller();
    }

    private void startTranslationPoller() {
        // Use _translationVersion (incremented by every text-change in editor.html, including
        // formatting-only changes like header/align) instead of plain text so that formatting
        // edits also trigger a retranslation.
        final int[]  stableVersion = {-1};
        final long[] lastChangeMs  = {0};

        PauseTransition poll = new PauseTransition(Duration.millis(500));
        poll.setOnFinished(e -> {
            if (!bridgeAttached || collabClient == null) return;
            if (translationManager == null || !translationManager.isTranslationEnabled()) {
                if (bridgeAttached && collabClient != null) poll.play();
                return;
            }
            try {
                Object verObj = quill.executeScript("window._translationVersion || 0");
                int currentVersion = verObj instanceof Number ? ((Number) verObj).intValue() : 0;
                long now = System.currentTimeMillis();

                if (currentVersion != stableVersion[0]) {
                    // Something changed — reset debounce timer
                    stableVersion[0] = currentVersion;
                    lastChangeMs[0]  = now;
                } else if (currentVersion != lastTranslatedVersion
                        && (now - lastChangeMs[0]) >= 1000
                        && !translating) {
                    // Stable for ≥1 s, not yet translated at this version, not busy — go
                    translating = true;
                    final int snapVersion = currentVersion;
                    syncParaFormatsToOriginal(); // copy header/align/list from display → _originalDelta
                    String deltaJson = (String) quill.executeScript(
                        "(function(){ return window._originalDelta || JSON.stringify(quill.getContents()); })()");
                    if (deltaJson != null) {
                        translationManager.translateDeltaAsync(deltaJson, translatedDelta -> {
                            Object nowVerObj = quill.executeScript("window._translationVersion || 0");
                            int nowVersion = nowVerObj instanceof Number ? ((Number) nowVerObj).intValue() : 0;
                            if (snapVersion == nowVersion) {
                                applyTranslatedDelta(translatedDelta);
                                lastTranslatedVersion = snapVersion;
                            }
                            // if version changed during translation, poller will retry automatically
                            translating = false;
                        });
                    } else {
                        translating = false;
                    }
                }
            } catch (Exception ex) { translating = false; }
            if (bridgeAttached && collabClient != null) poll.play();
        });
        poll.play();
    }

    /**
     * Before translating, copies paragraph-level formatting (header, align, list, indent)
     * from the currently displayed Quill content into _originalDelta by paragraph index.
     *
     * Why: when translation is ON the display has e.g. French text while _originalDelta has
     * English.  A header/alignment delta from the user is relative to the French character
     * positions, so composing it onto the shorter English text leaves the attribute at the
     * wrong (or non-existent) position.  Reading the final state of each paragraph's \n op
     * from the display and copying it to the matching \n in _originalDelta by index is
     * position-independent and always correct.
     */
    private void syncParaFormatsToOriginal() {
        quill.executeScript(
            "(function(){" +
            "  if (!window._originalDelta) return;" +
            "  var dOps = quill.getContents().ops;" +
            "  var oData = JSON.parse(window._originalDelta);" +
            "  var oOps  = oData.ops || [];" +
            // Collect paragraph-level attrs from every standalone \\n in the display
            "  var pa = [];" +
            "  for (var i = 0; i < dOps.length; i++) {" +
            "    var op = dOps[i];" +
            "    if (typeof op.insert === 'string' && op.insert === '\\n')" +
            "      pa.push(op.attributes ? JSON.parse(JSON.stringify(op.attributes)) : null);" +
            "  }" +
            // Apply those attrs to the matching \\n ops in _originalDelta by index
            "  var pi = 0;" +
            "  for (var i = 0; i < oOps.length; i++) {" +
            "    if (typeof oOps[i].insert === 'string' && oOps[i].insert === '\\n') {" +
            "      if (pi < pa.length) {" +
            "        if (pa[pi]) oOps[i].attributes = pa[pi];" +
            "        else        delete oOps[i].attributes;" +
            "      }" +
            "      pi++;" +
            "    }" +
            "  }" +
            "  window._originalDelta = JSON.stringify({ops: oOps});" +
            "})()"
        );
    }

    /** Applies a translated delta JSON to the Quill editor without triggering re-translation.
     *  Also saves window._originalDelta (the pre-translation content) the first time it is called,
     *  so future language switches always retranslate from the original base. */
    private void applyTranslatedDelta(String translatedDelta) {
        JSObject win = (JSObject) quill.executeScript("window");
        win.setMember("_pendingTranslatedDelta", translatedDelta);
        quill.executeScript(
            "(function(){" +
            "  if (!window._originalDelta) window._originalDelta = JSON.stringify(quill.getContents());" +
            "  window._applyingTranslation = true;" +
            "  try { quill.setContents(JSON.parse(window._pendingTranslatedDelta), 'api'); } catch(e){}" +
            "  window._applyingTranslation = false;" +
            "  window._pendingTranslatedDelta = null;" +
            // Refocus so formatting buttons work; don't setSelection with a stale index
            // from the old translated content (different character lengths cause mid-word placement).
            "  quill.focus();" +
            "})()"
        );
    }

    private void startCursorPoller() {
        lastSentCursorIndex  = -2;
        lastSentCursorLength = 0;
        PauseTransition poll = new PauseTransition(Duration.millis(100));
        poll.setOnFinished(e -> {
            if (!bridgeAttached || collabClient == null) return;
            try {
                Object raw = quill.executeScript(
                        "(function(){" +
                                "  var s = quill.getSelection();" +
                                "  return s ? s.index + ',' + s.length : null;" +
                                "})()"
                );
                if (raw instanceof String) {
                    String[] parts = ((String) raw).split(",");
                    int idx = Integer.parseInt(parts[0].trim());
                    int len = Integer.parseInt(parts[1].trim());
                    if (idx != lastSentCursorIndex || len != lastSentCursorLength) {
                        collabClient.sendCursor(idx, len);
                        lastSentCursorIndex  = idx;
                        lastSentCursorLength = len;
                    }
                }
            } catch (Exception ex) {
                // ignore
            }
            if (bridgeAttached && collabClient != null) poll.play();
        });
        poll.play();
    }

    private int pollCounter = 0;

    private void startDeltaPoller() {
        pollCounter = 0;
        System.out.println("DEBUG: startDeltaPoller started");
        PauseTransition poll = new PauseTransition(Duration.millis(150));
        poll.setOnFinished(e -> {
            if (!bridgeAttached || collabClient == null) {
                System.out.println("DEBUG: Delta poller skipped - bridgeAttached=" + bridgeAttached + ", collabClient=" + (collabClient != null));
                return;
            }
            try {
                if (pollCounter++ % 50 == 0) {
                    System.out.println("DEBUG: Delta poller heartbeat #" + pollCounter);
                }
                String arrJson = (String) quill.executeScript(
                        "(function(){ var el=document.getElementById('deltaComm'); if(!el) return '[]'; var v=el.value||'[]'; el.value='[]'; return v; })()"
                );

                if (arrJson != null && !arrJson.equals("[]")) {
                    JSONArray arr = new JSONArray(arrJson);
                    for (int i = 0; i < arr.length(); i++) {
                        collabClient.sendDelta(arr.getString(i));
                    }
                    // While translation is ON, compose each user-typed delta into _originalDelta
                    // so the poller retranslates up-to-date content instead of overwriting new typing.
                    if (translationManager != null && translationManager.isTranslationEnabled()) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSObject win = (JSObject) quill.executeScript("window");
                            win.setMember("_userDeltaForCompose", arr.getString(i));
                            quill.executeScript(
                                "(function(){" +
                                "  if (!window._originalDelta || !window._userDeltaForCompose) return;" +
                                "  try {" +
                                "    var Delta = Quill.import('delta');" +
                                "    var orig = new Delta(JSON.parse(window._originalDelta));" +
                                "    var d = new Delta(JSON.parse(window._userDeltaForCompose));" +
                                "    window._originalDelta = JSON.stringify(orig.compose(d));" +
                                "  } catch(e) {}" +
                                "  window._userDeltaForCompose = null;" +
                                "})()"
                            );
                        }
                        // No need to reset lastTranslatedVersion — _translationVersion already
                        // incremented when the user typed, so the poller will detect the change.
                    }
                }
            } catch (Exception ex) {
                System.err.println("Delta poller error: " + ex.getMessage());
                ex.printStackTrace();
            }
            if (bridgeAttached && collabClient != null) poll.play();
        });
        poll.play();
    }

    public void disconnectCollab() {
        System.out.println("DEBUG: disconnectCollab called");
        bridgeAttached        = false;
        lastSentCursorIndex   = -2;
        lastSentCursorLength  = 0;
        lastTranslatedVersion = -1;
        translating           = false;
        if (translationManager != null) translationManager.disableTranslation();
        try {
            quill.executeScript("if (typeof window.clearAllRemoteCursors === 'function') window.clearAllRemoteCursors();");
        } catch (Exception ignored) {}
        if (collabClient != null && collabClient.isOpen()) {
            try { collabClient.closeBlocking(); } catch (InterruptedException ignored) {}
        }
        collabClient = null;
    }

    public boolean isCollabConnected() {
        return collabClient != null && collabClient.isOpen();
    }

    public CollabClient getCollabClient() {
        return collabClient;
    }

    public Toolbar getToolbar() {
        return toolBar;
    }

    private void undo() {
        Platform.runLater(() -> quill.executeScript("quill.history.cutoff(); quill.history.undo();"));
    }

    private void redo() {
        Platform.runLater(() -> quill.executeScript("quill.history.cutoff(); quill.history.redo();"));
    }

    private void fontSizeShortcut(String operand, String source) {
        Platform.runLater(() -> {
            quill.executeScript("quill.focus();" +
                    "var selection = quill.getSelection();" +
                    "var selectionIndex = selection ? selection.index : 0;" +
                    "var selectionLength = selection ? selection.length : 0;" +
                    "var currentSize = quill.getFormat().size;" +
                    "if (!currentSize || !currentSize.endsWith('px')) { currentSize = '16px'; }" +
                    "var pxSize = parseInt(currentSize);" +
                    "var ptSize = Math.round(pxSize / 1.333);" +
                    "var newPtSize = ptSize " + operand + " 2;" +
                    "if (newPtSize < 8) newPtSize = 8;" +
                    "if (newPtSize > 92) newPtSize = 92;" +
                    "var newSize = Math.round(newPtSize * 1.333) + 'px';" +
                    "quill.format('size', newSize, '" + source + "');" +
                    "setTimeout(function() { quill.update(); var endIndex = selectionIndex + selectionLength; quill.setSelection(endIndex, 0); }, 10);"
            );
            forceRepaint();
        });
    }

    private void applyScript(String scriptType, String source) {
        Platform.runLater(() -> {
            webView.requestFocus();
            quill.executeScript("quill.focus();" +
                    "var currentScript = quill.getFormat().script;" +
                    "var newValue = (currentScript === '" + scriptType + "') ? false : '" + scriptType + "';" +
                    "quill.format('script', newValue, '" + source + "');"
            );
            forceRepaint();
        });
    }

    private void format(String type, String source) {
        Platform.runLater(() -> {
            quill.executeScript("(function(){" +
                    "  if (!window.quill) return;" +
                    "  var sel = quill.getSelection(); if (!sel) return;" +
                    "  quill.focus();" +
                    "  var fmt = quill.getFormat();" +
                    "  var current = fmt['" + type + "'];" +
                    "  quill.format('" + type + "', !current, '" + source + "');" +
                    "})();"
            );
            forceRepaint();
        });
    }

    private void applyLink(String source) {
        webView.requestFocus();
        JSObject selection = (JSObject) quill.executeScript("quill.getSelection(true)");
        if (selection == null) { promptForLink(0, 0, source); return; }
        Number indexNum  = (Number) selection.getMember("index");
        Number lengthNum = (Number) selection.getMember("length");
        int index  = indexNum  != null ? indexNum.intValue()  : 0;
        int length = lengthNum != null ? lengthNum.intValue() : 0;
        if (length == 0) return;
        Object currentLink = quill.executeScript("quill.getFormat().link");
        if (currentLink != null && !"undefined".equals(currentLink.toString()) && !currentLink.toString().isEmpty()) {
            Platform.runLater(() -> {
                quill.executeScript("quill.setSelection(" + index + ", " + length + "); quill.format('link', false, '" + source + "');");
                forceRepaint();
            });
        } else {
            promptForLink(index, length, source);
        }
    }

    private void promptForLink(int index, int length, String source) {
        TextInputDialog dialog = new TextInputDialog("http://");
        dialog.setTitle("Add Link");
        dialog.setHeaderText("Enter the URL:");
        dialog.setContentText("URL:");
        dialog.setGraphic(null);
        dialog.showAndWait().ifPresent(url -> {
            if (!url.trim().isEmpty()) {
                Platform.runLater(() -> {
                    quill.executeScript("quill.setSelection(" + index + ", " + length + "); quill.format('link', '" + url.replace("'", "\\'") + "', '" + source + "');");
                    forceRepaint();
                });
            }
        });
    }

    private void setFontSize(String size, String source) {
        Platform.runLater(() -> { webView.requestFocus(); quill.executeScript("quill.focus(); quill.format('size','" + size + "','" + source + "');"); forceRepaint(); });
    }

    private void setFontType(String type, String source) {
        Platform.runLater(() -> { webView.requestFocus(); quill.executeScript("quill.focus(); quill.format('font','" + type + "','" + source + "');"); forceRepaint(); });
    }

    private void setTextAlignment(String alignment, String source) {
        Platform.runLater(() -> { webView.requestFocus(); quill.executeScript("quill.focus(); quill.format('align','" + alignment + "','" + source + "');"); forceRepaint(); });
    }

    private void setTextColour(String colour, String source) {
        Platform.runLater(() -> { webView.requestFocus(); quill.executeScript("quill.focus(); quill.format('color','" + colour + "','" + source + "');"); forceRepaint(); });
    }

    private void setTextHighlight(String colour, String source) {
        Platform.runLater(() -> { webView.requestFocus(); quill.executeScript("quill.focus(); quill.format('background','" + colour + "','" + source + "');"); forceRepaint(); });
    }

    private void setHeader(String level, String source) {
        Platform.runLater(() -> {
            webView.requestFocus();
            String headerValue = level.equals("none") ? "false" : level;
            quill.executeScript("quill.focus(); quill.format('header'," + headerValue + ",'" + source + "');");
            forceRepaint();
        });
    }

    private void insertImage(String imageType, String source) {
        Platform.runLater(() -> {
            webView.requestFocus();
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Image");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("All Images", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp", "*.webp"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            File selectedFile = fileChooser.showOpenDialog(webView.getScene().getWindow());
            if (selectedFile != null) {
                try {
                    byte[] imageBytes = Files.readAllBytes(selectedFile.toPath());
                    String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                    String fileName = selectedFile.getName().toLowerCase();
                    String mimeType = "image/png";
                    if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) mimeType = "image/jpeg";
                    else if (fileName.endsWith(".gif")) mimeType = "image/gif";
                    else if (fileName.endsWith(".bmp")) mimeType = "image/bmp";
                    else if (fileName.endsWith(".webp")) mimeType = "image/webp";
                    String dataUri = "data:" + mimeType + ";base64," + base64Image;
                    JSObject selection = (JSObject) quill.executeScript("quill.getSelection(true)");
                    int index = 0;
                    if (selection != null) { Number n = (Number) selection.getMember("index"); if (n != null) index = n.intValue(); }
                    quill.executeScript("quill.focus(); quill.insertEmbed(" + index + ", 'image', '" + dataUri.replace("'", "\\'") + "', '" + source + "'); quill.insertText(" + (index + 1) + ", '\\n', '" + source + "'); quill.setSelection(" + (index + 2) + ", 0);");
                    forceRepaint();
                } catch (IOException e) { e.printStackTrace(); }
            }
        });
    }

    private void insertList(String listType, String source) {
        Platform.runLater(() -> {
            webView.requestFocus();
            quill.executeScript("quill.focus();");
            String listFormat = listType.equals("bullet") ? "bullet" : "ordered";
            quill.executeScript(
                    "var selection = quill.getSelection(); if (!selection) { selection = {index: 0, length: 0}; } " +
                            "var currentFormat = quill.getFormat(selection.index, 1); var currentList = currentFormat.list; " +
                            "var newListValue = (currentList === '" + listFormat + "') ? false : '" + listFormat + "'; " +
                            "if (selection && selection.length > 0) { quill.formatLine(selection.index, selection.length, 'list', newListValue); } " +
                            "else { quill.formatLine(selection.index, 1, 'list', newListValue); } quill.focus();"
            );
            forceRepaint();
        });
    }

    private JSObject getFormats() {
        try {
            Boolean quillExists = (Boolean) quill.executeScript("typeof quill !== 'undefined' && quill !== null");
            if (!quillExists) return null;
            Object result = quill.executeScript("quill.getFormat()");
            if (result instanceof JSObject) return (JSObject) result;
        } catch (Exception e) {}
        return null;
    }

    private void search(String text) {}

    private void toggleTranslation(String langCode, String action) {
        if (translationManager == null) return;
        if ("disable".equals(action)) {
            translationManager.disableTranslation();
            lastTranslatedVersion = -1;
            // Restore the English original if we have it stored
            try {
                quill.executeScript(
                    "(function(){" +
                    "  if (!window._originalDelta) return;" +
                    "  window._applyingTranslation = true;" +
                    "  try { quill.setContents(JSON.parse(window._originalDelta), 'api'); } catch(e){}" +
                    "  window._applyingTranslation = false;" +
                    "  window._originalDelta = null;" +
                    "  quill.focus();" +
                    "})()"
                );
            } catch (Exception ignored) {}
        } else if ("enable".equals(action)) {
            if (!isCollabConnected()) {
                System.out.println("Translation is only available during collaboration.");
                return;
            }
            translationManager.enableTranslation(langCode);
        }
    }

    private void retranslate(String langCode, String source) {
        if (translationManager == null || !isCollabConnected() || translating) return;

        // Always translate from the English original — never from an already-translated doc
        String deltaJson = (String) quill.executeScript(
            "(function(){ return window._originalDelta || JSON.stringify(quill.getContents()); })()");
        if (deltaJson == null) return;

        translating = true;
        syncParaFormatsToOriginal(); // preserve header/align/list across language switch

        translationManager.translateDeltaAsync(deltaJson, translatedDelta -> {
            applyTranslatedDelta(translatedDelta);
            Object verObj = quill.executeScript("window._translationVersion || 0");
            lastTranslatedVersion = verObj instanceof Number ? ((Number) verObj).intValue() : 0;
            translating = false;
        });
    }

    private String escapeForJavaScript(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void initializeShortcuts() {
        webView.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() || event.isMetaDown()) {
                switch (event.getCode()) {
                    case Z -> { event.consume(); if (event.isShiftDown()) redo(); else undo(); }
                    case Y -> { event.consume(); redo(); }
                    case B -> { event.consume(); format("bold", "user"); }
                    case I -> { event.consume(); format("italic", "user"); }
                    case U -> { event.consume(); format("underline", "user"); }
                    case K -> { event.consume(); applyLink("user"); }
                    case C -> { event.consume(); clipboardHandler.handleCopy(); }
                    case X -> { event.consume(); clipboardHandler.handleCut(); }
                    case V -> { event.consume(); clipboardHandler.handlePaste(); }
                    case F -> { event.consume(); showSearch(); }
                    case EQUALS -> { event.consume(); if (event.isShiftDown()) fontSizeShortcut("+", "user"); }
                    case MINUS  -> { event.consume(); if (event.isShiftDown()) fontSizeShortcut("-", "user"); }
                }
            } else if (event.getCode() == KeyCode.BACK_SPACE) {
                handleBackspaceInList();
            }
        });
    }

    private void handleBackspaceInList() {
        Platform.runLater(() -> {
            quill.executeScript("(function(){ var sel = quill.getSelection(); if (!sel || sel.length > 0) return; var idx = sel.index; var fmt = quill.getFormat(idx, 1); var isList = fmt.list && (fmt.list === 'bullet' || fmt.list === 'ordered' || fmt.list === 'unordered'); if (!isList) return; if (idx === 0) { quill.formatLine(idx, 1, 'list', false); return; } var charBefore = quill.getText(idx - 1, 1); if (charBefore === '\\n') { quill.formatLine(idx, 1, 'list', false); } })();");
            forceRepaint();
        });
    }

    public BorderPane getView()                        { return mainLayout; }
    public String getName()                            { return name; }
    public double getDPI()                             { return dpi; }
    public ClipboardHandler getClipboardHandler()      { return clipboardHandler; }

    private HashMap<String, BiConsumer<String, String>> giveFunctionsWithParams() {
        HashMap<String, BiConsumer<String, String>> temp = new HashMap<>();
        temp.put("format",           this::format);
        temp.put("setFontSize",      this::setFontSize);
        temp.put("setFontType",      this::setFontType);
        temp.put("applyScript",      this::applyScript);
        temp.put("setTextAlignment", this::setTextAlignment);
        temp.put("setFontColor",     this::setTextColour);
        temp.put("setHighlightColor",this::setTextHighlight);
        temp.put("setHeader",        this::setHeader);
        temp.put("insertList",       this::insertList);
        temp.put("insertImage",      this::insertImage);
        temp.put("toggleTranslation", this::toggleTranslation);
        temp.put("retranslate",      this::retranslate);
        return temp;
    }

    private HashMap<String, Runnable> giveFunctionsNoParams(Runnable saveAs, Runnable save,
                                                            Runnable newFile, Runnable openFile,
                                                            Runnable showCollabDialog, Runnable stopHosting, Runnable disconnectCollab) {
        HashMap<String, Runnable> temp = new HashMap<>();
        temp.put("undo",            this::undo);
        temp.put("redo",            this::redo);
        temp.put("forceRepaint",    this::forceRepaint);
        if (saveAs   != null) temp.put("saveAs",   saveAs);
        if (save     != null) temp.put("save",     save);
        if (newFile  != null) temp.put("newFile",  newFile);
        if (openFile != null) temp.put("openFile", openFile);
        temp.put("showSearch",  this::showSearch);
        if (showCollabDialog != null) temp.put("showCollabDialog", showCollabDialog);
        if (stopHosting      != null) temp.put("stopHosting",      stopHosting);
        if (disconnectCollab != null) temp.put("disconnectCollab", disconnectCollab);
        return temp;
    }

    private HashMap<String, Callable<Object>> giveReturnFunctions() {
        HashMap<String, Callable<Object>> temp = new HashMap<>();
        temp.put("getFormats", this::getFormats);
        return temp;
    }

    private void showSearch() {
        if (wordSearch == null) {
            wordSearch = new WordSearch(
                    this::quillExecute,
                    () -> { Object t = quill.executeScript("window.getCachedDocumentText()"); return t != null ? t.toString() : ""; },
                    webView
            );
        }
        wordSearch.showSearchPopup();
    }

    private void quillExecute(String script, Boolean repaint) {
        Platform.runLater(() -> { quill.executeScript(script); if (repaint) forceRepaint(); });
    }

    public void loadContent(String content) {
        if (content == null || content.isEmpty()) content = "";
        final String finalContent = content;
        PauseTransition pause = new PauseTransition(Duration.millis(500));
        pause.setOnFinished(event -> {
            Boolean quillReady = (Boolean) quill.executeScript("typeof quill !== 'undefined' && quill !== null");
            if (Boolean.TRUE.equals(quillReady)) {
                Platform.runLater(() -> {
                    if (finalContent.trim().startsWith("{")) {
                        String escaped = finalContent.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
                        quill.executeScript("quill.setContents(JSON.parse(\"" + escaped + "\"))");
                    } else {
                        String escaped = finalContent.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
                        quill.executeScript("quill.setText(\"" + escaped + "\")");
                    }
                });
            } else {
                loadContent(finalContent);
            }
        });
        pause.play();
    }

    public String getContent() {
        // If translation is active, always save/send the original-language content,
        // not the translated view — so reopening the file shows the author's language.
        Object result = quill.executeScript(
            "(function(){ return window._originalDelta || JSON.stringify(quill.getContents()); })()");
        return result != null ? result.toString() : "";
    }
}