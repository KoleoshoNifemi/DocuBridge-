package Group12;

import javafx.stage.Screen;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.geometry.Orientation;
import javafx.scene.control.Separator;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import javafx.application.Platform;
import netscape.javascript.JSObject;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

public class Toolbar {
    // Guard flag to prevent ComboBox action handlers from firing during programmatic updates
    private boolean isUpdatingUI = false;

    private double dpi; // stores dots per inch for screen scaling
    private VBox toolbarContainer; // holds the visual container for the toolbar
    private HashMap<String, Runnable> voidFunctions; // keeps runnable actions that do not return values
    private HashMap<String, BiConsumer<String, String>> formats; // keeps formatting actions keyed by name
    private HashMap<String, Callable<Object>> returnFunctions; // keeps callable helpers that return data
    private HashMap<String, String> rgbColor; // maps color names to css hex strings for pickers
    private Callable<Object> getTextProperties; // callable that fetches text state from the editor
    private int skipFormatRefreshTicks; // counts down refresh cycles to skip after user picks a value
    private Button bold; // button that toggles bold text
    private Button italic; // button that toggles italic text
    private Button underline; // button that toggles underline text
    private Button strikethrough; // button that toggles strike text
    private Button subscript; // button that applies subscript text
    private Button superscript; // button that applies superscript text
    private ComboBox<String> fontTypeCombo; // dropdown for choosing font family
    private ComboBox<Integer> fontSizeCombo; // dropdown for choosing font size
    private ComboBox<String> alignmentCombo; // dropdown for choosing paragraph alignment
    private ComboBox<String> headersCombo; // dropdown for choosing heading level
    private MenuButton fontColorCombo; // menu for picking font color
    private MenuButton highlightCombo; // menu for picking highlight color
    private Rectangle fontColorBar; // small bar that shows chosen font color
    private Rectangle highlightBar; // small bar that shows chosen highlight color
    private String lastAppliedFontName; // remember last applied font to keep prompt stable while skipping refresh
    private String lastAppliedFontSize; // remember last applied size to keep prompt stable while skipping refresh




    private double readDPI() {
        return Screen.getPrimary().getDpi();
    }

    private void displayTextProperties(){
        Platform.runLater(() -> { // pull current formatting from the editor and refresh the toolbar buttons
            if (skipFormatRefreshTicks > 0) { // let user selections settle before overwriting prompts
                skipFormatRefreshTicks--;
                return;
            }
            try {
                Callable<Object> getFormatsCaller = returnFunctions.get("getFormats");
                if (getFormatsCaller == null) {
                    setAllUIElementsToDefaults();
                    return;
                }

                Object formatObj = getFormatsCaller.call();

                if (!(formatObj instanceof JSObject)) { // use defaults when the response is missing or not a javascript object
                    setAllUIElementsToDefaults();
                    return;
                }

                JSObject formats = (JSObject) formatObj;
                updateUIFromFormats(formats);

            } catch (Exception e) {
                // ignore javascript errors because the editor can change quickly
            }
        });
    }

