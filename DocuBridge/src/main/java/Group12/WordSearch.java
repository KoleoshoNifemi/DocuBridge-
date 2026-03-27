package Group12;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Separator;
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
    //scriptExecute runs JS in the editor; the boolean flag controls whether it needs to be on the FX thread
    private final BiConsumer<String, Boolean> scriptExecute;
    //textFetcher pulls the current plain-text content out of Quill so we can run regex against it
    private final Supplier<String> textFetcher;
    private final WebView webView;

    private Stage searchStage;
    private TextField searchField;
    private TextField replaceField;
    private CheckBox regexBox;
    private Label countLabel;

    //These track the last known state so the live search thread can detect changes
    private volatile String lastDocText = "";
    private volatile String lastSearch = "";
    private volatile boolean lastRegexState = false;
    //currentIndex is AtomicInteger because it's read/written from both the search thread and the FX thread
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    //matchIndices holds [start, end] pairs for every match in the current document
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
            //If the popup is already open just bring it to the front instead of creating a second one
            if (searchStage != null && searchStage.isShowing()) {
                searchStage.requestFocus();
                searchField.requestFocus();
                return;
            }

            // UI Elements
            searchField = new TextField(lastSearch);
            searchField.setPrefWidth(200);
            replaceField = new TextField();
            replaceField.setPrefWidth(200);
            regexBox = new CheckBox("Regex");
            regexBox.setSelected(lastRegexState);
            countLabel = new Label("0/0");
            Button nextBtn    = new Button("Next");
            Button prevBtn    = new Button("Prev");
            Button replaceBtn    = new Button("Replace");
            Button replaceAllBtn = new Button("Replace All");

            // Layout
            Label findLbl    = new Label("Find:");
            Label replaceLbl = new Label("Replace:");
            findLbl.setPrefWidth(65);
            replaceLbl.setPrefWidth(65);
            HBox findRow    = new HBox(8, findLbl, searchField, regexBox);
            HBox replaceRow = new HBox(8, replaceLbl, replaceField);
            HBox btnRow     = new HBox(8, prevBtn, nextBtn, countLabel,
                                       new Separator(Orientation.VERTICAL),
                                       replaceBtn, replaceAllBtn);
            VBox root = new VBox(10, findRow, replaceRow, btnRow);
            root.setPadding(new Insets(15));
            root.setStyle("-fx-background-color: white;");

            // Stage Setup
            searchStage = new Stage();
            //UTILITY style gives a smaller title bar without minimize/maximize buttons
            searchStage.initStyle(StageStyle.UTILITY);
            searchStage.setTitle("Find & Replace");
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
            replaceBtn.setOnAction(e -> replaceCurrentMatch());
            replaceAllBtn.setOnAction(e -> replaceAllMatches());
            //Any time regex mode is toggled, redo the search immediately
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
        //Remove all yellow highlights before closing so the document looks normal again
        clearHighlight();

        if (searchStage != null) {
            searchStage.close();
        }

        //Give focus back to the editor so the user can keep typing straight away
        Platform.runLater(() -> {
            webView.requestFocus();
            scriptExecute.accept("quill.focus();", false);
        });
    }

    private void highlightMatch(int idx) {
        synchronized (matchIndices) {
            if (matchIndices.isEmpty()) return;

            //Wrap around so Next on the last match goes back to the first, and vice versa
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

    //These are simple wrappers - the +1/-1 wrapping is handled inside highlightMatch
    private void nextMatch() { synchronized(matchIndices) { if(!matchIndices.isEmpty()) highlightMatch(currentIndex.get() + 1); } }
    private void prevMatch() { synchronized(matchIndices) { if(!matchIndices.isEmpty()) highlightMatch(currentIndex.get() - 1); } }

    private void clearHighlight() {
        //Reset every character's background to false (i.e., no highlight) across the whole document
        scriptExecute.accept("quill.formatText(0, quill.getLength(), 'background', false, 'silent');", true);
    }

    private void replaceCurrentMatch() {
        String replacement = replaceField.getText();
        int start, length;
        synchronized (matchIndices) {
            if (matchIndices.isEmpty()) return;
            int[] range = matchIndices.get(currentIndex.get());
            start  = range[0];
            length = range[1] - range[0];
        }
        String script = String.format(
            "quill.deleteText(%d, %d, 'user'); quill.insertText(%d, '%s', 'user');",
            start, length, start, escapeForJs(replacement)
        );
        scriptExecute.accept(script, true);
        // Re-run search so indices reflect the changed document
        Platform.runLater(this::triggerSearch);
    }

    private void replaceAllMatches() {
        String replacement = replaceField.getText();
        synchronized (matchIndices) {
            if (matchIndices.isEmpty()) return;
            String escaped = escapeForJs(replacement);
            //Process replacements in reverse order so that earlier char indices stay valid
            //as the document length changes with each substitution
            StringBuilder sb = new StringBuilder();
            for (int i = matchIndices.size() - 1; i >= 0; i--) {
                int[] range = matchIndices.get(i);
                sb.append(String.format(
                    "quill.deleteText(%d, %d, 'user'); quill.insertText(%d, '%s', 'user');",
                    range[0], range[1] - range[0], range[0], escaped
                ));
            }
            scriptExecute.accept(sb.toString(), true);
            matchIndices.clear();
        }
        Platform.runLater(() -> {
            countLabel.setText("0/0");
            triggerSearch();
        });
    }

    //Escape characters that would break the JS string literals we're building dynamically
    private static String escapeForJs(String text) {
        return text
            .replace("\\", "\\\\")
            .replace("'",  "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private void startLiveSearchThread() {
        stopThread = false;
        searchThread = new Thread(() -> {
            //Poll every 400 ms and re-run the search if anything changed (text, query, or regex toggle)
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
        searchThread.setDaemon(true); //don't block JVM shutdown if the app closes with this thread running
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
            //When not in regex mode, quote the search string so special characters are treated as literals
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
            //An invalid regex pattern will throw - show feedback in the label instead of crashing
            Platform.runLater(() -> countLabel.setText("Invalid Regex"));
        }
    }

    private String getDocumentTextSync() {
        //If we're already on the FX thread, call the supplier directly to avoid a deadlock
        if (Platform.isFxApplicationThread()) return textFetcher.get();
        //Otherwise submit to the FX thread and block (with a timeout) so the background thread gets the result
        FutureTask<String> task = new FutureTask<>(textFetcher::get);
        Platform.runLater(task);
        try { return task.get(400, TimeUnit.MILLISECONDS); } catch (Exception e) { return ""; }
    }
}
