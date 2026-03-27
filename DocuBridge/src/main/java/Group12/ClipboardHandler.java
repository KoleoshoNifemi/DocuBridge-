package Group12;

import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.application.Platform;
import netscape.javascript.JSObject;

import java.io.File;


// Handles all clipboard operations (copy, cut, paste) between Quill editor and system clipboard.
// Uses keyboard event listeners and JSObject for direct, reliable clipboard access.

public class ClipboardHandler {
    private final WebView webView; // view hosting Quill editor content
    private final WebEngine quill; // engine used to run JavaScript for clipboard actions


    public ClipboardHandler(WebView webView) {
        // Keep a handle on the WebView and its engine so we can talk to Quill
        this.webView = webView;
        this.quill = webView.getEngine();
        setupJavaScriptBridge();
    }

    // Enables Clipboard access for the webview
    private void enableClipboard() {
        // Allow the embedded browser to use system clipboard features
        webView.setContextMenuEnabled(true);
        quill.setJavaScriptEnabled(true);
        //Setting a user data directory unlocks full clipboard API access in the WebView's browser engine
        quill.setUserDataDirectory(new File(System.getProperty("java.io.tmpdir")));
    }

    // Sets up the JavaScript bridge for clipboard operations
    private void setupJavaScriptBridge() {
        enableClipboard();
        // Keyboard listeners are now handled in Editor.java initializeShortcuts()
    }

    // Handle Ctrl+C to copy selected text to system clipboard
    public void handleCopy() {
        try {
            //getSelection(true) focuses the editor before returning the selection, so we always get fresh data
            JSObject selection = (JSObject) quill.executeScript("quill.getSelection(true)");

            if (selection != null) {
                Number index = (Number) selection.getMember("index");
                Number length = (Number) selection.getMember("length");

                if (length != null && length.intValue() > 0) {
                    // Get the text and HTML content
                    String text = (String) quill.executeScript("quill.getText(" + index.intValue() + ", " + length.intValue() + ")");
                    String html = (String) quill.executeScript("quill.getSemanticHTML(" + index.intValue() + ", " + length.intValue() + ")");

                    //Put both plain text and HTML on the clipboard so paste targets can pick whichever they prefer
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(text);
                    if (html != null && !html.isEmpty()) {
                        content.putHtml(html);
                    }
                    clipboard.setContent(content);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Handle Ctrl+X to cut selected text to system clipboard
    public void handleCut() {
        try {
            JSObject selection = (JSObject) quill.executeScript("quill.getSelection(true)");

            if (selection != null) {
                Number index = (Number) selection.getMember("index");
                Number length = (Number) selection.getMember("length");

                if (length != null && length.intValue() > 0) {
                    // Get the text and HTML content
                    String text = (String) quill.executeScript("quill.getText(" + index.intValue() + ", " + length.intValue() + ")");
                    String html = (String) quill.executeScript("quill.getSemanticHTML(" + index.intValue() + ", " + length.intValue() + ")");

                    // Set to system clipboard
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(text);
                    if (html != null && !html.isEmpty()) {
                        content.putHtml(html);
                    }
                    clipboard.setContent(content);

                    //history.cutoff() bookends the delete so it shows up as a single discrete undo step
                    quill.executeScript(
                            "quill.history.cutoff();" +
                            "quill.deleteText(" + index.intValue() + ", " + length.intValue() + ", 'user');" +
                            "quill.history.cutoff();"
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Handle Ctrl+V to paste from system clipboard into Quill
    public void handlePaste() {
        //Clipboard reads and DOM mutations must happen on the JavaFX application thread
        Platform.runLater(() -> {
            try {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                // Prefer HTML if present, otherwise fall back to plain text
                String content = null;
                boolean isHtml = false;

                if (clipboard.hasHtml()) {
                    content = clipboard.getHtml();
                    isHtml = true;
                } else if (clipboard.hasString()) {
                    content = clipboard.getString();
                    isHtml = false;
                } else {
                    return;
                }

                if (content == null || content.isEmpty()) {
                    return;
                }

                //We can't safely pass large strings through executeScript string literals, so we
                //stash the content on the window object and read it back from within the script
                JSObject window = (JSObject) quill.executeScript("window");
                window.setMember("_pasteContent", content);
                window.setMember("_pasteIsHtml", isHtml);

                // Execute JavaScript that uses the content directly
                String script = "quill.history.cutoff();" +
                        "var selection = quill.getSelection(true);" +
                        "var index = selection ? selection.index : 0;" +
                        "if (window._pasteIsHtml) {" +
                        //dangerouslyPasteHTML parses and inserts the HTML with Quill formatting preserved
                        "    quill.clipboard.dangerouslyPasteHTML(index, window._pasteContent, 'user');" +
                        "} else {" +
                        "    quill.insertText(index, window._pasteContent, 'user');" +
                        "    quill.setSelection(index + window._pasteContent.length);" +
                        "}" +
                        "quill.history.cutoff();" +
                        //Clean up the temp properties so they don't linger on the global object
                        "delete window._pasteContent;" +
                        "delete window._pasteIsHtml;";

                quill.executeScript(script);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