    private void updateUIFromFormats(JSObject formats) {
        if (skipFormatRefreshTicks > 0) {
            if (fontTypeCombo != null && lastAppliedFontName != null) {
                fontTypeCombo.setPromptText(lastAppliedFontName);
            }
            if (fontSizeCombo != null && lastAppliedFontSize != null) {
                fontSizeCombo.setPromptText(lastAppliedFontSize);
            }
            return; // skip refresh while user-applied value is settling
        }
        isUpdatingUI = true;
        // Update button states

        // Update bold button
        if (isTruthy(formats.getMember("bold"))) {
            bold.setStyle("-fx-font-weight: bold; -fx-text-fill: #0066cc; -fx-background-color: #AAAAAA;");
        } else {
            bold.setStyle("-fx-font-weight: bold;");
        }

        // Update italic button
        if (isTruthy(formats.getMember("italic"))) {
            italic.setStyle("-fx-font-style: italic; -fx-text-fill: #0066cc; -fx-background-color: #AAAAAA;");
        } else {
            italic.setStyle("-fx-font-style: italic;");
        }

        // Update underline button
        if (isTruthy(formats.getMember("underline"))) {
            underline.setStyle("-fx-underline: true; -fx-text-fill: #0066cc; -fx-background-color: #AAAAAA;");
        } else {
            underline.setStyle("-fx-underline: true;");
        }

        // Update subscript/superscript buttons
        Object scriptVal = formats.getMember("script");
        String scriptStr = scriptVal != null ? scriptVal.toString() : "";

        if (scriptStr.equals("sub")) {
            subscript.setStyle("-fx-font-size: 14; -fx-text-fill: #0066cc; -fx-background-color: #AAAAAA;");
        } else {
            subscript.setStyle("-fx-font-size: 14;");
        }

        if (scriptStr.equals("super")) {
            superscript.setStyle("-fx-font-size: 14; -fx-text-fill: #0066cc; -fx-background-color: #AAAAAA;");
        } else {
            superscript.setStyle("-fx-font-size: 14;");
        }

        // Update strikethrough button
         if (isTruthy(formats.getMember("strike"))) {
             strikethrough.setStyle("-fx-background-color: #AAAAAA;");
             if (strikethrough.getGraphic() instanceof Text) {
                 Text strikeText = (Text) strikethrough.getGraphic();
                 strikeText.setStyle("-fx-font-size: 14; -fx-fill: #0066cc;");
             }
         } else {
             strikethrough.setStyle("");
             if (strikethrough.getGraphic() instanceof Text) {
                 Text strikeText = (Text) strikethrough.getGraphic();
                 strikeText.setStyle("-fx-font-size: 14;");
             }
         }

        Object fontVal = formats.getMember("font");
        String fontName = getStringValue(fontVal, "Arial");
        fontName = displayFontName(fontName);
        if (fontTypeCombo != null) {
            fontTypeCombo.setPromptText(fontName); // show current font as a placeholder without altering selection
            // Set the selected value to match the current font
            if (fontTypeCombo.getItems().contains(fontName)) {
                fontTypeCombo.setValue(fontName);
            } else {
                fontTypeCombo.setValue(null);
            }
        }

         Object sizeVal = formats.getMember("size");
         if (fontSizeCombo != null) {
             String sizeDisplay = convertSizeToPt(sizeVal);
             fontSizeCombo.setPromptText(sizeDisplay); // show current size as a placeholder without altering selection
         }

        // Update font color bar
        Object colorVal = formats.getMember("color");
        String colorStr = getStringValue(colorVal, null);
        if (fontColorBar != null) {
            if (colorStr != null) {
                try {
                    fontColorBar.setFill(Color.web(colorStr));
                } catch (Exception e) {
                    fontColorBar.setFill(Color.BLACK);
                }
            } else {
                fontColorBar.setFill(Color.BLACK);
            }
        }

        // Update highlight bar
        Object bgVal = formats.getMember("background");
        String bgStr = getStringValue(bgVal, null);
        if (highlightBar != null) {
            if (bgStr != null) {
                try {
                    highlightBar.setFill(Color.web(bgStr));
                } catch (Exception e) {
                    highlightBar.setFill(Color.TRANSPARENT);
                }
            } else {
                highlightBar.setFill(Color.TRANSPARENT);
            }
        }

        Object alignVal = formats.getMember("align");
        if (alignmentCombo != null) {
            String alignStr = getStringValue(alignVal, null);
            String displayAlign = "Alignment";
            if (alignStr != null && !alignStr.isEmpty()) {
                displayAlign = alignStr.substring(0, 1).toUpperCase() + alignStr.substring(1);
                // Set the selected value to match the current alignment
                for (String item : alignmentCombo.getItems()) {
                    if (item.equalsIgnoreCase(displayAlign)) {
                        alignmentCombo.setValue(item);
                        break;
                    }
                }
            } else {
                alignmentCombo.setValue(null);
            }
            alignmentCombo.setPromptText(displayAlign); // show alignment label while leaving selection untouched
        }
        isUpdatingUI = false;

        Object headerVal = formats.getMember("header");
        if (headersCombo != null) {
            String headerStr = getStringValue(headerVal, null);
            String displayHeader = "Headers";
            if (headerStr != null && !headerStr.isEmpty() && !headerStr.equals("false")) {
                displayHeader = "Header " + headerStr;
            }
            headersCombo.setPromptText(displayHeader); // show header label while leaving selection untouched
        }
    }

