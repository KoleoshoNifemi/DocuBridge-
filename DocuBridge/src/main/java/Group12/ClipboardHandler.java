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
    private final WebView webView;
    private final WebEngine quill;


    public ClipboardHandler(WebView webView) {
        this.webView = webView;
        this.quill = webView.getEngine();
        setupJavaScriptBridge();
    }

    // Enables Clipboard access for the webview
    private void enableClipboard() {
        webView.setContextMenuEnabled(true);
        quill.setJavaScriptEnabled(true);
        quill.setUserDataDirectory(new File(System.getProperty("java.io.tmpdir")));
    }

    // Sets up the JavaScript bridge for clipboard operations
    private void setupJavaScriptBridge() {
        enableClipboard();
        // Keyboard listeners are now handled in Editor.java initializeShortcuts()
    }

    // Handle Ctrl+C - Copy selected text to system clipboard
    public void handleCopy() {
        try {
            // Get the selected text from Quill using direct executeScript
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
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Handle Ctrl+X - Cut selected text to system clipboard
    public void handleCut() {
        try {
            // Get the selected text from Quill using direct executeScript
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
                    
                    // Delete the text from editor
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

    // Handle Ctrl+V - Paste from system clipboard into Quill
    public void handlePaste() {
        Platform.runLater(() -> {
            try {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                
                // Try to get HTML first, fall back to plain text
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
                
                // Store the content and HTML flag in the window object
                JSObject window = (JSObject) quill.executeScript("window");
                window.setMember("_pasteContent", content);
                window.setMember("_pasteIsHtml", isHtml);
                
                // Execute JavaScript that uses the content directly
                String script = "quill.history.cutoff();" +
                        "var selection = quill.getSelection(true);" +
                        "var index = selection ? selection.index : 0;" +
                        "if (window._pasteIsHtml) {" +
                        "    quill.clipboard.dangerouslyPasteHTML(index, window._pasteContent, 'user');" +
                        "} else {" +
                        "    quill.insertText(index, window._pasteContent, 'user');" +
                        "    quill.setSelection(index + window._pasteContent.length);" +
                        "}" +
                        "quill.history.cutoff();" +
                        "delete window._pasteContent;" +
                        "delete window._pasteIsHtml;";
                
                quill.executeScript(script);
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}