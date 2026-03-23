package Group12;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import javafx.scene.layout.HBox;

public class Main extends Application {
    private DatabaseManager db;
    private AuthenticationUI authUI;
    private Editor editor;
    private Stage primaryStage;
    private int currentUserId;
    private String currentUsername;
    private String currentFileName;

    // ── Collab state ──────────────────────────────────────────────
    private String collabServerHost = null;
    private boolean isHosting = false;
    private String activeRoomCode = null;
    // ─────────────────────────────────────────────────────────────

    public void start(Stage stage) {
        primaryStage = stage;

        java.util.Properties props = new java.util.Properties();
        try (java.io.FileInputStream fis = new java.io.FileInputStream("config.properties")) {
            props.load(fis);
        } catch (java.io.IOException ex) {
            throw new RuntimeException("config.properties not found. Create it with db.url=<your connection string>", ex);
        }
        String connectionUrl = props.getProperty("db.url");

        try {
            db = new DatabaseManager(connectionUrl);
            authUI = new AuthenticationUI(db, this::onLoginSuccess);
            authUI.showAuthScreen(stage);
        } catch (Exception e) {
            System.err.println("Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void onLoginSuccess() {
        currentUserId   = authUI.getCurrentUserId();
        currentUsername = authUI.getCurrentUsername();
        System.out.println("✓ Logged in as: " + currentUsername);
        showFileSelector();
    }

    private void showFileSelector() {
        java.util.List<String> userFiles = db.getUserFiles(currentUserId);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Open File");
        dialog.setHeaderText("Select a file or create a new one");

        VBox content = new VBox(15);
        content.setPadding(new Insets(15));

        ComboBox<String> fileCombo = new ComboBox<>();
        fileCombo.setPromptText("Select File");
        java.util.Collections.reverse(userFiles);
        fileCombo.getItems().addAll(userFiles);

        Button openButton   = new Button("Open");   openButton.setDisable(true);
        Button deleteButton = new Button("Delete"); deleteButton.setDisable(true);

        fileCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isSelected = newVal != null && !newVal.isEmpty();
            openButton.setDisable(!isSelected);
            deleteButton.setDisable(!isSelected);
        });

        openButton.setOnAction(e -> {
            if (fileCombo.getValue() != null) {
                currentFileName = fileCombo.getValue();
                dialog.close();
                showEditor();
            }
        });

        deleteButton.setOnAction(e -> {
            if (fileCombo.getValue() != null) {
                String fileToDelete = fileCombo.getValue();
                Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
                confirmDialog.setTitle("Confirm Delete");
                confirmDialog.setHeaderText("Delete '" + fileToDelete + "'?");
                confirmDialog.setContentText("This action cannot be undone.");
                java.util.Optional<ButtonType> confirmResult = confirmDialog.showAndWait();
                if (confirmResult.isPresent() && confirmResult.get() == ButtonType.OK) {
                    db.deleteFile(currentUserId, fileToDelete);
                    String documentsFolder = System.getProperty("user.home") + java.io.File.separator + "Documents";
                    String fileName = fileToDelete.endsWith(".docx") ? fileToDelete : fileToDelete + ".docx";
                    String filePath = documentsFolder + java.io.File.separator + "DocuBridge" + java.io.File.separator + fileName;
                    java.io.File file = new java.io.File(filePath);
                    if (file.exists() && file.delete()) System.out.println("✓ Deleted file: " + filePath);
                    else if (!file.exists())             System.out.println("⚠ File doesn't exist at: " + filePath);
                    else                                 System.err.println("✗ Failed to delete: " + filePath);
                    fileCombo.getItems().remove(fileToDelete);
                    fileCombo.setValue(null);
                    openButton.setDisable(true);
                    deleteButton.setDisable(true);
                }
            }
        });

        HBox buttonBox = new HBox(10);
        buttonBox.getChildren().addAll(openButton, deleteButton);

        TextField newFileField = new TextField();
        newFileField.setPromptText("Enter file name...");

        content.getChildren().addAll(
                new Label("Your Files:"), fileCombo, buttonBox,
                new Separator(),
                new Label("Create New File:"), newFileField
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        java.util.Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String newName = newFileField.getText().trim();
            if (!newName.isEmpty()) {
                db.createFile(currentUserId, newName);
                currentFileName = newName;
                showEditor();
            }
        }
    }

