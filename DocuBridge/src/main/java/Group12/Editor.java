package Group12;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Screen;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;
import javafx.util.Duration;
import netscape.javascript.JSObject;

import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

public class Editor {
    private String name;
    private WebView webView;
    private WebEngine quill;
    private ScrollPane scrollPane;
    private StackPane webViewContainer;
    private BorderPane mainLayout;
    private Toolbar toolBar;
    private double dpi;
    private ClipboardHandler clipboardHandler;

    private double readDPI(){
        return Screen.getPrimary().getDpi();
    }

    // Getting our WebView running with quillJS
    private void initializeWebView() {
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

        webView.setPrefHeight(800);
        webView.setMaxWidth(8.5 * dpi);     //8.5 inches width
        webView.setMinHeight(600);
    }

    private void waitForQuillReady() {
        PauseTransition pause = new PauseTransition(Duration.millis(100));
        pause.setOnFinished(event -> {
            // Execute JavaScript to test if Quill is defined and not null
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
        // Configuring the stackPane which holds webView
        webViewContainer = new StackPane();
        webViewContainer.setStyle("-fx-background-color: rgb(204, 202, 202)" + "-fx-padding: 30px 0;");
        webViewContainer.setMaxWidth(Double.MAX_VALUE);
        webViewContainer.setStyle( "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);");
        webViewContainer.setAlignment(javafx.geometry.Pos.CENTER);  //center our editing area
        webViewContainer.getChildren().add(webView);
        webViewContainer.setFocusTraversable(true); //makes container able to receive events

        // Configuring the ScrollPane() which contains the stackPane()
        scrollPane = new ScrollPane();
        scrollPane.setContent(webViewContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);     //Never want horizontal scrolling
    }

    // Defining where to place toolbar and editing area
    private void initializeLayout(){
        mainLayout = new BorderPane();
        mainLayout.setTop(toolBar.getView());
        mainLayout.setCenter(scrollPane);
    }

    private void forceRepaint() {
        // Force a repaint by triggering a browser reflow without touching history
        PauseTransition delay = new PauseTransition(Duration.millis(50));
        delay.setOnFinished(e -> {
            // Use CSS opacity toggle to force a repaint without modifying undo/redo history
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
            Platform.runLater(() -> webView.setPrefWidth(w));
        });
        delay.play();
    }

    public Editor(String name) {
        this.name = name;
        dpi = readDPI();
        toolBar = new Toolbar(giveClipboardOperations(), giveFunctionsWithParams());
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
            quill.executeScript("quill.history.cutoff(); quill.history.redo();");
        });
    }

    private void fontSizeShortcut(String operand, String source){
        Platform.runLater(() -> {
            quill.executeScript("quill.focus();" +
                            "var currentSize = quill.getFormat().size;" +
                            "if (!currentSize || !currentSize.endsWith('px')) {" +
                            "    currentSize = '16px';" +   // Equivalent to 12pt font
                            "}" +
                            "var pxSize = parseInt(currentSize);" +
                            "var ptSize = Math.round(pxSize / 1.333);" +
                            "var newSize = (ptSize " + operand + " 2);" +
                            "newSize = Math.round (newSize * 1.333) + 'px';" +
                            "quill.format('size', newSize, '" + source + "');"
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
                    "quill.focus();" +
                    "quill.format('" + type + "', !quill.getFormat()." + type + ", '" + source + "');"
            );
            forceRepaint();
        });
    }

    private void applyLink(String source) {
        System.out.println("applyLink() called with source: " + source);
        webView.requestFocus();
        
        // Get current selection and link state
        JSObject selection = (JSObject) quill.executeScript("quill.getSelection(true)");
        System.out.println("Selection object: " + selection);
        
        if (selection == null) {
            System.out.println("No selection, returning");
            // No selection, show dialog with empty input
            promptForLink(0, 0, source);
            return;
        }
        
        Number indexNum = (Number) selection.getMember("index");
        Number lengthNum = (Number) selection.getMember("length");
        
        int index = indexNum != null ? indexNum.intValue() : 0;
        int length = lengthNum != null ? lengthNum.intValue() : 0;
        
        System.out.println("Selection: index=" + index + ", length=" + length);
        
        if (length == 0) {
            System.out.println("No text selected, returning");
            // No text selected
            return;
        }
        
        // Check if selected text already has a link
        Object currentLink = quill.executeScript("quill.getFormat().link");
        System.out.println("Current link: " + currentLink);
        
        // JavaScript undefined returns as string "undefined", check for that
        if (currentLink != null && !"undefined".equals(currentLink.toString()) && !currentLink.toString().isEmpty()) {
            System.out.println("Removing existing link");
            // Remove existing link
            Platform.runLater(() -> {
                quill.executeScript(
                        "quill.setSelection(" + index + ", " + length + ");" +
                        "quill.format('link', false, '" + source + "');"
                );
                forceRepaint();
            });
        } else {
            System.out.println("No link found, prompting for URL");
            // Prompt user for URL
            promptForLink(index, length, source);
        }
    }
    
    private void promptForLink(int index, int length, String source) {
        System.out.println("promptForLink() called with index=" + index + ", length=" + length);
        TextInputDialog dialog = new TextInputDialog("http://");
        dialog.setTitle("Add Link");
        dialog.setHeaderText("Enter the URL:");
        dialog.setContentText("URL:");
        dialog.setGraphic(null);  // Remove the question mark icon
        
        System.out.println("Showing dialog...");
        java.util.Optional<String> result = dialog.showAndWait();
        System.out.println("Dialog result: " + (result.isPresent() ? result.get() : "cancelled"));
        
        result.ifPresent(url -> {
            System.out.println("URL entered: " + url);
            if (!url.trim().isEmpty()) {
                System.out.println("Applying link to selection");
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

    private void initializeShortcuts(){
        webView.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() || event.isMetaDown()) {  //ctrl (windows), cmd (mac)
                System.out.println("Ctrl key detected with: " + event.getCode());
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
                        System.out.println("Ctrl+K pressed!");
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
            }
        });
    }

    private void toolBarFontSize(String size, String source){
        Platform.runLater(() -> {
            webView.requestFocus();
            quill.executeScript("quill.focus();" +
                    "quill.format('size','" + size + "','" + source + "');"
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

    // These two functions provide the Toolbar object limited access to the webEngine (quill)
    private HashMap<String, BiConsumer<String, String>> giveFunctionsWithParams(){
        HashMap<String, BiConsumer<String, String>> temp = new HashMap<>();
        temp.put("format", this::format);
        temp.put("setFontSize", this::toolBarFontSize);
        temp.put("applyScript", this::applyScript);
        return temp;
    }

    private HashMap<String, Runnable> giveClipboardOperations(){
        HashMap<String, Runnable> temp = new HashMap<>();
        temp.put("undo", this::undo);
        temp.put("redo", this::redo);
        return temp;
    }

}