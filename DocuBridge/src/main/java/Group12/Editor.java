package Group12;

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
    private double dpi;

    private Toolbar toolbar;   // toolbar

    private double readDPI(){
        Screen s = Screen.getPrimary();
        return (s.getDpi());
    }

    private void initializeWebView(){
        webView = new WebView();
        quill = webView.getEngine();
        String quillJsPath = getClass().getResource("/quill/editor.html").toExternalForm();
        quill.load(quillJsPath);

        webView.setPrefHeight(800);
        webView.setMaxWidth(8.5 * dpi);     // 8.5 inches width
        webView.setMinHeight(600);
    }

    private void StyleContainer(){

        webViewContainer = new StackPane();

        webViewContainer.setStyle(
                "-fx-background-color: #f3f3f3;" +
                        "-fx-padding: 20px 0 0 0;"
        );

        webViewContainer.setMaxWidth(Double.MAX_VALUE);
        webViewContainer.setAlignment(javafx.geometry.Pos.TOP_CENTER);

        webViewContainer.getChildren().add(webView);

        scrollPane = new ScrollPane();
        scrollPane.setContent(webViewContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    }

    private void initializeLayout(){

        mainLayout = new BorderPane();

        mainLayout.setTop(toolbar.getView()); // use toolbar created in constructor
        mainLayout.setCenter(scrollPane);
    }

    public Editor(String name) {

        this.name = name;
        dpi = readDPI();

        toolbar = new Toolbar();   // initialize toolbar

        initializeWebView();
        StyleContainer();
        initializeLayout();
    }

    //Getters & Setters
    public BorderPane getView() {
        return mainLayout;
    }

    public String getName() {
        return name;
    }

    public double getDPI(){
        return dpi;
    }
}