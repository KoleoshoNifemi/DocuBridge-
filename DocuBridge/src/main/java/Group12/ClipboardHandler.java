package Group12;

import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import netscape.javascript.JSObject;

import java.io.File;


// Handles all clipboard operations (copy, cut, paste) between Quill editor and system clipboard.
// Uses JavaScript bridge for reliable clipboard access and prevents duplicate paste events.

public class ClipboardHandler {
    private final WebView webView;
    private final WebEngine quill;
    private long lastPasteTime = 0; // Guard against duplicate paste events


    // Class to be given to JavaScript
    public class ClipboardBridge {
        public void pasteText(String text) {
            long now = System.currentTimeMillis();
            if (now - lastPasteTime < 500) return; // ignore duplicates
            lastPasteTime = now;
            Platform.runLater(() -> insertIntoQuill(text, false));
        }

        public void pasteHTML(String html) {
            long now = System.currentTimeMillis();
            if (now - lastPasteTime < 500) return;
            lastPasteTime = now;
            Platform.runLater(() -> insertIntoQuill(html, true));
        }

        public void setClipboardText(String text, String html, String callback) {
            Platform.runLater(() -> {
                Clipboard fxClipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(text);
                if (html != null && !html.isEmpty()) {
                    content.putHtml(html);
                }
                fxClipboard.setContent(content);

                // Execute callback if needed (for cut operation)
                if (callback != null && !callback.isEmpty()) {
                    quill.executeScript(callback + "();");
                }
            });
        }
    }

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

    //Sets up the JavaScript bridge and clipboard event listeners
    private void setupJavaScriptBridge() {
        enableClipboard();

        quill.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {       // Check for completion of page loading
                Platform.runLater(() -> {                   //Ensures code runs on JavaFX thread
                    JSObject window = (JSObject) quill.executeScript("window");  // Get the js window as a java object

                    // Add a new property to the JS window object, setting its value to a new instance of our ClipboardBridge class
                    window.setMember("javaClipboard", new ClipboardBridge());

                    // Adding clipboard event listeners on document
                    // Capturing Phase of DOM: window → document → body → div → target
                    // By using the capturing phase we can guarantee our listeners fire before any others
                    quill.executeScript(
                            "if (!window._clipboardListenersAttached) {" +
                                    "    window._clipboardListenersAttached = true;" +
                                    "    document.addEventListener('paste', function(e) {" +        // Adding listener for pasting
                                    "        if (!e.target.closest('.ql-editor')) return;" +        // Check if the event target is inside quill editor
                                    "        e.preventDefault(); e.stopPropagation();" +            // Ensure our custom handler is the only one running
                                    "        var text = e.clipboardData.getData('text/plain');" +   // Get plain text from clipboard
                                    "        var html = e.clipboardData.getData('text/html');" +    // Get formatted text from clipboard
                                    "        if (html) javaClipboard.pasteHTML(html); else if (text) javaClipboard.pasteText(text);" +
                                    "    }, true);" +                                               // True param indicates use in capturing phase
                                    "    document.addEventListener('copy', function(e) {" +         // Add listener for copying
                                    "        if (!e.target.closest('.ql-editor')) return;" +
                                    "        e.preventDefault(); e.stopPropagation();" +
                                    "        var range = quill.getSelection(true);" +               // Get location of selected text
                                    "        if (range && range.length > 0) {" +                    // Check if selection is valid
                                    "            var text = quill.getText(range.index, range.length);" +    // Get plain text within selected range
                                    "            var html = quill.getSemanticHTML(range.index, range.length);" +    // Get formatted text within selected range
                                    "            javaClipboard.setClipboardText(text, html, null);" +   // Call given Java class and method
                                    "        }" +
                                    "    }, true);" +
                                    "    document.addEventListener('cut', function(e) {" +          // Adding listener for cutting
                                    "        if (!e.target.closest('.ql-editor')) return;" +
                                    "        e.preventDefault(); e.stopPropagation();" +
                                    "        var range = quill.getSelection(true);" +
                                    "        if (range && range.length > 0) {" +
                                    "            var text = quill.getText(range.index, range.length);" +
                                    "            var html = quill.getSemanticHTML(range.index, range.length);" +
                                    "            var callbackName = 'cutCallback_' + Date.now();" + // Create unique callback name using sys time
                                    "            window[callbackName] = function() {" +             // Add new function to js window
                                    "                quill.deleteText(range.index, range.length, 'user');" +    // User indicates changes were local
                                    "                delete window[callbackName];" +                // Delete function after execution
                                    "            };" +
                                    "            javaClipboard.setClipboardText(text, html, callbackName);" +
                                    "        }" +
                                    "    }, true);" +
                                    "}"
                    );
                });
            }
        });
    }

    // Insert content into the Quill editor at the current cursor position
    private void insertIntoQuill(String content, boolean isHtml) {
        // Escape backslashes, quotes, and newlines to prevent JS syntax errors
        content = content.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");


        if (isHtml) {
            quill.executeScript(
                    "var range = quill.getSelection(true);" +
                            "var index = range ? range.index : 0;" +    // Check if range has a value
                            "quill.clipboard.dangerouslyPasteHTML(index, '" + content + "', 'user');"
            );
        } else {
            quill.executeScript(
                    "var range = quill.getSelection(true);" +
                            "var index = range ? range.index : 0;" +
                            "quill.insertText(index, '" + content + "', 'user');" +
                            "quill.setSelection(index + " + content.length() + ");" // Move caret to location after paste
            );
        }
        quill.executeScript("quill.focus();");  // Enable general use of quill again
    }
}
