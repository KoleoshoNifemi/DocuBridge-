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
        java.util.Collections.reverse(userFiles);

        Stage dialog = new Stage();
        dialog.initOwner(primaryStage);
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.setTitle("DocuBridge");
        dialog.setResizable(false);

        VBox root = new VBox(10);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: white;");

        Label header = new Label("What would you like to do?");
        header.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        RadioButton offlineBtn = new RadioButton("Work Offline");
        RadioButton joinBtn    = new RadioButton("Join a Session");
        ToggleGroup group = new ToggleGroup();
        offlineBtn.setToggleGroup(group);
        joinBtn.setToggleGroup(group);
        offlineBtn.setSelected(true);

        // ── Work Offline area ──────────────────────────────────────────
        VBox offlineArea = new VBox(8);
        offlineArea.setPadding(new Insets(2, 0, 4, 20));

        ComboBox<String> fileCombo = new ComboBox<>();
        fileCombo.setPromptText("Select existing file");
        fileCombo.getItems().addAll(userFiles);
        fileCombo.setMaxWidth(280);

        String blueBtn = "-fx-background-color: #0096C9; -fx-text-fill: white; -fx-font-weight: bold;";
        Button openButton   = new Button("Open");   openButton.setDisable(true); openButton.setStyle(blueBtn);
        Button deleteButton = new Button("Delete"); deleteButton.setDisable(true);
        fileCombo.valueProperty().addListener((obs, o, n) -> {
            boolean sel = n != null && !n.isEmpty();
            openButton.setDisable(!sel);
            deleteButton.setDisable(!sel);
        });

        HBox fileRow = new HBox(8, fileCombo, openButton, deleteButton);

        TextField newFileField = new TextField();
        newFileField.setPromptText("New file name...");
        newFileField.setMaxWidth(280);

        offlineArea.getChildren().addAll(
                new Label("Your Files:"), fileRow,
                new Separator(),
                new Label("Create New File:"), newFileField
        );

        // ── Join area ──────────────────────────────────────────────────
        VBox joinArea = new VBox(6);
        joinArea.setPadding(new Insets(2, 0, 4, 20));
        joinArea.setDisable(true);

        TextField codeField = new TextField();
        codeField.setPromptText("Same network: 192.168.x.x   |   Different network: host:port");
        codeField.setMaxWidth(380);
        joinArea.getChildren().addAll(new Label("Room code or address:"), codeField);

        group.selectedToggleProperty().addListener((obs, old, now) -> {
            boolean isJoin = now == joinBtn;
            offlineArea.setDisable(isJoin);
            joinArea.setDisable(!isJoin);
        });

        // ── Buttons ────────────────────────────────────────────────────
        Button continueBtn = new Button("Continue");
        Button cancelBtn   = new Button("Cancel");
        continueBtn.setDefaultButton(true);
        cancelBtn.setCancelButton(true);
        continueBtn.setStyle("-fx-pref-width: 90px; -fx-background-color: #0096C9; -fx-text-fill: white; -fx-font-weight: bold;");
        cancelBtn.setStyle("-fx-pref-width: 90px;");
        HBox btnRow = new HBox(10, continueBtn, cancelBtn);

        root.getChildren().addAll(
                header, offlineBtn, offlineArea, joinBtn, joinArea,
                new Separator(), btnRow
        );
        dialog.setScene(new Scene(root, 560, 420));

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
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Confirm Delete");
                confirm.setHeaderText("Delete '" + fileToDelete + "'?");
                confirm.setContentText("This action cannot be undone.");
                confirm.showAndWait().ifPresent(result -> {
                    if (result == ButtonType.OK) {
                        db.deleteFile(currentUserId, fileToDelete);
                        String docs = System.getProperty("user.home") + java.io.File.separator + "Documents";
                        String fn   = fileToDelete.endsWith(".docx") ? fileToDelete : fileToDelete + ".docx";
                        java.io.File file = new java.io.File(docs + java.io.File.separator + "DocuBridge" + java.io.File.separator + fn);
                        if (file.exists() && file.delete()) System.out.println("✓ Deleted file: " + file.getPath());
                        else if (!file.exists())             System.out.println("⚠ File not found: " + file.getPath());
                        else                                 System.err.println("✗ Failed to delete: " + file.getPath());
                        fileCombo.getItems().remove(fileToDelete);
                        fileCombo.setValue(null);
                    }
                });
            }
        });

        continueBtn.setOnAction(e -> {
            if (offlineBtn.isSelected()) {
                String newName = newFileField.getText().trim();
                if (!newName.isEmpty()) {
                    db.createFile(currentUserId, newName);
                    currentFileName = newName;
                    dialog.close();
                    showEditor();
                }
            } else {
                String code = codeField.getText().trim();
                if (!code.isEmpty()) {
                    String host = CollabServer.resolveHostFromCode(code.toUpperCase());
                    if (host == null) host = code;
                    currentFileName = null; // will be assigned by the server's "joined" ack
                    dialog.close();
                    showEditor(host);
                }
            }
        });

        cancelBtn.setOnAction(e -> dialog.close());
        dialog.showAndWait();
    }

    private void showEditor() { showEditor(null); }

    private void showEditor(String joinAddress) {
        try {
            String displayName = currentFileName != null ? currentFileName : "Connecting...";
            String content     = currentFileName != null ? db.getFileContent(currentUserId, currentFileName) : null;

            editor = new Editor(
                    displayName,
                    this::saveAs,
                    this::saveFile,
                    this::newFile,
                    this::openFile,
                    this::showCollabSetupDialog,
                    this::stopHosting,
                    this::disconnectFromCollab
            );

            if (content != null && !content.isEmpty()) editor.loadContent(content);

            Scene scene = new Scene(editor.getView(), 1200, 800);
            primaryStage.setTitle("DocuBridge - " + currentUsername + " | " + displayName);
            primaryStage.setScene(scene);
            primaryStage.setMaximized(true);

            if (joinAddress != null) {
                final String addr = joinAddress;
                Platform.runLater(() -> {
                    connectToCollab(addr);
                    editor.getToolbar().updateCollabStatus("connected", addr);
                });
            }

            startAutoSave();

            primaryStage.setOnCloseRequest(e -> {
                saveFile();
                disconnectFromCollab();
                CollabServer.stopServer();
                System.out.println("✓ File '" + (currentFileName != null ? currentFileName : "unsaved") + "' saved!");
                System.exit(0);
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
        RadioButton joinBtn    = new RadioButton("Join a session  (enter a room code or tunnel address)");

        ToggleGroup group = new ToggleGroup();
        offlineBtn.setToggleGroup(group);
        hostBtn.setToggleGroup(group);
        joinBtn.setToggleGroup(group);
        offlineBtn.setSelected(true);

        // ── updated placeholder to reflect localhost.run format ──
        TextField codeField = new TextField();
        codeField.setPromptText("Same network: 192.168.x.x   |   Different network: host:port");
        codeField.setDisable(true);
        codeField.setMaxWidth(380);

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
        dialogStage.setScene(new Scene(root, 480, 265));

        cancelBtn.setOnAction(e -> dialogStage.close());

        continueBtn.setOnAction(e -> {
            dialogStage.close();

            if (hostBtn.isSelected()) {
                CollabServer.startServer();
                collabServerHost = "localhost";
                isHosting = true;
                activeRoomCode = CollabServer.generateRoomCode();
                connectToCollab("localhost");
                if (editor != null) editor.getToolbar().updateCollabStatus("hosting", activeRoomCode);
                final String code = activeRoomCode;
                Platform.runLater(() -> showRoomCodeDialog(code));

            } else if (joinBtn.isSelected()) {
                String code = codeField.getText().trim();
                // Don't uppercase — localhost.run hostnames are case sensitive
                if (!code.isEmpty()) {
                    String host = CollabServer.resolveHostFromCode(code.toUpperCase());
                    // If resolveHostFromCode didn't find a registered code, treat the
                    // raw input as a direct address (covers localhost.run hostnames)
                    if (host == null) host = code;
                    collabServerHost = host;
                    isHosting = false;
                    connectToCollab(host);
                    if (editor != null) editor.getToolbar().updateCollabStatus("connected", host);
                }
            } else {
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
                            (currentFileName != null ? currentFileName : "Connecting...") + "  👥 " + userList)
            );
        });
        editor.getCollabClient().setOnFileNameChanged(serverFileName -> {
            // Create a local DB entry for this file if the user doesn't have one yet
            if (!db.getUserFiles(currentUserId).contains(serverFileName)) {
                db.createFile(currentUserId, serverFileName);
            }
            currentFileName = serverFileName;
            primaryStage.setTitle("DocuBridge - " + currentUsername + " | " + currentFileName);
        });
    }

    private void showRoomCodeDialog(String roomCode) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(primaryStage);
        alert.setTitle("Session Started");
        alert.setHeaderText("Share with your teammates:");

        Label sameWifiHeader = new Label("👥  Same network (hotspot/LAN) — your IP:");
        sameWifiHeader.setStyle("-fx-font-weight: bold;");
        Label codeLabel = new Label(roomCode);
        codeLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-font-family: monospace; -fx-padding: 2 0 8 0;");

        Label diffWifiHeader = new Label("🌐  Different WiFi — ngrok:");
        diffWifiHeader.setStyle("-fx-font-weight: bold; -fx-padding: 6 0 0 0;");
        Label step1 = new Label("Step 1 — Open a terminal and run:");
        Label ngrokCmd = new Label("    ngrok tcp 8765");
        ngrokCmd.setStyle("-fx-font-family: monospace; -fx-background-color: #f0f0f0; -fx-padding: 6; -fx-font-size: 12px;");
        Label step2 = new Label("Step 2 — Copy the address it shows e.g.  0.tcp.ngrok.io:12345");
        Label step3 = new Label("Step 3 — Send that address to teammates. They paste it into the Join field.");
        step2.setWrapText(true);
        step3.setWrapText(true);
        Label keepOpen = new Label("⚠ Keep the terminal open while your session is active.");
        keepOpen.setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold; -fx-font-size: 11px;");

        Button firstTimeBtn = new Button("First time hosting? Click here to set up ngrok");
        firstTimeBtn.setStyle("-fx-text-fill: #2980b9; -fx-background-color: transparent; -fx-cursor: hand; -fx-underline: true; -fx-padding: 6 0 0 0;");
        firstTimeBtn.setOnAction(e -> showNgrokSetupGuide());

        VBox content = new VBox(5);
        content.getChildren().addAll(sameWifiHeader, codeLabel, new Separator(),
                diffWifiHeader, step1, ngrokCmd, step2, step3, keepOpen,
                new Separator(), firstTimeBtn);
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }

    private void showNgrokSetupGuide() {
        Stage guide = new Stage();
        guide.initOwner(primaryStage);
        guide.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        guide.setTitle("ngrok Setup Guide");
        guide.setResizable(false);

        VBox root = new VBox(12);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: white;");
        root.setPrefWidth(520);

        Label header = new Label("Setting up ngrok for the first time");
        header.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        Label intro = new Label("ngrok lets teammates on different networks join your session by creating a secure tunnel to your machine.");
        intro.setWrapText(true);
        intro.setStyle("-fx-text-fill: #555;");

        // Step 1
        Label s1 = new Label("Step 1 — Create a free account");
        s1.setStyle("-fx-font-weight: bold;");
        Label s1detail = new Label("Go to https://ngrok.com and sign up for a free account.");
        s1detail.setWrapText(true);

        // Step 2
        Label s2 = new Label("Step 2 — Download and install ngrok");
        s2.setStyle("-fx-font-weight: bold;");
        Label s2detail = new Label("After signing in, go to the Setup & Installation page. Download the version for your OS and follow the install instructions.");
        s2detail.setWrapText(true);

        // Step 3
        Label s3 = new Label("Step 3 — Connect your account");
        s3.setStyle("-fx-font-weight: bold;");
        Label s3detail = new Label("On the ngrok dashboard, copy your authtoken. Then run this once in a terminal:");
        s3detail.setWrapText(true);
        Label authtokenCmd = new Label("    ngrok config add-authtoken <your-token-here>");
        authtokenCmd.setStyle("-fx-font-family: monospace; -fx-background-color: #f0f0f0; -fx-padding: 6; -fx-font-size: 12px;");

        // Step 4
        Label s4 = new Label("Step 4 — Every time you host");
        s4.setStyle("-fx-font-weight: bold;");
        Label s4detail = new Label("Open a terminal and run:");
        Label ngrokCmd = new Label("    ngrok tcp 8765");
        ngrokCmd.setStyle("-fx-font-family: monospace; -fx-background-color: #f0f0f0; -fx-padding: 6; -fx-font-size: 12px;");
        Label s4detail2 = new Label("Copy the address it shows (e.g. 6.tcp.ngrok.io:13407) and share it with your teammates. Keep the terminal open for the whole session.");
        s4detail2.setWrapText(true);

        Label note = new Label("You only need to do steps 1–3 once. After that it's just step 4 every time you host.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");

        Button closeBtn = new Button("Got it");
        closeBtn.setDefaultButton(true);
        closeBtn.setStyle("-fx-pref-width: 80px;");
        closeBtn.setOnAction(e -> guide.close());
        HBox btnRow = new HBox(closeBtn);

        root.getChildren().addAll(
                header, intro, new Separator(),
                s1, s1detail,
                s2, s2detail,
                s3, s3detail, authtokenCmd,
                s4, s4detail, ngrokCmd, s4detail2,
                new Separator(), note, btnRow
        );

        guide.setScene(new Scene(root));
        guide.showAndWait();
    }

    // ── Stop hosting ──────────────────────────────────────────────

    private void stopHosting() {
        if (!isHosting) return;
        if (editor != null) editor.disconnectCollab();
        CollabServer.stopServer();
        isHosting        = false;
        activeRoomCode   = null;
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