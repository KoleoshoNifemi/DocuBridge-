package Group12;

import javafx.application.Application;
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

    public void start(Stage stage) {
        primaryStage = stage;

        String connectionUrl = "jdbc:sqlserver://docubridge-server.database.windows.net:1433;database=docubridge-db;user=docuadmin@docubridge-server;password=DocuPass123!;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;";

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
        currentUserId = authUI.getCurrentUserId();
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

        // Existing files dropdown
        ComboBox<String> fileCombo = new ComboBox<>();
        fileCombo.setPromptText("Select File");

        // Reverse the list so newest files appear first
        java.util.Collections.reverse(userFiles);
        fileCombo.getItems().addAll(userFiles);

        // Open and Delete buttons
        Button openButton = new Button("Open");
        openButton.setDisable(true);

        Button deleteButton = new Button("Delete");
        deleteButton.setDisable(true);

        // Enable buttons when a file is selected
        fileCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isSelected = newVal != null && !newVal.isEmpty();
            openButton.setDisable(!isSelected);
            deleteButton.setDisable(!isSelected);
        });

        // Open file action
        openButton.setOnAction(e -> {
            if (fileCombo.getValue() != null) {
                currentFileName = fileCombo.getValue();
                dialog.close();
                showEditor();
            }
        });

        // Delete file action
        deleteButton.setOnAction(e -> {
            if (fileCombo.getValue() != null) {
                String fileToDelete = fileCombo.getValue();

                // Confirm deletion
                Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
                confirmDialog.setTitle("Confirm Delete");
                confirmDialog.setHeaderText("Delete '" + fileToDelete + "'?");
                confirmDialog.setContentText("This action cannot be undone.");

                java.util.Optional<ButtonType> confirmResult = confirmDialog.showAndWait();
                if (confirmResult.isPresent() && confirmResult.get() == ButtonType.OK) {
                    // Delete from database
                    db.deleteFile(currentUserId, fileToDelete);

                    // Delete from computer - use same path as WordDocumentManager
                    String documentsFolder = System.getProperty("user.home") + java.io.File.separator + "Documents";
                    String fileName = fileToDelete.endsWith(".docx") ? fileToDelete : fileToDelete + ".docx";
                    String filePath = documentsFolder + java.io.File.separator + "DocuBridge" + java.io.File.separator + fileName;

                    java.io.File file = new java.io.File(filePath);
                    if (file.exists() && file.delete()) {
                        System.out.println("✓ Deleted file: " + filePath);
                    } else if (!file.exists()) {
                        System.out.println("⚠ File doesn't exist at: " + filePath);
                    } else {
                        System.err.println("✗ Failed to delete: " + filePath);
                    }

                    // Remove from dropdown and reset buttons
                    fileCombo.getItems().remove(fileToDelete);
                    fileCombo.setValue(null);
                    openButton.setDisable(true);
                    deleteButton.setDisable(true);
                }
            }
        });

        // Button layout
        HBox buttonBox = new HBox(10);
        buttonBox.getChildren().addAll(openButton, deleteButton);

        // New file input
        TextField newFileField = new TextField();
        newFileField.setPromptText("Enter file name...");

        content.getChildren().addAll(
                new Label("Your Files:"),
                fileCombo,
                buttonBox,
                new Separator(),
                new Label("Create New File:"),
                newFileField
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK,
                ButtonType.CANCEL
        );

        java.util.Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            String newName = newFileField.getText().trim();

            if (!newName.isEmpty()) {
                // Create new file
                db.createFile(currentUserId, newName);
                currentFileName = newName;
                showEditor();
            }
        }
    }

    private void showEditor() {
        try {
            // Load file content
            String content = db.getFileContent(currentUserId, currentFileName);

            editor = new Editor(currentFileName, this::saveAs, this::saveFile, this::newFile, this::openFile);
            if (content != null && !content.isEmpty()) {
                editor.loadContent(content);
            }

            Scene scene = new Scene(editor.getView(), 1200, 800);
            primaryStage.setTitle("DocuBridge - " + currentUsername + " | " + currentFileName);
            primaryStage.setScene(scene);
            primaryStage.setMaximized(true);

            // Auto-save every 5 seconds
            startAutoSave();

            // Save on close
            primaryStage.setOnCloseRequest(e -> {
                saveFile();
                System.out.println("✓ File '" + currentFileName + "' saved!");
            });
        } catch (Exception e) {
            System.err.println("Error opening editor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void newFile() {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("");
        dialog.setTitle("New File");
        dialog.setHeaderText("Create a new document");
        dialog.setContentText("File name:");
        dialog.setGraphic(null);

        java.util.Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                saveFile();
                db.createFile(currentUserId, name.trim());
                currentFileName = name.trim();
                showEditor();
            }
        });
    }

    private void openFile() {
        saveFile();
        showFileSelector();
    }

    private void saveAs() {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(
                currentFileName != null ? currentFileName : "");
        dialog.setTitle("Save As");
        dialog.setHeaderText("Save to Documents/DocuBridge folder");
        dialog.setContentText("File name:");
        dialog.setGraphic(null);

        java.util.Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                String newName = name.trim();
                String content = editor != null ? editor.getContent() : "";

                // Save to disk
                WordDocumentManager.createWordFile(newName, content);

                // Register in DB if it's a new filename, then save content
                if (!newName.equals(currentFileName)) {
                    db.createFile(currentUserId, newName);
                }
                db.saveFileContent(currentUserId, newName, content);

                // Switch current file to the new name
                currentFileName = newName;
                primaryStage.setTitle("DocuBridge - " + currentUsername + " | " + currentFileName);
            }
        });
    }

    private void saveFile() {
        if (editor != null && currentFileName != null && currentUserId != -1) {
            String content = editor.getContent();

            // Save to database
            db.saveFileContent(currentUserId, currentFileName, content);

            // Also save as Word file
            String fileName = currentFileName.endsWith(".docx") ? currentFileName : currentFileName + ".docx";
            WordDocumentManager.createWordFile(fileName, content);

            System.out.println("✓ Saved: " + fileName);
        }
    }

    private void startAutoSave() {
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(
                        javafx.util.Duration.seconds(5),
                        e -> saveFile()
                )
        );
        timeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        timeline.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}