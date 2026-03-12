package Group12;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {

        Editor editor = new Editor("Untitled Document");

        Scene scene = new Scene(editor.getView(), 1200, 800);

        stage.setTitle("DocuBridge");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}