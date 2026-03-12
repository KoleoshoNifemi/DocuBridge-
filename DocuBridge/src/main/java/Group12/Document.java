package Group12;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;

public class Document {
    private String name;
    private WebView webView;
    private WebEngine quill;
    private ScrollPane scrollPane;
    private StackPane webViewContainer;
    private BorderPane mainLayout;
    private ToolBar toolBar;


    private void initializeWebView(){
        webView = new WebView();
        quill = webView.getEngine();
        String quillJsPath = getClass().getResource("/quill/editor.html").toExternalForm();
        quill.load(quillJsPath);

        webView.setPrefHeight(800);
        webView.setMaxWidth(816);  // 8.5 * 96 (standard screen DPI)
        webView.setMinHeight(600);
    }

    private void StyleContainer(){
        webViewContainer = new StackPane();
        webViewContainer.setStyle("-fx-background-color: rgb(204, 202, 202)" + "-fx-padding: 30px 0;");
        webViewContainer.setMaxWidth(Double.MAX_VALUE);
        webViewContainer.setStyle( "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);");
        webViewContainer.setAlignment(javafx.geometry.Pos.CENTER);
        webViewContainer.getChildren().add(webView);


        scrollPane = new ScrollPane();
        scrollPane.setContent(webViewContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    }

    private void initializeLayout(){
        mainLayout = new BorderPane();
        // mainLayout.setTop() -> toolbar would be param
        mainLayout.setCenter(scrollPane);
    }

    public Document(String name) {
        this.name = name;
        //initialize the toolbar here
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

}
