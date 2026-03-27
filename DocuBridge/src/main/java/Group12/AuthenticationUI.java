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
    private int currentUserId = -1;
    private String currentUsername = "";
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

            loginBtn.setDisable(true);
            statusLabel.setText("Connecting to server, please wait...");
            statusLabel.setStyle("-fx-text-fill: #e67e22;");

            new Thread(() -> {
                if (!waitForServer(statusLabel, 15, 2000)) {
                    Platform.runLater(() -> {
                        loginBtn.setDisable(false);
                        statusLabel.setText("Could not connect to server. Please try again.");
                        statusLabel.setStyle("-fx-text-fill: red;");
                    });
                    return;
                }
                String hash = db.getPasswordHash(username);
                Platform.runLater(() -> {
                    loginBtn.setDisable(false);
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
                String hash = BCrypt.hashpw(password, BCrypt.gensalt());
                boolean success = db.registerUser(username, hash);
                Platform.runLater(() -> {
                    signupBtn.setDisable(false);
                    if (success) {
                        statusLabel.setText("Account created! Switch to Login tab.");
                        statusLabel.setStyle("-fx-text-fill: green;");
                        usernameField.clear();
                        passwordField.clear();
                        confirmField.clear();
                    } else {
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

    private boolean waitForServer(Label statusLabel, int maxRetries, int delayMs) {
        for (int i = 1; i <= maxRetries; i++) {
            if (db.testConnection()) return true;
            final int attempt = i;
            Platform.runLater(() -> statusLabel.setText(
                "Waiting for server... (" + attempt + "/" + maxRetries + ")"));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}