    // Helper method to safely get a string value, with fallback default
    private String getStringValue(Object val, String defaultValue) {
        if (val == null) return defaultValue;
        String str = val.toString();
        if (str.equals("undefined") || str.isEmpty()) return defaultValue;
        return str;
    }

    // Helper method to convert pixel size to points
    private String convertSizeToPt(Object sizeVal) {
         if (sizeVal == null) return "12";
         String sizeStr = sizeVal.toString();
         if (sizeStr.equals("undefined") || sizeStr.isEmpty()) return "12";

         if (sizeStr.endsWith("px")) {
            sizeStr = sizeStr.substring(0, sizeStr.length() - 2); // remove the px suffix so we can parse the number
         }
         try {
            int px = Integer.parseInt(sizeStr); // convert the cleaned text to pixels as a whole number
            int pt =  (int)Math.round(px * 3.0 / 4.0); // translate pixels to points for the picker display
             return String.valueOf(pt);
         } catch (NumberFormatException e) {
             return "12";
         }
     }

    // Deals with JavaScript return behaviour
    private boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        String str = value.toString();
        return !str.isEmpty() && !str.equals("false") && !str.equals("undefined");
    }

    private void defineRGBColor(String[] colorNames, Color[] colors) {
        rgbColor = new HashMap<>();

        for (int x = 0; x < colorNames.length; x++){
            if (colors[x] != null) {
                // convert javafx color to css hex text
                String hexColor = String.format("#%02X%02X%02X",
                    (int) (colors[x].getRed() * 255),
                    (int) (colors[x].getGreen() * 255),
                    (int) (colors[x].getBlue() * 255));
                rgbColor.put(colorNames[x], hexColor);
            } else {
                rgbColor.put(colorNames[x], "transparent");
            }
        }
    }

    private void createToolbar() {
        MenuBar menuBar = new MenuBar();

        // MenuBar items
        menuBar.getMenus().addAll(
                createMenu("File", new String[] {"New", "Open", "Save", "Save As"}),
                createMenu("Edit", new String[]{"Undo", "Redo", "Find"}),
                createMenu("Insert", new String[]{"Image", "Link", "List"}),
                createMenu("Format", new String[]{"Align Left", "Align Center", "Align Right", "Justify"})
        );

        ToolBar formatToolbar = new ToolBar();


        // Color data
        Color[] colors = {null, Color.YELLOW, Color.LIME, Color.CYAN, Color.MAGENTA, Color.BLUE,
                Color.RED, Color.NAVY, Color.TEAL, Color.GREEN, Color.PURPLE,
                Color.MAROON, Color.GRAY, Color.SILVER, Color.BLACK, Color.WHITE};
        String[] names = {"No Color", "Yellow", "Lime", "Cyan", "Magenta", "Blue",
                "Red", "Navy", "Teal", "Green", "Purple", "Maroon", "Gray", "Silver", "Black", "White"};
        defineRGBColor(names, colors);


        // ToolBar buttons
        formatToolbar.getItems().addAll(
                createButton("Undo", "-fx-font-size: 14;", "undo", null, null),
                createButton("Redo", "-fx-font-size: 14;", "redo", null, null),
                createSeparator(),
                bold = (createButton("B", "-fx-font-weight: bold;", "format", "bold", "user")),
                italic = (createButton("I", "-fx-font-style: italic;", "format", "italic", "user")),
                underline = (createButton("U", "-fx-underline: true;", "format", "underline", "user")),
                createSeparator(),
                subscript = (createButton("Aₓ", "-fx-font-size: 14;", "applyScript", "sub", "user")),
                superscript = (createButton("Aˣ", "-fx-font-size: 14;", "applyScript", "super", "user")),
                strikethrough = createStrikethroughButton(),
                createSeparator(),
                fontTypeCombo = createFontTypeOptions(),
                fontSizeCombo = createFontSizeOptions(),
                createSeparator(),
                createFontColorContainer(colors, names),
                createHighlightContainer(colors, names),
                createSeparator(),
                alignmentCombo = createAlignmentOptions(),
                headersCombo = createHeadersOptions(),
                createSeparator(),
                createTranslationMenu()
        );

        toolbarContainer = new VBox(menuBar, formatToolbar);
    }


    private Separator createSeparator() {
        return new Separator(Orientation.VERTICAL);
    }

    // Create Menu with items
    private Menu createMenu(String menuName, String[] itemNames) {
        if (menuName.equals("Edit")) {
            return createEditMenu(itemNames);
        } else if (menuName.equals("Format")) {
            return createFormatMenu(itemNames);
        } else if (menuName.equals("File")) {
            return createFileMenu(itemNames);
        } else if (menuName.equals("Insert")) {
            return createInsertMenu(itemNames);
        } else {
            return createDefaultMenu(menuName, itemNames);
        }
    }

    private Menu createEditMenu(String[] itemNames) {
        Menu menu = new Menu("Edit");
        for (String name : itemNames) {
            MenuItem item = new MenuItem(name);
            if (name.equals("Undo")) {
                item.setOnAction(event -> {
                    Runnable undoFn = voidFunctions.get("undo");
                    if (undoFn != null) {
                        undoFn.run();
                    }
                });
            } else if (name.equals("Redo")) {
                item.setOnAction(event -> {
                    Runnable redoFn = voidFunctions.get("redo");
                    if (redoFn != null) {
                        redoFn.run();
                    }
                });
            }
            // Skip Find because no action is set
            menu.getItems().add(item);
        }
        return menu;
    }

    private Menu createFormatMenu(String[] itemNames) {
        Menu menu = new Menu("Format");
        for (String name : itemNames) {
            MenuItem item = new MenuItem(name);
            item.setOnAction(event -> {
                BiConsumer<String, String> alignFormat = formats.get("setTextAlignment");
                if (alignFormat != null) {
                    String alignment = name.replace("Align ", "").toLowerCase();
                    alignFormat.accept(alignment, "user");
                }
            });
            menu.getItems().add(item);
        }
        return menu;
    }

    private Menu createFileMenu(String[] itemNames) {
        Menu menu = new Menu("File");
        for (String name : itemNames) {
            menu.getItems().add(new MenuItem(name));
        }
        return menu;
    }

    private Menu createInsertMenu(String[] itemNames) {
        Menu menu = new Menu("Insert");
        for (String name : itemNames) {
            if (name.equals("List")) {
                // make a submenu for list types to keep the list options together
                Menu listSubmenu = new Menu("List");

                MenuItem bulletedList = new MenuItem("Bulleted List");
                bulletedList.setOnAction(event -> {
                    BiConsumer<String, String> listFormatter = formats.get("insertList");
                    if (listFormatter != null) {
                        listFormatter.accept("bullet", "user");
                    }
                });

                MenuItem numberedList = new MenuItem("Numbered List");
                numberedList.setOnAction(event -> {
                    BiConsumer<String, String> listFormatter = formats.get("insertList");
                    if (listFormatter != null) {
                        listFormatter.accept("ordered", "user");
                    }
                });

                listSubmenu.getItems().addAll(bulletedList, numberedList);
                menu.getItems().add(listSubmenu);
            } else if (name.equals("Image")) {
                MenuItem imageItem = new MenuItem("Image");
                imageItem.setOnAction(event -> {
                    BiConsumer<String, String> imageFormatter = formats.get("insertImage");
                    if (imageFormatter != null) {
                        imageFormatter.accept("", "user");
                    }
                });
                menu.getItems().add(imageItem);
            } else {
                menu.getItems().add(new MenuItem(name));
            }
        }
        return menu;
    }

    private Menu createDefaultMenu(String menuName, String[] itemNames) {
        Menu menu = new Menu(menuName);
        for (String name : itemNames) {
            menu.getItems().add(new MenuItem(name));
        }
        return menu;
    }

    private ComboBox<Integer> createFontSizeOptions(){
        ComboBox<Integer> fontSizeCombo = new ComboBox<>();
        fontSizeCombo.getItems().addAll(8, 9, 10, 11, 12, 14, 16, 18, 24, 30, 36, 48, 60, 72);
        fontSizeCombo.setEditable(true);
        fontSizeCombo.setPrefWidth(92);
        fontSizeCombo.setPromptText("Font Size");
        fontSizeCombo.setStyle("-fx-text-fill: black; -fx-prompt-text-fill: black; -fx-control-inner-background: white;");
        fontSizeCombo.getEditor().setStyle("-fx-text-fill: black; -fx-prompt-text-fill: black;");

        fontSizeCombo.setOnAction(e -> {
            Object value = fontSizeCombo.getValue();

            Integer size = null;

            if (value instanceof Integer) {
                size = (Integer) value;
            } else if (value instanceof String) {
                try {
                    size = Integer.parseInt(((String) value).trim());
                } catch (NumberFormatException ex) {
                    fontSizeCombo.getEditor().clear();
                    return;
                }
            }

            if (size != null) {
                int px = (int) Math.round(size * 4.0 / 3.0);
                skipFormatRefreshTicks = 10; // skip more poll cycles to so applied value sticks
                formats.get("setFontSize").accept(px + "px", "user");
                fontSizeCombo.setPromptText(String.valueOf(size)); // show last applied size
                // Clear selection after the event to avoid ListView index errors and allow reselection of the same size
                Platform.runLater(() -> {
                    fontSizeCombo.getSelectionModel().clearSelection();
                    fontSizeCombo.setValue(null);
                });
            }
        });
        return fontSizeCombo;
    }

    private ComboBox<String> createFontTypeOptions(){
        ComboBox<String> fontType = new ComboBox<>();
        fontType.getItems().addAll("Arial", "Courier New", "Georgia", "Times New Roman");
        fontType.setPrefWidth(100);
        fontType.setPromptText("Font");

        fontType.setOnAction(e -> {
            if (isUpdatingUI) return;
            String value = fontType.getValue();
            if (value != null && !value.isEmpty()) {
                BiConsumer<String, String> format = formats.get("setFontType");
                if (format != null) {
                    skipFormatRefreshTicks = 10; // skip more poll cycles so applied value sticks
                    String normalized = normalizeFontValue(value);
                    format.accept(normalized, "user");
                    fontType.setPromptText(value); // keep last applied font visible as prompt
                }
            }
        });
        return fontType;
    }

    private ComboBox<String> createAlignmentOptions(){
        ComboBox<String> alignment = new ComboBox<>();
        alignment.getItems().addAll("Left", "Center", "Right", "Justify");
        alignment.setPrefWidth(110);
        alignment.setPromptText("Alignment");

        alignment.setOnAction(e -> {
            if (isUpdatingUI) return;
            String value = alignment.getValue();
            if (value != null && !value.isEmpty()) {
                BiConsumer<String, String> format = formats.get("setTextAlignment");
                if (format != null) {
                    skipFormatRefreshTicks = 10; // skip more poll cycles so applied value sticks
                    format.accept(value.toLowerCase(), "user");
                    alignment.setPromptText(value); // show last applied alignment
                }
            }
        });
        return alignment;
    }

    private ComboBox<String> createHeadersOptions(){
        ComboBox<String> headersCombo = new ComboBox<>();
        headersCombo.getItems().addAll("No Header", "Header 1", "Header 2", "Header 3", "Header 4", "Header 5", "Header 6");
        headersCombo.setPrefWidth(110);
        headersCombo.setPromptText("Headers");

        headersCombo.setOnAction(event -> {
            String selected = headersCombo.getValue();
            if (selected != null) {
                BiConsumer<String, String> setHeaderFn = formats.get("setHeader");
                if (setHeaderFn != null) {
                    if (selected.equals("No Header")) {
                        setHeaderFn.accept("none", "user");
                    } else {
                        String level = selected.replace("Header ", "");
                        setHeaderFn.accept(level, "user");
                    }
                    // Defer clearing to allow JavaFX internal state to settle
                    Platform.runLater(() -> {
                        try {
                            headersCombo.setValue(null);
                        } catch (Exception ex) {
                            // Ignore IndexOutOfBoundsException from internal JavaFX ListView
                        }
                    });
                }
            }
        });
        return headersCombo;
    }

    private MenuButton createTranslationMenu(){
        MenuButton translationMenu = new MenuButton("Translation");
        translationMenu.setStyle("-fx-font-size: 14;");

        String[] languages = {
                "No Translation",
                "French",
                "Spanish",
                "German",
                "Arabic",
                "Chinese (Simplified)",
                "Portuguese",
                "Japanese"
        };

        for (String language : languages) {
            MenuItem langItem = new MenuItem(language);
            langItem.setOnAction(event -> {
                if (language.equals("No Translation")) {
                    // Placeholder for clearing translation
                } else {
                    BiConsumer<String, String> translateFn = formats.get("translate");
                    if (translateFn != null) {
                        //
                    }
                }
            });
            translationMenu.getItems().add(langItem);
        }

        return translationMenu;
    }

    private Button createStrikethroughButton() {
        Button button = new Button();
        Text strikeText = new Text("S");
        strikeText.setStyle("-fx-font-size: 14;");
        strikeText.setStrikethrough(true);
        button.setGraphic(strikeText);
        button.setFocusTraversable(false);
        setButtonActions(button, "format", "strike", "user");
        return button;
    }

    private Button createButton(String text, String style, String functionName, String param1, String param2) {
        Button button = new Button(text);
        button.setStyle(style);
        button.setFocusTraversable(false);
        setButtonActions(button, functionName, param1, param2);
        return button;
    }

    private void setButtonActions(Button button, String functionName, String param1, String param2) {
        if (voidFunctions.containsKey(functionName)) {
            button.setOnAction(event -> {
                Runnable function = voidFunctions.get(functionName);
                function.run();
            });
        } else {
            button.setOnAction(event -> {
                BiConsumer<String, String> format = formats.get(functionName);
                // Get the current format state
                try {
                    Callable<Object> getFormatsCaller = returnFunctions.get("getFormats");
                    if (getFormatsCaller != null) {
                        Object formatObj = getFormatsCaller.call();
                        if (formatObj instanceof JSObject) {
                            JSObject currentFormats = (JSObject) formatObj;
                            Object currentValue = currentFormats.getMember(param1);

                            // If the format is already active, toggle it off
                            if (isTruthy(currentValue)) {
                                format.accept(param1, "user");
                                // Immediately update button style to show it's inactive
                                resetButtonStyle(button, param1);
                            } else {
                                // Otherwise apply the format
                                format.accept(param1, param2);
                                // Immediately update button style to show it's active
                                highlightButtonStyle(button, param1);
                            }
                            return;
                        }
                    }
                } catch (Exception e) {
                    // Fall through to default behavior
                }

                // Fallback: just apply the format
                format.accept(param1, param2);
            });
        }
    }

    private void highlightButtonStyle(Button button, String formatType) {
        String baseStyle = button.getStyle();
        if (baseStyle == null) baseStyle = "";

        // Special handling for strikethrough button with Text graphic
        if (formatType.equals("strike") && button.getGraphic() instanceof Text) {
            Text strikeText = (Text) button.getGraphic();
            strikeText.setStyle("-fx-font-size: 14; -fx-fill: #0066cc;");
            button.setStyle("-fx-background-color: #AAAAAA;");
        } else {
            // Add active styling
            if (!baseStyle.contains("-fx-background-color")) {
                button.setStyle(baseStyle + "; -fx-text-fill: #0066cc; -fx-background-color: #AAAAAA;");
            }
        }
    }

    private void resetButtonStyle(Button button, String formatType) {
        // Reset to base style without the highlight
        if (formatType.equals("strike") && button.getGraphic() instanceof Text) {
            Text strikeText = (Text) button.getGraphic();
            strikeText.setStyle("-fx-font-size: 14;");
            button.setStyle("");
        } else {
            switch(formatType) {
                case "bold":
                    button.setStyle("-fx-font-weight: bold;");
                    break;
                case "italic":
                    button.setStyle("-fx-font-style: italic;");
                    break;
                case "underline":
                    button.setStyle("-fx-underline: true;");
                    break;
                case "sub":
                    button.setStyle("-fx-font-size: 14;");
                    break;
                case "super":
                    button.setStyle("-fx-font-size: 14;");
                    break;
                default:
                    button.setStyle("");
            }
        }
    }

    private void setAllUIElementsToDefaults() {
        // Reset all buttons to default state
        bold.setStyle("-fx-font-weight: bold;");
        italic.setStyle("-fx-font-style: italic;");
        underline.setStyle("-fx-underline: true;");
        strikethrough.setStyle("");
        subscript.setStyle("-fx-font-size: 14;");
        superscript.setStyle("-fx-font-size: 14;");

        // Reset color bars to defaults
        if (fontColorBar != null) {
            fontColorBar.setFill(Color.BLACK);
        }
        if (highlightBar != null) {
            highlightBar.setFill(Color.TRANSPARENT);
        }

        // Reset all combos to default prompts (don't touch values to avoid crashes)
        if (fontTypeCombo != null) {
            fontTypeCombo.setPromptText("Arial");
        }
        if (fontSizeCombo != null) {
            fontSizeCombo.setPromptText("12");
        }
        if (alignmentCombo != null) {
            alignmentCombo.setPromptText("Alignment");
        }
        if (headersCombo != null) {
            headersCombo.setPromptText("Headers");
        }
    }

    public Toolbar(HashMap<String, Runnable> voidFunctions,
                   HashMap<String, BiConsumer<String, String>> formats,
                   HashMap<String, Callable<Object>> returnFunctions
                  ) {
        dpi = readDPI();
        this.voidFunctions = voidFunctions;
        this.formats = formats;
        this.returnFunctions = returnFunctions;
        createToolbar();

        // Delay timeline start to allow Quill to fully initialize
        Timeline delayTimeline = new Timeline(new KeyFrame(Duration.millis(1000), event -> {
            Timeline updateTimeline = new Timeline(new KeyFrame(Duration.millis(200), e -> displayTextProperties()));
            updateTimeline.setCycleCount(Timeline.INDEFINITE);
            updateTimeline.play();
        }));
        delayTimeline.play();
    }

    public VBox getView() {
        return toolbarContainer;
    }

    private VBox createFontColorContainer(Color[] colors, String[] names) {
        fontColorCombo = createFontColorCombo(colors, names);

        fontColorBar = new Rectangle(100, 4);
        fontColorBar.setFill(Color.BLACK);
        fontColorBar.setStyle("-fx-stroke: #CCCCCC; -fx-stroke-width: 0.5;");

        VBox container = new VBox(0);
        container.setStyle("-fx-alignment: top-center;");
        container.getChildren().addAll(fontColorCombo, fontColorBar);

        return container;
    }

    private VBox createHighlightContainer(Color[] colors, String[] names) {
        highlightCombo = createHighlightCombo(colors, names);

        highlightBar = new Rectangle(100, 4);
        highlightBar.setFill(Color.TRANSPARENT);
        highlightBar.setStyle("-fx-stroke: #CCCCCC; -fx-stroke-width: 0.5;");

        VBox container = new VBox(0);
        container.setStyle("-fx-alignment: top-center;");
        container.getChildren().addAll(highlightCombo, highlightBar);

        return container;
    }

    private MenuButton createFontColorCombo(Color[] colors, String[] names) {
        MenuButton colorMenu = new MenuButton("Font Color");
        colorMenu.setPrefWidth(100);
        colorMenu.setStyle("-fx-font-size: 14;");

        // Create color grid
        GridPane colorGrid = new GridPane();
        colorGrid.setHgap(5);
        colorGrid.setVgap(5);
        colorGrid.setStyle("-fx-padding: 10; -fx-background-color: white;");

        int index = 1;  // Skip "No Color"
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) {
                if (index < colors.length) {
                    Color color = colors[index] == null ? Color.TRANSPARENT : colors[index];
                    String colorName = names[index];

                    // Create color button with rectangle
                    Rectangle colorRect = new Rectangle(30, 30);
                    colorRect.setFill(color);

                    Button colorButton = new Button();
                    colorButton.setGraphic(colorRect);
                    colorButton.setFocusTraversable(false);
                    colorButton.setStyle("-fx-padding: 2; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
                    colorButton.setTooltip(new Tooltip(colorName));

                    // Set button action
                    colorButton.setOnAction(e -> {
                        BiConsumer<String, String> format = formats.get("setFontColor");
                        if (format != null) {
                            format.accept(rgbColor.get(colorName), "user");
                        }
                        colorMenu.hide();
                    });

                    colorGrid.add(colorButton, col, row);
                    index++;
                }
            }
        }

        CustomMenuItem colorMenuItem = new CustomMenuItem(colorGrid);
        colorMenuItem.setHideOnClick(false);
        colorMenu.getItems().add(colorMenuItem);

        return colorMenu;
    }

    private MenuButton createHighlightCombo(Color[] colors, String[] names) {
        MenuButton highlightMenu = new MenuButton("Highlight");
        highlightMenu.setPrefWidth(100);
        highlightMenu.setStyle("-fx-font-size: 14;");

        // Create color grid
        GridPane colorGrid = new GridPane();
        colorGrid.setHgap(5);
        colorGrid.setVgap(5);
        colorGrid.setStyle("-fx-padding: 10; -fx-background-color: white;");

        int index = 1;  // Skip "No Color"
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) {
                if (index < colors.length) {
                    // Skip white for highlight menu
                    if (index == colors.length - 1) {
                        break;
                    }

                    Color color = colors[index] == null ? Color.TRANSPARENT : colors[index];
                    String colorName = names[index];

                    // Create color button with rectangle
                    Rectangle colorRect = new Rectangle(30, 30);
                    colorRect.setFill(color);

                    Button colorButton = new Button();
                    colorButton.setGraphic(colorRect);
                    colorButton.setFocusTraversable(false);
                    colorButton.setStyle("-fx-padding: 2; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
                    colorButton.setTooltip(new Tooltip(colorName));

                    // Set button action
                    colorButton.setOnAction(e -> {
                        BiConsumer<String, String> format = formats.get("setHighlightColor");
                        if (format != null) {
                            format.accept(rgbColor.get(colorName), "user");
                        }
                        highlightMenu.hide();
                    });

                    colorGrid.add(colorButton, col, row);
                    index++;
                }
            }
        }

        CustomMenuItem colorMenuItem = new CustomMenuItem(colorGrid);
        colorMenuItem.setHideOnClick(false);
        highlightMenu.getItems().add(colorMenuItem);

        return highlightMenu;
    }

    // Helper to normalize font value for Quill (must match whitelist exactly)
    private String normalizeFontValue(String value) {
        // Quill expects exact match to whitelist: 'Arial', 'Courier New', 'Georgia', 'Times New Roman'
        // So just return as-is, but trim and handle nulls
        if (value == null) return "Arial";
        value = value.trim();
        // Defensive: fallback to Arial if not in list
        switch (value.toLowerCase()) {
            case "arial":
                return "Arial";
            case "courier new":
                return "Courier New";
            case "georgia":
                return "Georgia";
            case "times new roman":
                return "Times New Roman";
            default:
                return "Arial";
        }
    }

    private String displayFontName(String fontValue) {
        if (fontValue == null) return "Arial";
        String cleaned = fontValue.trim();
        if (cleaned.isEmpty()) return "Arial";
        // Convert quill whitelist values back to readable names
        String display = cleaned.replace('-', ' ');
        return display.substring(0, 1).toUpperCase() + display.substring(1);
    }
}
