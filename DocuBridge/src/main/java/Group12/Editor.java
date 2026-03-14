package Group12;

import javafx.scene.input.KeyCode;
import javafx.stage.Screen;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;

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
        quill.load(quillJsPath);
        initializeShortcuts();

        // Initialize clipboard handler (handles all copy/cut/paste functionality)
        clipboardHandler = new ClipboardHandler(webView);

        webView.setPrefHeight(800);
        webView.setMaxWidth(8.5 * dpi);     //8.5 inches width
        webView.setMinHeight(600);
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

    public Editor(String name) {
        this.name = name;
        dpi = readDPI();
        toolBar = new Toolbar();
        initializeWebView();
        StyleContainer();
        initializeLayout();
    }

    //Shortcut functions
    private void undo(){
        quill.executeScript("quill.history.undo();");
    }

    private void redo(){
        quill.executeScript("quill.history.redo();");
    }

    private void initializeShortcuts(){
        webView.setOnKeyPressed(event -> {
            if (event.isControlDown() || event.isMetaDown()) {
                if (event.getCode() == javafx.scene.input.KeyCode.Z) {
                    if (event.isShiftDown()) {
                        redo();
                    } else {
                        undo();
                    }
                    event.consume();    //Only want to consume undo/redo
                } else if (event.getCode() == javafx.scene.input.KeyCode.Y) {
                    redo();
                    event.consume();    //only want to consume redo
                }
            }
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
}
