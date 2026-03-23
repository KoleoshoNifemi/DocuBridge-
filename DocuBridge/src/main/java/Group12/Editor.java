package Group12;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.effect.DropShadow;
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
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import javafx.animation.PauseTransition;

public class Editor {
    private String name;
    private WebView webView;
    private WebEngine quill;
    private BorderPane mainLayout;
    private Toolbar toolBar;
    private double dpi;
    private VBox editorWrapper;
    private ClipboardHandler clipboardHandler;



    private double readDPI(){
        return Screen.getPrimary().getDpi();
    }

    // Getting our WebView running with quillJS
    private void initializeWebView() {
        // Load the HTML page that contains the Quill editor inside the WebView
        webView = new WebView();
        quill = webView.getEngine();
        String quillJsPath = getClass().getResource("/quill/editor.html").toExternalForm();
        quill.getLoadWorker().stateProperty().addListener((obs, old, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                // HTML loaded – now wait for Quill to be ready
                waitForQuillReady();
            }
        });
        quill.load(quillJsPath);

        // Keep the WebView sized like a sheet of paper and tall enough to type
        webView.setPrefHeight(800);
        webView.setMaxWidth(8.5 * dpi);     //8.5 inches width
        webView.setMinHeight(600);
    }

    private void waitForQuillReady() {
        PauseTransition pause = new PauseTransition(Duration.millis(100));
        pause.setOnFinished(event -> {
            // Ask the page if Quill is ready before wiring up shortcuts and clipboard
            Boolean quillReady = (Boolean) quill.executeScript("typeof quill !== 'undefined' && quill !== null");
            if (Boolean.TRUE.equals(quillReady)) {  //Quill is fully loaded internally
                clipboardHandler = new ClipboardHandler(webView);   // Initialize clipboard handler (handles all copy/cut/paste functionality)
                //clipboardHandler.enableCharacterByCharacterUndo();
                initializeShortcuts();

            } else {
                // Not ready yet – try again
                waitForQuillReady();
            }
        });
        pause.play();
    }





    private void StyleContainer(){
        // Wrap the editor so it stays centered and keeps the drop shadow look
        editorWrapper = new VBox();
        editorWrapper.setAlignment(Pos.TOP_CENTER);
        editorWrapper.setPadding(Insets.EMPTY); // remove gaps above/below
        editorWrapper.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(webView, Priority.ALWAYS);
        webView.setMaxHeight(Double.MAX_VALUE);
        editorWrapper.getChildren().add(webView);
        editorWrapper.setFillWidth(false);

        // Keep focus on the editor when clicked
        editorWrapper.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (!webView.isFocused()) {
                webView.requestFocus();
                quill.executeScript("if (window.quill) { quill.focus(); }");
            }
        });
    }

    // Defining where to place toolbar and editing area
    private void initializeLayout(){
        // Place toolbar at the top and the editor below it
        mainLayout = new BorderPane();
        mainLayout.setTop(toolBar.getView());
        mainLayout.setCenter(editorWrapper);
        // Light, Google-Docs-like canvas around the page
        mainLayout.setStyle("-fx-background-color: #f1f3f4;");
        mainLayout.setPadding(Insets.EMPTY); // no gaps around content
    }

    private void forceRepaint() {
        // Force a repaint by triggering a browser reflow without touching history
        // Save scroll position inside the WebView to prevent snapping
        final double savedScrollY = currentScrollY();

        PauseTransition delay = new PauseTransition(Duration.millis(50));
        delay.setOnFinished(e -> {
            // Use CSS opacity toggle to force a repaint
            quill.executeScript(
                    "var editor = document.querySelector('.ql-editor');" +
                            "if (editor) {" +
                            "    editor.style.opacity = '0.99';" +
                            "    setTimeout(function() {" +
                            "        editor.style.opacity = '1';" +
                            "    }, 1);" +
                            "}"
            );

            // JavaFX-side repaint: sub-pixel resize forces the window to redraw
            double w = webView.getWidth();
            webView.setPrefWidth(w + 0.01);
            Platform.runLater(() -> {
                webView.setPrefWidth(w);
                // Restore scroll position after layout is complete
                quill.executeScript("window.scrollTo(0, " + savedScrollY + ");");
            });
        });
        delay.play();
    }

    private double currentScrollY() {
        try {
            Object scrollVal = quill.executeScript("window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0");
            if (scrollVal instanceof Number) {
                return ((Number) scrollVal).doubleValue();
            }
        } catch (Exception ignored) {
            // best effort
        }
        return 0.0;
    }

    public Editor(String name, Runnable saveAs, Runnable save, Runnable newFile, Runnable openFile) {
        // Build all UI pieces as soon as the editor is created
        this.name = name;
        dpi = readDPI();
        toolBar = new Toolbar(giveFunctionsNoParams(saveAs, save, newFile, openFile), giveFunctionsWithParams(), giveReturnFunctions());
        initializeWebView();
        StyleContainer();
        initializeLayout();
    }


    //Shortcut functions
    private void undo(){
        Platform.runLater(() -> {
            // Cutoff ensures that whatever was being typed right now
            // is "sealed" before the undo command moves the timeline back.
            quill.executeScript("quill.history.cutoff(); quill.history.undo();");
        });
    }

    private void redo(){
        Platform.runLater(() -> {
            // Same idea as undo but moves forward in the history
            quill.executeScript("quill.history.cutoff(); quill.history.redo();");
        });
    }

    // Text formatting functions
    private void fontSizeShortcut(String operand, String source){
        Platform.runLater(() -> {
            // Adjust the font size up or down relative to the current selection
            quill.executeScript("quill.focus();" +
                    "var selection = quill.getSelection();" +
                    "var selectionIndex = selection ? selection.index : 0;" +
                    "var selectionLength = selection ? selection.length : 0;" +
                    "var currentSize = quill.getFormat().size;" +
                    "if (!currentSize || !currentSize.endsWith('px')) {" +
                    "    currentSize = '16px';" +   // Equivalent to 12pt font
                    "}" +
                    "var pxSize = parseInt(currentSize);" +
                    "var ptSize = Math.round(pxSize / 1.333);" +
                    "var newPtSize = ptSize " + operand + " 2;" +
                    "if (newPtSize < 8) newPtSize = 8;" +
                    "if (newPtSize > 92) newPtSize = 92;" +
                    "var newSize = Math.round(newPtSize * 1.333) + 'px';" +
                    "quill.format('size', newSize, '" + source + "');" +
                    "setTimeout(function() {" +
                    "    quill.update();" +
                    "    var endIndex = selectionIndex + selectionLength;" +
                    "    quill.setSelection(endIndex, 0);" +
                    "}, 10);"
            );
            forceRepaint();
        });
    }

    private void applyScript(String scriptType, String source){
        Platform.runLater(() -> {
            webView.requestFocus();
            quill.executeScript(
                    "quill.focus();" +
                            "var currentScript = quill.getFormat().script;" +
                            "var newValue = (currentScript === '" + scriptType + "') ? false : '" + scriptType + "';" +
                            "quill.format('script', newValue, '" + source + "');"
            );
            forceRepaint();
        });
    }

    private void format(String type, String source){
        Platform.runLater(() -> {
            quill.executeScript(
                    "(function(){" +
                            "  if (!window.quill) return;" +
                            "  var sel = quill.getSelection();" +
                            "  if (!sel) return;" +
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

        // Get current selection and link state
        JSObject selection = (JSObject) quill.executeScript("quill.getSelection(true)");

        if (selection == null) {
            // No selection, show dialog with empty input
            promptForLink(0, 0, source);
            return;
        }

        Number indexNum = (Number) selection.getMember("index");
        Number lengthNum = (Number) selection.getMember("length");

        int index = indexNum != null ? indexNum.intValue() : 0;
        int length = lengthNum != null ? lengthNum.intValue() : 0;

        if (length == 0) {
            // No text selected
            return;
        }

        // Check if selected text already has a link
        Object currentLink = quill.executeScript("quill.getFormat().link");

        // JavaScript undefined returns as string "undefined", check for that
        if (currentLink != null && !"undefined".equals(currentLink.toString()) && !currentLink.toString().isEmpty()) {
            // Remove existing link
            Platform.runLater(() -> {
                quill.executeScript(
                        "quill.setSelection(" + index + ", " + length + ");" +
                                "quill.format('link', false, '" + source + "');"
                );
                forceRepaint();
            });
        } else {
            // Prompt user for URL
            promptForLink(index, length, source);
        }
    }

    private void promptForLink(int index, int length, String source) {
        TextInputDialog dialog = new TextInputDialog("http://");
        dialog.setTitle("Add Link");
        dialog.setHeaderText("Enter the URL:");
        dialog.setContentText("URL:");
        dialog.setGraphic(null);  // Remove the question mark icon

        java.util.Optional<String> result = dialog.showAndWait();

        result.ifPresent(url -> {
            if (!url.trim().isEmpty()) {
                Platform.runLater(() -> {
                    // Ensure selection is still active and apply link
                    quill.executeScript(
                            "quill.setSelection(" + index + ", " + length + ");" +
                                    "quill.format('link', '" + url.replace("'", "\\'") + "', '" + source + "');"
                    );
                    forceRepaint();
                });
            }
        });
    }

    private void setFontSize(String size, String source){
        Platform.runLater(() -> {
            webView.requestFocus();
            quill.executeScript("quill.focus();" +
                    "quill.format('size','" + size + "','" + source + "');"
            );
            forceRepaint();
        });
    }

    private void setFontType(String type, String source){
        Platform.runLater(() -> {
            webView.requestFocus();
            quill.executeScript("quill.focus();" +
                    "quill.format('font','" + type + "','" + source + "');"
            );
            forceRepaint();
        });
    }

    private void setTextAlignment(String alignment, String source){
        Platform.runLater(() -> {
            webView.requestFocus();
            quill.executeScript("quill.focus();" +
                    "quill.format('align','" + alignment + "','" + source + "');"
            );
            forceRepaint();
        });
    }

    private void setTextColour(String colour, String source){
        Platform.runLater(() -> {
            webView.requestFocus();
            quill.executeScript("quill.focus();" +
                    "quill.format('color','" + colour + "','" + source + "');"
            );
            forceRepaint();
        });
    }

    private void setTextHighlight (String colour, String source){
        Platform.runLater(() -> {
            webView.requestFocus();
            quill.executeScript("quill.focus();" +
                    "quill.format('background','" + colour + "','" + source + "');"
            );
            forceRepaint();
        });
    }

    private void setHeader(String level, String source){
        Platform.runLater(() -> {
            webView.requestFocus();
            String headerValue = level.equals("none") ? "false" : level;
            quill.executeScript("quill.focus();" +
                    "quill.format('header'," + headerValue + ",'" + source + "');"
            );
            forceRepaint();
        });
    }

    private void insertImage(String imageType, String source) {
        Platform.runLater(() -> {
            webView.requestFocus();

            // Create file chooser for image selection
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Image");

            // Add image format filters
            FileChooser.ExtensionFilter allImages = new FileChooser.ExtensionFilter("All Images",
                    "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp", "*.webp");
            FileChooser.ExtensionFilter jpgFilter = new FileChooser.ExtensionFilter("JPG Images", "*.jpg", "*.jpeg");
            FileChooser.ExtensionFilter pngFilter = new FileChooser.ExtensionFilter("PNG Images", "*.png");
            FileChooser.ExtensionFilter gifFilter = new FileChooser.ExtensionFilter("GIF Images", "*.gif");
            FileChooser.ExtensionFilter allFiles = new FileChooser.ExtensionFilter("All Files", "*.*");

            fileChooser.getExtensionFilters().addAll(allImages, jpgFilter, pngFilter, gifFilter, allFiles);

            // Show file chooser
            File selectedFile = fileChooser.showOpenDialog(webView.getScene().getWindow());

            if (selectedFile != null) {
                try {
                    // Read image file and convert to Base64
                    byte[] imageBytes = Files.readAllBytes(selectedFile.toPath());
                    String base64Image = Base64.getEncoder().encodeToString(imageBytes);

                    // Determine MIME type based on file extension
                    String fileName = selectedFile.getName().toLowerCase();
                    String mimeType = "image/png"; // default
                    if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                        mimeType = "image/jpeg";
                    } else if (fileName.endsWith(".gif")) {
                        mimeType = "image/gif";
                    } else if (fileName.endsWith(".bmp")) {
                        mimeType = "image/bmp";
                    } else if (fileName.endsWith(".webp")) {
                        mimeType = "image/webp";
                    }

                    // Create data URI for the image
                    String dataUri = "data:" + mimeType + ";base64," + base64Image;

                    // Insert image using Quill's native insertEmbed
                    JSObject selection = (JSObject) quill.executeScript("quill.getSelection(true)");
                    int index = 0;
                    if (selection != null) {
                        Number indexNum = (Number) selection.getMember("index");
                        if (indexNum != null) {
                            index = indexNum.intValue();
                        }
                    }

                    // Insert image at cursor position using Quill's native image format
                    quill.executeScript(
                            "quill.focus(); " +
                                    "quill.insertEmbed(" + index + ", 'image', '" + dataUri.replace("'", "\\'") + "', '" + source + "'); " +
                                    "quill.insertText(" + (index + 1) + ", '\\n', '" + source + "'); " +
                                    "quill.setSelection(" + (index + 2) + ", 0);"
                    );

                    forceRepaint();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }



    private void insertList(String listType, String source){
        Platform.runLater(() -> {
            webView.requestFocus();
            quill.executeScript("quill.focus();");

            // Determine the list format (Quill v2 uses 'bullet' for unordered, 'ordered' for ordered)
            String listFormat = listType.equals("bullet") ? "bullet" : "ordered";

            // Check current list format and toggle if same type
            quill.executeScript(
                    "var selection = quill.getSelection(); " +
                            "if (!selection) { selection = {index: 0, length: 0}; } " +
                            "var currentFormat = quill.getFormat(selection.index, 1); " +
                            "var currentList = currentFormat.list; " +
                            "var newListValue = (currentList === '" + listFormat + "') ? false : '" + listFormat + "'; " +
                            "if (selection && selection.length > 0) { " +
                            "  quill.formatLine(selection.index, selection.length, 'list', newListValue); " +
                            "} else { " +
                            "  quill.formatLine(selection.index, 1, 'list', newListValue); " +
                            "} " +
                            "quill.focus();"
            );

            forceRepaint();
        });
    }



    private JSObject getFormats(){
        try {
            // Check if quill exists first before trying to call it
            Boolean quillExists = (Boolean) quill.executeScript("typeof quill !== 'undefined' && quill !== null");

            if (!quillExists) {
                return null;
            }

            // Now safely call getFormat()
            Object result = quill.executeScript("quill.getFormat()");

            if (result instanceof JSObject) {
                return (JSObject) result;
            }

            return null;
        } catch (Exception e) {
            // Silently catch exceptions - they occur during rapid state changes
            // when Quill's DOM is being updated
            return null;
        }
    }

    private void search(String text){

    }

    private void initializeShortcuts(){

        webView.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() || event.isMetaDown()) {  //ctrl (windows), cmd (mac)
                switch (event.getCode()) {
                    case Z:
                        event.consume();    // Prevents event from being passed to quill
                        if (event.isShiftDown()) {
                            redo();
                        } else {
                            undo();
                        }
                        break;
                    case Y:
                        event.consume();
                        redo();
                        break;
                    case B:
                        event.consume();
                        format("bold", "user");
                        break;
                    case I:
                        event.consume();
                        format("italic", "user");
                        break;
                    case U:
                        event.consume();
                        format("underline", "user");
                        break;
                    case K:
                        event.consume();
                        applyLink("user");
                        break;
                    case C:
                        event.consume();
                        clipboardHandler.handleCopy();
                        break;
                    case X:
                        event.consume();
                        clipboardHandler.handleCut();
                        break;
                    case V:
                        event.consume();
                        clipboardHandler.handlePaste();
                        break;
                    case F:
                        event.consume();
                        // Pass a lambda that calls the JS function and returns the text
                        new WordSearch(this::quillExecute, () -> {
                            Object text = quill.executeScript("window.getCachedDocumentText()");
                            return text != null ? text.toString() : "";
                        }, this.webView).showSearchPopup();
                        break;
                    case EQUALS:
                        event.consume();
                        if(event.isShiftDown()){    //ctrl-shift-plus
                            fontSizeShortcut("+", "user");
                        }
                        break;
                    case MINUS:
                        event.consume();
                        if(event.isShiftDown()){    //ctrl-shift-minus
                            fontSizeShortcut("-", "user");
                        }
                        break;
                }
            } else if (event.getCode() == KeyCode.BACK_SPACE) {
                // Restore backspace behavior: exit list when at start of an item
                handleBackspaceInList();
            }
        });
    }

    private void handleBackspaceInList() {
        Platform.runLater(() -> {
            quill.executeScript(
                    "(function(){" +
                            "  var sel = quill.getSelection();" +
                            "  if (!sel || sel.length > 0) return;" +
                            "  var idx = sel.index;" +
                            "  var fmt = quill.getFormat(idx, 1);" +
                            "  var isList = fmt.list && (fmt.list === 'bullet' || fmt.list === 'ordered' || fmt.list === 'unordered');" +
                            "  if (!isList) return;" +
                            "  if (idx === 0) { quill.formatLine(idx, 1, 'list', false); return; }" +
                            "  var charBefore = quill.getText(idx - 1, 1);" +
                            "  if (charBefore === '\\n') { quill.formatLine(idx, 1, 'list', false); }" +
                            "})();"
            );
            forceRepaint();
        });
    }

    // Getters & Setters
    public BorderPane getView() {
        return mainLayout;
    }

    public String getName() {
        return name;
    }

    public double getDPI(){
        return dpi;
    }

    public ClipboardHandler getClipboardHandler() {
        return clipboardHandler;
    }

    // These three functions provide the Toolbar object limited access to the webEngine (quill)
    private HashMap<String, BiConsumer<String, String>> giveFunctionsWithParams(){
        HashMap<String, BiConsumer<String, String>> temp = new HashMap<>();
        temp.put("format", this::format);
        temp.put("setFontSize", this::setFontSize);
        temp.put("setFontType", this::setFontType);
        temp.put("applyScript", this::applyScript);
        temp.put("setTextAlignment", this::setTextAlignment);
        temp.put("setFontColor", this::setTextColour);
        temp.put("setHighlightColor", this::setTextHighlight);
        temp.put("setHeader", this::setHeader);
        temp.put("insertList", this::insertList);
        temp.put("insertImage", this::insertImage);
        return temp;
    }

    private HashMap<String, Runnable> giveFunctionsNoParams(Runnable saveAs, Runnable save, Runnable newFile, Runnable openFile){
        HashMap<String, Runnable> temp = new HashMap<>();
        temp.put("undo", this::undo);
        temp.put("redo", this::redo);
        temp.put("forceRepaint", this::forceRepaint);
        if (saveAs != null) temp.put("saveAs", saveAs);
        if (save != null) temp.put("save", save);
        if (newFile != null) temp.put("newFile", newFile);
        if (openFile != null) temp.put("openFile", openFile);
        return temp;
    }

    private HashMap<String, Callable<Object>> giveReturnFunctions(){
        HashMap<String, Callable<Object>> temp = new HashMap<>();
        temp.put("getFormats", this::getFormats);
        return temp;
    }

    // General purpose script executor to be given to other classes where needed
    private void quillExecute(String script, Boolean repaint){
        Platform.runLater(() -> {quill.executeScript(script);
            if(repaint){forceRepaint();}
        });
    }

    public void loadContent(String content) {
        if (content == null || content.isEmpty()) {
            content = "";
        }

        final String finalContent = content;

        // Wait for Quill to be ready
        PauseTransition pause = new PauseTransition(Duration.millis(500));
        pause.setOnFinished(event -> {
            // Check if Quill is ready
            Boolean quillReady = (Boolean) quill.executeScript("typeof quill !== 'undefined' && quill !== null");
            if (Boolean.TRUE.equals(quillReady)) {
                Platform.runLater(() -> {
                    if (finalContent.trim().startsWith("{")) {
                        // Delta JSON — restore full formatting
                        String escaped = finalContent
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\r", "\\r")
                                .replace("\n", "\\n");
                        quill.executeScript("quill.setContents(JSON.parse(\"" + escaped + "\"))");
                    } else {
                        // Plain text fallback for old documents
                        String escaped = finalContent
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n");
                        quill.executeScript("quill.setText(\"" + escaped + "\")");
                    }
                });
            } else {
                // Quill still not ready, try again
                loadContent(finalContent);
            }
        });
        pause.play();
    }

    public String getContent() {
        Object result = quill.executeScript("JSON.stringify(quill.getContents())");
        return result != null ? result.toString() : "";
    }
}