    private void showEditor() {
        try {
            String content = db.getFileContent(currentUserId, currentFileName);

            // Pass all collab callbacks directly into Editor so Toolbar can call them
            editor = new Editor(
                    currentFileName,
                    this::saveAs,
                    this::saveFile,
                    this::newFile,
                    this::openFile,
                    this::showCollabSetupDialog,   // "Session settings…" menu item
                    this::stopHosting,             // "Stop hosting" menu item
                    this::disconnectFromCollab     // "Disconnect" menu item
            );

            if (content != null && !content.isEmpty()) editor.loadContent(content);

            Scene scene = new Scene(editor.getView(), 1200, 800);
            primaryStage.setTitle("DocuBridge - " + currentUsername + " | " + currentFileName);
            primaryStage.setScene(scene);
            primaryStage.setMaximized(true);

            // Show collab dialog after window is fully laid out
            Platform.runLater(() -> showCollabSetupDialog());

            startAutoSave();

            primaryStage.setOnCloseRequest(e -> {
                saveFile();
                disconnectFromCollab();
                System.out.println("✓ File '" + currentFileName + "' saved!");
            });
        } catch (Exception e) {
            System.err.println("Error opening editor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── Collab dialog ─────────────────────────────────────────────

    private void showCollabSetupDialog() {
        Stage dialogStage = new Stage();
        dialogStage.initOwner(primaryStage);
        dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialogStage.setTitle("Collaboration");
        dialogStage.setResizable(false);

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: white;");

        Label header = new Label("How do you want to work on this document?");
        header.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        RadioButton offlineBtn = new RadioButton("Work offline  (just me)");
        RadioButton hostBtn    = new RadioButton("Host a session  (generate a room code for teammates)");
        RadioButton joinBtn    = new RadioButton("Join a session  (enter a room code)");

        ToggleGroup group = new ToggleGroup();
        offlineBtn.setToggleGroup(group);
        hostBtn.setToggleGroup(group);
        joinBtn.setToggleGroup(group);
        offlineBtn.setSelected(true);

        TextField codeField = new TextField();
        codeField.setPromptText("Enter room code  e.g.  BRIDGE-4821  or  0.tcp.ngrok.io:12345");
        codeField.setDisable(true);
        codeField.setMaxWidth(340);

        joinBtn.selectedProperty().addListener((obs, was, is) -> codeField.setDisable(!is));

        Button continueBtn = new Button("Continue");
        Button cancelBtn   = new Button("Cancel");
        continueBtn.setDefaultButton(true);
        cancelBtn.setCancelButton(true);
        continueBtn.setStyle("-fx-pref-width: 90px;");
        cancelBtn.setStyle("-fx-pref-width: 90px;");

        HBox btnRow = new HBox(10);
        btnRow.getChildren().addAll(continueBtn, cancelBtn);

        root.getChildren().addAll(header, offlineBtn, hostBtn, joinBtn, codeField, new Separator(), btnRow);
        dialogStage.setScene(new Scene(root, 460, 265));

        cancelBtn.setOnAction(e -> dialogStage.close());

        continueBtn.setOnAction(e -> {
            dialogStage.close();

            if (hostBtn.isSelected()) {
                CollabServer.startServer();
                collabServerHost = "localhost";
                isHosting = true;
                activeRoomCode = CollabServer.generateRoomCode();
                connectToCollab("localhost");
                // Update toolbar status
                if (editor != null) editor.getToolbar().updateCollabStatus("hosting", activeRoomCode);
                showRoomCodeDialog(activeRoomCode);

            } else if (joinBtn.isSelected()) {
                String code = codeField.getText().trim().toUpperCase();
                if (!code.isEmpty()) {
                    String host = CollabServer.resolveHostFromCode(code);
                    if (host != null) {
                        collabServerHost = host;
                        isHosting = false;
                        connectToCollab(host);
                        if (editor != null) editor.getToolbar().updateCollabStatus("connected", host);
                    } else {
                        Alert err = new Alert(Alert.AlertType.ERROR);
                        err.setTitle("Invalid Code");
                        err.setHeaderText("Could not connect");
                        err.setContentText("The room code '" + code + "' is not valid.\nMake sure you entered it correctly.");
                        err.showAndWait();
                    }
                }
            } else {
                // Offline
                if (editor != null) editor.getToolbar().updateCollabStatus("offline", "");
            }
        });

        dialogStage.showAndWait();
    }

    private void connectToCollab(String host) {
        if (editor == null) return;
        editor.enableCollab(host, currentUsername, users -> {
            String userList = String.join(", ", users);
            Platform.runLater(() ->
                    primaryStage.setTitle("DocuBridge - " + currentUsername + " | " +
                            currentFileName + "  👥 " + userList)
            );
        });
    }

    private void showRoomCodeDialog(String roomCode) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Room Code");
        alert.setHeaderText("Share this code with your teammates:");

        Label codeLabel = new Label(roomCode);
        codeLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-font-family: monospace; -fx-padding: 10 0 10 0;");

        VBox content = new VBox(8);
        content.getChildren().addAll(
                codeLabel,
                new Label("Teammates select 'Join a session' and enter this code."),
                new Label("⚠ Same WiFi only."),
                new Label("For different networks: run  ngrok tcp 8765  and share the ngrok address instead.")
        );
        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }

    // ── Stop hosting ──────────────────────────────────────────────

    private void stopHosting() {
        if (!isHosting) return;

        // Disconnect our own client connection
        if (editor != null) editor.disconnectCollab();

        // Stop the server
        CollabServer.stopServer();

        isHosting      = false;
        activeRoomCode = null;
        collabServerHost = null;

        if (editor != null) editor.getToolbar().updateCollabStatus("offline", "");

        primaryStage.setTitle("DocuBridge - " + currentUsername + " | " + currentFileName);

        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Session ended");
        info.setHeaderText(null);
        info.setContentText("You've stopped hosting. The session has ended for all teammates.");
        info.showAndWait();
    }

    // ── Disconnect (joiner) ───────────────────────────────────────

    private void disconnectFromCollab() {
        if (editor != null) editor.disconnectCollab();
        isHosting        = false;
        activeRoomCode   = null;
        collabServerHost = null;
        if (editor != null) editor.getToolbar().updateCollabStatus("offline", "");
        primaryStage.setTitle("DocuBridge - " + currentUsername + " | " + currentFileName);
    }

    // ─────────────────────────────────────────────────────────────

    private void newFile() {
        TextInputDialog dialog = new TextInputDialog("");
        dialog.setTitle("New File"); dialog.setHeaderText("Create a new document");
        dialog.setContentText("File name:"); dialog.setGraphic(null);
        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                saveFile();
                db.createFile(currentUserId, name.trim());
                currentFileName = name.trim();
                showEditor();
            }
        });
    }

    private void openFile() { saveFile(); showFileSelector(); }

    private void saveAs() {
        TextInputDialog dialog = new TextInputDialog(currentFileName != null ? currentFileName : "");
        dialog.setTitle("Save As"); dialog.setHeaderText("Save to Documents/DocuBridge folder");
        dialog.setContentText("File name:"); dialog.setGraphic(null);
        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                String newName = name.trim();
                String content = editor != null ? editor.getContent() : "";
                WordDocumentManager.createWordFile(newName, content);
                if (!newName.equals(currentFileName)) db.createFile(currentUserId, newName);
                db.saveFileContent(currentUserId, newName, content);
                currentFileName = newName;
                primaryStage.setTitle("DocuBridge - " + currentUsername + " | " + currentFileName);
            }
        });
    }

    private void saveFile() {
        if (editor != null && currentFileName != null && currentUserId != -1) {
            String content = editor.getContent();
            db.saveFileContent(currentUserId, currentFileName, content);
            String fileName = currentFileName.endsWith(".docx") ? currentFileName : currentFileName + ".docx";
            WordDocumentManager.createWordFile(fileName, content);
            System.out.println("✓ Saved: " + fileName);
        }
    }

    private void startAutoSave() {
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(5), e -> {
                    saveFile();
                    if (editor != null && editor.isCollabConnected()) {
                        editor.getCollabClient().sendFullContent(editor.getContent());
                    }
                })
        );
        timeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        timeline.play();
    }

    public static void main(String[] args) { launch(args); }
}