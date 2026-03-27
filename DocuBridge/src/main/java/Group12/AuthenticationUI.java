package Group12;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;

public class AuthenticationUI {
    private DatabaseManager db;
    //- 1 means no user is logged in yet
    private int currentUserId = -1;
    private String currentUsername = "";
    //callback fired after a successful login or signup → login
    private Runnable onAuthSuccess;

    public AuthenticationUI(DatabaseManager db, Runnable onAuthSuccess) {
        this.db = db;
        this.onAuthSuccess = onAuthSuccess;
    }

    public void showAuthScreen(Stage stage) {
        VBox root = new VBox(15);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #f5f5f5;");

        Label titleLabel = new Label("DocuBridge");
        titleLabel.setStyle("-fx-font-size: 28; -fx-font-weight: bold;");

        //login and signup live in separate tabs so we reuse the same stage
        TabPane tabPane = new TabPane();
        tabPane.getTabs().addAll(
                createLoginTab(),
                createSignupTab()
        );
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        root.getChildren().addAll(titleLabel, tabPane);

        Scene scene = new Scene(root, 550, 500);
        stage.setTitle("DocuBridge - Authentication");
        stage.setScene(scene);
        stage.show();
    }

    private Tab createLoginTab() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setPrefHeight(40);
        usernameField.setStyle("-fx-font-size: 12;");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setPrefHeight(40);
        passwordField.setStyle("-fx-font-size: 12;");

        Button loginBtn = new Button("Login");
        loginBtn.setPrefHeight(40);
        loginBtn.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        loginBtn.setPrefWidth(Double.MAX_VALUE);

        //single label reused for both errors and success messages
        Label statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: red;");
        statusLabel.setWrapText(true);

        loginBtn.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();

            if (username.isEmpty() || password.isEmpty()) {
                statusLabel.setText("Username and password required");
                statusLabel.setStyle("-fx-text-fill: red;");
                return;
            }

            //disable the button while the DB call is in flight to prevent double-clicks
            loginBtn.setDisable(true);
            statusLabel.setText("Connecting to server, please wait...");
            statusLabel.setStyle("-fx-text-fill: #e67e22;");

            //DB calls block, so they go off the FX thread
            new Thread(() -> {
                //poll the server before trying to authenticate; bail out if it never comes up
                if (!waitForServer(statusLabel, 15, 2000)) {
                    Platform.runLater(() -> {
                        loginBtn.setDisable(false);
                        statusLabel.setText("Could not connect to server. Please try again.");
                        statusLabel.setStyle("-fx-text-fill: red;");
                    });
                    return;
                }
                String hash = db.getPasswordHash(username);
                //all UI updates must come back to the FX thread
                Platform.runLater(() -> {
                    loginBtn.setDisable(false);
                    //hash is null when the username doesn't exist; BCrypt handles the comparison
                    if (hash != null && BCrypt.checkpw(password, hash)) {
                        currentUserId   = db.getUserId(username);
                        currentUsername = username;
                        statusLabel.setText("Login successful!");
                        statusLabel.setStyle("-fx-text-fill: green;");
                        onAuthSuccess.run();
                    } else {
                        statusLabel.setText("Invalid username or password");
                        statusLabel.setStyle("-fx-text-fill: red;");
                    }
                });
            }, "db-login").start();
        });

        content.getChildren().addAll(
                new Label("Username:"),
                usernameField,
                new Label("Password:"),
                passwordField,
                loginBtn,
                statusLabel
        );

        Tab tab = new Tab("Login", content);
        tab.setClosable(false);
        return tab;
    }

    private Tab createSignupTab() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setPrefHeight(40);
        usernameField.setStyle("-fx-font-size: 12;");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setPrefHeight(40);
        passwordField.setStyle("-fx-font-size: 12;");

        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirm Password");
        confirmField.setPrefHeight(40);
        confirmField.setStyle("-fx-font-size: 12;");

        Button signupBtn = new Button("Create Account");
        signupBtn.setPrefHeight(40);
        signupBtn.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        signupBtn.setPrefWidth(Double.MAX_VALUE);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);

        signupBtn.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            String confirm = confirmField.getText();

            //validate everything client-side before hitting the DB
            if (username.isEmpty() || password.isEmpty()) {
                statusLabel.setText("All fields required");
                statusLabel.setStyle("-fx-text-fill: red;");
                return;
            }

            if (!password.equals(confirm)) {
                statusLabel.setText("Passwords don't match");
                statusLabel.setStyle("-fx-text-fill: red;");
                return;
            }

            if (password.length() < 6) {
                statusLabel.setText("Password must be at least 6 characters");
                statusLabel.setStyle("-fx-text-fill: red;");
                return;
            }

            if (username.length() < 3) {
                statusLabel.setText("Username must be at least 3 characters");
                statusLabel.setStyle("-fx-text-fill: red;");
                return;
            }

            signupBtn.setDisable(true);
            statusLabel.setText("Connecting to server, please wait...");
            statusLabel.setStyle("-fx-text-fill: #e67e22;");

            new Thread(() -> {
                if (!waitForServer(statusLabel, 15, 2000)) {
                    Platform.runLater(() -> {
                        signupBtn.setDisable(false);
                        statusLabel.setText("Could not connect to server. Please try again.");
                        statusLabel.setStyle("-fx-text-fill: red;");
                    });
                    return;
                }
                //hash the password before it ever touches the DB; plain text never stored
                String hash = BCrypt.hashpw(password, BCrypt.gensalt());
                boolean success = db.registerUser(username, hash);
                Platform.runLater(() -> {
                    signupBtn.setDisable(false);
                    if (success) {
                        //don't auto-login after signup; just prompt them to switch tabs
                        statusLabel.setText("Account created! Switch to Login tab.");
                        statusLabel.setStyle("-fx-text-fill: green;");
                        usernameField.clear();
                        passwordField.clear();
                        confirmField.clear();
                    } else {
                        //registerUser returns false on a duplicate username (unique constraint)
                        statusLabel.setText("Username already taken");
                        statusLabel.setStyle("-fx-text-fill: red;");
                    }
                });
            }, "db-signup").start();
        });

        content.getChildren().addAll(
                new Label("Username:"),
                usernameField,
                new Label("Password:"),
                passwordField,
                new Label("Confirm Password:"),
                confirmField,
                signupBtn,
                statusLabel
        );

        Tab tab = new Tab("Sign Up", content);
        tab.setClosable(false);
        return tab;
    }

    public int getCurrentUserId() {
        return currentUserId;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    //polls testConnection() up to maxRetries times with delayMs between attempts
    //runs on a background thread so it's safe to sleep here; UI updates go through Platform.runLater
    private boolean waitForServer(Label statusLabel, int maxRetries, int delayMs) {
        for (int i = 1; i <= maxRetries; i++) {
            if (db.testConnection()) return true;
            final int attempt = i;
            Platform.runLater(() -> statusLabel.setText(
                "Waiting for server... (" + attempt + "/" + maxRetries + ")"));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ex) {
                //restore the interrupt flag and give up; caller will show an error
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
