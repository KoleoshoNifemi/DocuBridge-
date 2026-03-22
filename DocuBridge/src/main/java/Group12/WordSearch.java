package Group12;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.scene.web.WebView;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class WordSearch {
    private final BiConsumer<String, Boolean> scriptExecute;
    private final Supplier<String> textFetcher;
    private final WebView webView;

    private Stage searchStage;
    private TextField searchField;
    private CheckBox regexBox;
    private Label countLabel;

    private volatile String lastDocText = "";
    private volatile String lastSearch = "";
    private volatile boolean lastRegexState = false;
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private final List<int[]> matchIndices = new ArrayList<>();
    private Thread searchThread;
    private volatile boolean stopThread = false;

    public WordSearch(BiConsumer<String, Boolean> scriptExecute, Supplier<String> textFetcher, WebView webView) {
        this.scriptExecute = scriptExecute;
        this.textFetcher = textFetcher;
        this.webView = webView;
    }

    public void showSearchPopup() {
        Platform.runLater(() -> {
            if (searchStage != null && searchStage.isShowing()) {
                searchStage.requestFocus();
                searchField.requestFocus();
                return;
            }

            // UI Elements
            searchField = new TextField(lastSearch);
            regexBox = new CheckBox("Regex");
            regexBox.setSelected(lastRegexState);
            countLabel = new Label("0/0");
            Button nextBtn = new Button("Next");
            Button prevBtn = new Button("Prev");

            // Layout
            HBox top = new HBox(8, new Label("Find:"), searchField, regexBox);
            HBox bot = new HBox(10, prevBtn, nextBtn, countLabel);
            VBox root = new VBox(12, top, bot);
            root.setPadding(new Insets(15));
            root.setStyle("-fx-background-color: white;");

            // Stage Setup
            searchStage = new Stage();
            searchStage.initStyle(StageStyle.UTILITY);
            searchStage.setTitle("Find");
            searchStage.setAlwaysOnTop(true);
            searchStage.setResizable(false);

            Window owner = webView.getScene().getWindow();
            searchStage.initOwner(owner);
            searchStage.setScene(new Scene(root));

            // Position: Top Left, shifted down to clear Undo/Redo toolbar
            if (owner != null) {
                searchStage.setX(owner.getX() + 20);
                searchStage.setY(owner.getY() + 150); // Increased from 80 to 150
            }

            // Native Close Handler
            searchStage.setOnCloseRequest(e -> closePopup());

            nextBtn.setOnAction(e -> nextMatch());
            prevBtn.setOnAction(e -> prevMatch());
            regexBox.setOnAction(e -> triggerSearch());

            searchField.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    nextMatch();
                    e.consume();
                }
                if (e.getCode() == KeyCode.ESCAPE) {
                    closePopup();
                }
            });

            searchStage.show();
            searchField.requestFocus();
            startLiveSearchThread();
        });
    }

    private void closePopup() {
        stopThread = true;
        clearHighlight();

        if (searchStage != null) {
            searchStage.close();
        }

        Platform.runLater(() -> {
            webView.requestFocus();
            scriptExecute.accept("quill.focus();", false);
        });
    }

    private void highlightMatch(int idx) {
        synchronized (matchIndices) {
            if (matchIndices.isEmpty()) return;

            // Calculate the circular index
            idx = (idx + matchIndices.size()) % matchIndices.size();
            currentIndex.set(idx);
            int[] range = matchIndices.get(idx);

            // Update the UI label (e.g., "1/5")
            Platform.runLater(() -> {
                if (countLabel != null) {
                    countLabel.setText((currentIndex.get() + 1) + "/" + matchIndices.size());
                }
            });

            // HIGHLIGHT ONLY:
            // We clear all existing highlights first, then apply yellow to the specific range.
            // The 'silent' source prevents these highlights from being added to the Undo/Redo stack.
            String script = String.format(
                    "try {" +
                            "  quill.formatText(0, quill.getLength(), 'background', false, 'silent');" +
                            "  quill.formatText(%d, %d, 'background', 'yellow', 'silent');" +
                            "} catch(e) { console.error('Highlight Error:', e); }",
                    range[0], range[1] - range[0]
            );

            scriptExecute.accept(script, true);
        }
    }

    private void nextMatch() { synchronized(matchIndices) { if(!matchIndices.isEmpty()) highlightMatch(currentIndex.get() + 1); } }
    private void prevMatch() { synchronized(matchIndices) { if(!matchIndices.isEmpty()) highlightMatch(currentIndex.get() - 1); } }

    private void clearHighlight() {
        scriptExecute.accept("quill.formatText(0, quill.getLength(), 'background', false, 'silent');", true);
    }

    private void startLiveSearchThread() {
        stopThread = false;
        searchThread = new Thread(() -> {
            while (!stopThread) {
                String docText = getDocumentTextSync();
                String search = (searchField != null) ? searchField.getText() : "";
                boolean isRegex = (regexBox != null) && regexBox.isSelected();

                if (!docText.equals(lastDocText) || !search.equals(lastSearch) || isRegex != lastRegexState) {
                    lastDocText = docText;
                    lastSearch = search;
                    lastRegexState = isRegex;
                    Platform.runLater(this::triggerSearch);
                }
                try { Thread.sleep(400); } catch (InterruptedException e) { break; }
            }
        });
        searchThread.setDaemon(true);
        searchThread.start();
    }

    private void triggerSearch() {
        if (searchField == null) return;
        String search = searchField.getText();
        String docText = getDocumentTextSync();
        boolean isRegex = regexBox.isSelected();

        if (search.isEmpty() || docText.isEmpty()) {
            clearHighlight();
            Platform.runLater(() -> countLabel.setText("0/0"));
            return;
        }

        try {
            Pattern pattern = isRegex ?
                    Pattern.compile(search, Pattern.CASE_INSENSITIVE) :
                    Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE);

            Matcher matcher = pattern.matcher(docText);
            List<int[]> found = new ArrayList<>();
            while (matcher.find()) found.add(new int[]{matcher.start(), matcher.end()});

            synchronized (matchIndices) {
                matchIndices.clear();
                matchIndices.addAll(found);
            }

            Platform.runLater(() -> {
                int total = found.size();
                countLabel.setText(total > 0 ? (currentIndex.get() + 1) + "/" + total : "0/0");
                if (total > 0) highlightMatch(0);
                else clearHighlight();
            });
        } catch (Exception e) {
            Platform.runLater(() -> countLabel.setText("Invalid Regex"));
        }
    }

    private String getDocumentTextSync() {
        if (Platform.isFxApplicationThread()) return textFetcher.get();
        FutureTask<String> task = new FutureTask<>(textFetcher::get);
        Platform.runLater(task);
        try { return task.get(400, TimeUnit.MILLISECONDS); } catch (Exception e) { return ""; }
    }
}