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
    private boolean isUpdatingUI = false;

    private double dpi;
    private VBox toolbarContainer;
    private HashMap<String, Runnable> voidFunctions;
    private HashMap<String, BiConsumer<String, String>> formats;
    private HashMap<String, Callable<Object>> returnFunctions;
    private HashMap<String, String> rgbColor;
    private Callable<Object> getTextProperties;
    private int skipFormatRefreshTicks;
    private Button bold;
    private Button italic;
    private Button underline;
    private Button strikethrough;
    private Button subscript;
    private Button superscript;
    private ComboBox<String> fontTypeCombo;
    private ComboBox<Integer> fontSizeCombo;
    private ComboBox<String> alignmentCombo;
    private ComboBox<String> headersCombo;
    private MenuButton fontColorCombo;
    private MenuButton highlightCombo;
    private Rectangle fontColorBar;
    private Rectangle highlightBar;
    private String lastAppliedFontName;
    private String lastAppliedFontSize;

    private MenuItem collabStatusItem;
    private MenuItem stopHostingItem;
    private MenuButton translationMenu;

    private double readDPI() {
        return Screen.getPrimary().getDpi();
    }

    private void displayTextProperties(){
        Platform.runLater(() -> {
            if (skipFormatRefreshTicks > 0) {
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

                if (!(formatObj instanceof JSObject)) {
                    setAllUIElementsToDefaults();
                    return;
                }

                JSObject formats = (JSObject) formatObj;
                updateUIFromFormats(formats);

            } catch (Exception e) {
                // ignore
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
            return;
        }
        isUpdatingUI = true;

        if (isTruthy(formats.getMember("bold"))) {
            bold.setStyle("-fx-font-weight: bold; -fx-text-fill: #0066cc; -fx-background-color: #AAAAAA;");
        } else {
            bold.setStyle("-fx-font-weight: bold;");
        }

        if (isTruthy(formats.getMember("italic"))) {
            italic.setStyle("-fx-font-style: italic; -fx-text-fill: #0066cc; -fx-background-color: #AAAAAA;");
        } else {
            italic.setStyle("-fx-font-style: italic;");
        }

        if (isTruthy(formats.getMember("underline"))) {
            underline.setStyle("-fx-underline: true; -fx-text-fill: #0066cc; -fx-background-color: #AAAAAA;");
        } else {
            underline.setStyle("-fx-underline: true;");
        }

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
            fontTypeCombo.setPromptText(fontName);
            if (fontTypeCombo.getItems().contains(fontName)) {
                fontTypeCombo.setValue(fontName);
            } else {
                fontTypeCombo.setValue(null);
            }
        }

        Object sizeVal = formats.getMember("size");
        if (fontSizeCombo != null) {
            String sizeDisplay = convertSizeToPt(sizeVal);
            fontSizeCombo.setPromptText(sizeDisplay);
        }

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
                for (String item : alignmentCombo.getItems()) {
                    if (item.equalsIgnoreCase(displayAlign)) {
                        alignmentCombo.setValue(item);
                        break;
                    }
                }
            } else {
                alignmentCombo.setValue(null);
            }
            alignmentCombo.setPromptText(displayAlign);
        }
        isUpdatingUI = false;

        Object headerVal = formats.getMember("header");
        if (headersCombo != null) {
            String headerStr = getStringValue(headerVal, null);
            String displayHeader = "Headers";
            if (headerStr != null && !headerStr.isEmpty() && !headerStr.equals("false")) {
                displayHeader = "Header " + headerStr;
            }
            headersCombo.setPromptText(displayHeader);
        }
    }

    private String getStringValue(Object val, String defaultValue) {
        if (val == null) return defaultValue;
        String str = val.toString();
        if (str.equals("undefined") || str.isEmpty()) return defaultValue;
        return str;
    }

    private String convertSizeToPt(Object sizeVal) {
        if (sizeVal == null) return "12";
        String sizeStr = sizeVal.toString();
        if (sizeStr.equals("undefined") || sizeStr.isEmpty()) return "12";
        if (sizeStr.endsWith("px")) {
            sizeStr = sizeStr.substring(0, sizeStr.length() - 2);
        }
        try {
            int px = Integer.parseInt(sizeStr);
            int pt = (int) Math.round(px * 3.0 / 4.0);
            return String.valueOf(pt);
        } catch (NumberFormatException e) {
            return "12";
        }
    }

    private boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        String str = value.toString();
        return !str.isEmpty() && !str.equals("false") && !str.equals("undefined");
    }

    private void defineRGBColor(String[] colorNames, Color[] colors) {
        rgbColor = new HashMap<>();
        for (int x = 0; x < colorNames.length; x++) {
            if (colors[x] != null) {
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

        menuBar.getMenus().addAll(
                createMenu("File",   new String[]{"New", "Open", "Save", "Save As"}),
                createMenu("Edit",   new String[]{"Undo", "Redo", "Find"}),
                createMenu("Insert", new String[]{"Image", "Link", "List"}),
                createMenu("Format", new String[]{"Align Left", "Align Center", "Align Right", "Justify"}),
                createCollabMenu()
        );

        ToolBar formatToolbar = new ToolBar();

        Color[] colors = {null, Color.YELLOW, Color.LIME, Color.CYAN, Color.MAGENTA, Color.BLUE,
                Color.RED, Color.NAVY, Color.TEAL, Color.GREEN, Color.PURPLE,
                Color.MAROON, Color.GRAY, Color.SILVER, Color.BLACK, Color.WHITE};
        String[] names = {"No Color", "Yellow", "Lime", "Cyan", "Magenta", "Blue",
                "Red", "Navy", "Teal", "Green", "Purple", "Maroon", "Gray", "Silver", "Black", "White"};
        defineRGBColor(names, colors);

        formatToolbar.getItems().addAll(
                createButton("Undo", "-fx-font-size: 14;", "undo", null, null),
                createButton("Redo", "-fx-font-size: 14;", "redo", null, null),
                createSeparator(),
                bold         = createButton("B", "-fx-font-weight: bold;", "format", "bold", "user"),
                italic       = createButton("I", "-fx-font-style: italic;", "format", "italic", "user"),
                underline    = createButton("U", "-fx-underline: true;", "format", "underline", "user"),
                createSeparator(),
                subscript    = createButton("Aₓ", "-fx-font-size: 14;", "applyScript", "sub", "user"),
                superscript  = createButton("Aˣ", "-fx-font-size: 14;", "applyScript", "super", "user"),
                strikethrough = createStrikethroughButton(),
                createSeparator(),
                fontTypeCombo = createFontTypeOptions(),
                fontSizeCombo = createFontSizeOptions(),
                createSeparator(),
                createFontColorContainer(colors, names),
                createHighlightContainer(colors, names),
                createSeparator(),
                alignmentCombo = createAlignmentOptions(),
                headersCombo   = createHeadersOptions(),
                createSeparator(),
                createTranslationMenu()
        );

        toolbarContainer = new VBox(menuBar, formatToolbar);
    }

    private Menu createCollabMenu() {
        Menu menu = new Menu("Collab");

        collabStatusItem = new MenuItem("○ Offline");
        collabStatusItem.setDisable(true);

        SeparatorMenuItem sep1 = new SeparatorMenuItem();

        MenuItem openCollabItem = new MenuItem("Session settings…");
        openCollabItem.setOnAction(e -> {
            Runnable fn = voidFunctions.get("showCollabDialog");
            if (fn != null) fn.run();
        });

        SeparatorMenuItem sep2 = new SeparatorMenuItem();

        stopHostingItem = new MenuItem("Stop hosting");
        stopHostingItem.setDisable(true);
        stopHostingItem.setOnAction(e -> {
            Runnable fn = voidFunctions.get("stopHosting");
            if (fn != null) fn.run();
        });

        MenuItem disconnectItem = new MenuItem("Disconnect");
        disconnectItem.setOnAction(e -> {
            Runnable fn = voidFunctions.get("disconnectCollab");
            if (fn != null) fn.run();
        });

        menu.getItems().addAll(collabStatusItem, sep1, openCollabItem, sep2, stopHostingItem, disconnectItem);
        return menu;
    }

    public void updateCollabStatus(String status, String details) {
        Platform.runLater(() -> {
            switch (status) {
                case "hosting" -> {
                    collabStatusItem.setText("● Hosting  —  code: " + details);
                    stopHostingItem.setDisable(false);
                }
                case "connected" -> {
                    collabStatusItem.setText("● Connected  →  " + details);
                    stopHostingItem.setDisable(true);
                }
                case "offline" -> {
                    collabStatusItem.setText("○ Offline");
                    stopHostingItem.setDisable(true);
                }
            }
        });
    }

    private Separator createSeparator() {
        return new Separator(Orientation.VERTICAL);
    }

    private Menu createMenu(String menuName, String[] itemNames) {
        if (menuName.equals("Edit"))   return createEditMenu(itemNames);
        if (menuName.equals("Format")) return createFormatMenu(itemNames);
        if (menuName.equals("File"))   return createFileMenu(itemNames);
        if (menuName.equals("Insert")) return createInsertMenu(itemNames);
        return createDefaultMenu(menuName, itemNames);
    }

    private Menu createEditMenu(String[] itemNames) {
        Menu menu = new Menu("Edit");
        for (String name : itemNames) {
            MenuItem item = new MenuItem(name);
            if (name.equals("Undo")) {
                item.setOnAction(event -> {
                    Runnable fn = voidFunctions.get("undo");
                    if (fn != null) fn.run();
                });
            } else if (name.equals("Redo")) {
                item.setOnAction(event -> {
                    Runnable fn = voidFunctions.get("redo");
                    if (fn != null) fn.run();
                });
            } else if (name.equals("Find")) {
                item.setOnAction(event -> {
                    Runnable fn = voidFunctions.get("showSearch");
                    if (fn != null) fn.run();
                });
            }
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
            MenuItem item = new MenuItem(name);
            if (name.equals("New")) {
                item.setOnAction(event -> { Runnable fn = voidFunctions.get("newFile"); if (fn != null) fn.run(); });
            } else if (name.equals("Open")) {
                item.setOnAction(event -> { Runnable fn = voidFunctions.get("openFile"); if (fn != null) fn.run(); });
            } else if (name.equals("Save")) {
                item.setOnAction(event -> { Runnable fn = voidFunctions.get("save"); if (fn != null) fn.run(); });
            } else if (name.equals("Save As")) {
                item.setOnAction(event -> { Runnable fn = voidFunctions.get("saveAs"); if (fn != null) fn.run(); });
            }
            menu.getItems().add(item);
        }
        return menu;
    }

    private Menu createInsertMenu(String[] itemNames) {
        Menu menu = new Menu("Insert");
        for (String name : itemNames) {
            if (name.equals("List")) {
                Menu listSubmenu = new Menu("List");
                MenuItem bulletedList = new MenuItem("Bulleted List");
                bulletedList.setOnAction(event -> {
                    BiConsumer<String, String> fn = formats.get("insertList");
                    if (fn != null) fn.accept("bullet", "user");
                });
                MenuItem numberedList = new MenuItem("Numbered List");
                numberedList.setOnAction(event -> {
                    BiConsumer<String, String> fn = formats.get("insertList");
                    if (fn != null) fn.accept("ordered", "user");
                });
                listSubmenu.getItems().addAll(bulletedList, numberedList);
                menu.getItems().add(listSubmenu);
            } else if (name.equals("Image")) {
                MenuItem imageItem = new MenuItem("Image");
                imageItem.setOnAction(event -> {
                    BiConsumer<String, String> fn = formats.get("insertImage");
                    if (fn != null) fn.accept("", "user");
                });
                menu.getItems().add(imageItem);
            } else if (name.equals("Link")) {
                MenuItem linkItem = new MenuItem("Link");
                linkItem.setOnAction(event -> {
                    Runnable fn = voidFunctions.get("applyLink");
                    if (fn != null) fn.run();
                });
                menu.getItems().add(linkItem);
            } else {
                menu.getItems().add(new MenuItem(name));
            }
        }
        return menu;
    }

    private Menu createDefaultMenu(String menuName, String[] itemNames) {
        Menu menu = new Menu(menuName);
        for (String name : itemNames) menu.getItems().add(new MenuItem(name));
        return menu;
    }

    private ComboBox<Integer> createFontSizeOptions() {
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
                try { size = Integer.parseInt(((String) value).trim()); } catch (NumberFormatException ex) {
                    fontSizeCombo.getEditor().clear(); return;
                }
            }
            if (size != null) {
                int px = (int) Math.round(size * 4.0 / 3.0);
                skipFormatRefreshTicks = 10;
                formats.get("setFontSize").accept(px + "px", "user");
                fontSizeCombo.setPromptText(String.valueOf(size));
                Platform.runLater(() -> {
                    fontSizeCombo.getSelectionModel().clearSelection();
                    fontSizeCombo.setValue(null);
                });
            }
        });
        return fontSizeCombo;
    }

    private ComboBox<String> createFontTypeOptions() {
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
                    skipFormatRefreshTicks = 10;
                    format.accept(normalizeFontValue(value), "user");
                    fontType.setPromptText(value);
                }
            }
        });
        return fontType;
    }

    private ComboBox<String> createAlignmentOptions() {
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
                    skipFormatRefreshTicks = 10;
                    format.accept(value.toLowerCase(), "user");
                    alignment.setPromptText(value);
                }
            }
        });
        return alignment;
    }

    private ComboBox<String> createHeadersOptions() {
        ComboBox<String> headersCombo = new ComboBox<>();
        headersCombo.getItems().addAll("No Header", "Header 1", "Header 2", "Header 3", "Header 4", "Header 5", "Header 6");
        headersCombo.setPrefWidth(110);
        headersCombo.setPromptText("Headers");
        headersCombo.setOnAction(event -> {
            String selected = headersCombo.getValue();
            if (selected != null) {
                BiConsumer<String, String> fn = formats.get("setHeader");
                if (fn != null) {
                    fn.accept(selected.equals("No Header") ? "none" : selected.replace("Header ", ""), "user");
                    Platform.runLater(() -> {
                        try { headersCombo.setValue(null); } catch (Exception ex) {}
                    });
                }
            }
        });
        return headersCombo;
    }

    private MenuButton createTranslationMenu() {
        translationMenu = new MenuButton("Translation");
        translationMenu.setStyle("-fx-font-size: 14;");
        // "No Translation" = turn off, restore original native language
        // All other options are real translation targets
        String[] languages = {"No Translation", "English", "French", "German", "Greek", "Portuguese", "Spanish"};
        for (String language : languages) {
            MenuItem langItem = new MenuItem(language);
            langItem.setOnAction(event -> {
                if (language.equals("No Translation")) {
                    BiConsumer<String, String> fn = formats.get("toggleTranslation");
                    if (fn != null) {
                        fn.accept(null, "disable");
                        translationMenu.setText("Translation");
                    }
                } else {
                    String langCode = getLanguageCode(language);
                    BiConsumer<String, String> fn = formats.get("toggleTranslation");
                    if (fn != null) {
                        fn.accept(langCode, "enable");
                        translationMenu.setText("\u21C4 " + language);
                        System.out.println("✓ Changed translation to: " + language + " (" + langCode + ")");
                        BiConsumer<String, String> retranslate = formats.get("retranslate");
                        if (retranslate != null) retranslate.accept(langCode, "user");
                    }
                }
            });
            translationMenu.getItems().add(langItem);
        }
        return translationMenu;
    }

    private String getLanguageCode(String language) {
        switch (language) {
            case "English":    return "en";
            case "French":     return "fr";
            case "Spanish":    return "es";
            case "German":     return "de";
            case "Greek":      return "el";
            case "Portuguese": return "pt";
            default:           return "en";
        }
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
            button.setOnAction(event -> voidFunctions.get(functionName).run());
        } else {
            button.setOnAction(event -> {
                BiConsumer<String, String> format = formats.get(functionName);
                try {
                    Callable<Object> getFormatsCaller = returnFunctions.get("getFormats");
                    if (getFormatsCaller != null) {
                        Object formatObj = getFormatsCaller.call();
                        if (formatObj instanceof JSObject) {
                            JSObject currentFormats = (JSObject) formatObj;
                            Object currentValue = currentFormats.getMember(param1);
                            if (isTruthy(currentValue)) {
                                format.accept(param1, "user");
                                resetButtonStyle(button, param1);
                            } else {
                                format.accept(param1, param2);
                                highlightButtonStyle(button, param1);
                            }
                            return;
                        }
                    }
                } catch (Exception e) {}
                format.accept(param1, param2);
            });
        }
    }

    private void highlightButtonStyle(Button button, String formatType) {
        String baseStyle = button.getStyle();
        if (baseStyle == null) baseStyle = "";
        if (formatType.equals("strike") && button.getGraphic() instanceof Text) {
            ((Text) button.getGraphic()).setStyle("-fx-font-size: 14; -fx-fill: #0066cc;");
            button.setStyle("-fx-background-color: #AAAAAA;");
        } else {
            if (!baseStyle.contains("-fx-background-color")) {
                button.setStyle(baseStyle + "; -fx-text-fill: #0066cc; -fx-background-color: #AAAAAA;");
            }
        }
    }

    private void resetButtonStyle(Button button, String formatType) {
        if (formatType.equals("strike") && button.getGraphic() instanceof Text) {
            ((Text) button.getGraphic()).setStyle("-fx-font-size: 14;");
            button.setStyle("");
        } else {
            switch (formatType) {
                case "bold"      -> button.setStyle("-fx-font-weight: bold;");
                case "italic"    -> button.setStyle("-fx-font-style: italic;");
                case "underline" -> button.setStyle("-fx-underline: true;");
                case "sub", "super" -> button.setStyle("-fx-font-size: 14;");
                default          -> button.setStyle("");
            }
        }
    }

    private void setAllUIElementsToDefaults() {
        bold.setStyle("-fx-font-weight: bold;");
        italic.setStyle("-fx-font-style: italic;");
        underline.setStyle("-fx-underline: true;");
        strikethrough.setStyle("");
        subscript.setStyle("-fx-font-size: 14;");
        superscript.setStyle("-fx-font-size: 14;");
        if (fontColorBar  != null) fontColorBar.setFill(Color.BLACK);
        if (highlightBar  != null) highlightBar.setFill(Color.TRANSPARENT);
        if (fontTypeCombo != null) fontTypeCombo.setPromptText("Arial");
        if (fontSizeCombo != null) fontSizeCombo.setPromptText("12");
        if (alignmentCombo != null) alignmentCombo.setPromptText("Alignment");
        if (headersCombo  != null) headersCombo.setPromptText("Headers");
    }

    public Toolbar(HashMap<String, Runnable> voidFunctions,
                   HashMap<String, BiConsumer<String, String>> formats,
                   HashMap<String, Callable<Object>> returnFunctions) {
        dpi = readDPI();
        this.voidFunctions   = voidFunctions;
        this.formats         = formats;
        this.returnFunctions = returnFunctions;
        createToolbar();

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
        GridPane colorGrid = new GridPane();
        colorGrid.setHgap(5); colorGrid.setVgap(5);
        colorGrid.setStyle("-fx-padding: 10; -fx-background-color: white;");
        int index = 1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) {
                if (index < colors.length) {
                    Color color = colors[index] == null ? Color.TRANSPARENT : colors[index];
                    String colorName = names[index];
                    Rectangle colorRect = new Rectangle(30, 30);
                    colorRect.setFill(color);
                    Button colorButton = new Button();
                    colorButton.setGraphic(colorRect);
                    colorButton.setFocusTraversable(false);
                    colorButton.setStyle("-fx-padding: 2; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
                    colorButton.setTooltip(new Tooltip(colorName));
                    colorButton.setOnAction(e -> {
                        BiConsumer<String, String> format = formats.get("setFontColor");
                        if (format != null) format.accept(rgbColor.get(colorName), "user");
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
        GridPane colorGrid = new GridPane();
        colorGrid.setHgap(5); colorGrid.setVgap(5);
        colorGrid.setStyle("-fx-padding: 10; -fx-background-color: white;");
        int index = 1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) {
                if (index < colors.length) {
                    if (index == colors.length - 1) break;
                    Color color = colors[index] == null ? Color.TRANSPARENT : colors[index];
                    String colorName = names[index];
                    Rectangle colorRect = new Rectangle(30, 30);
                    colorRect.setFill(color);
                    Button colorButton = new Button();
                    colorButton.setGraphic(colorRect);
                    colorButton.setFocusTraversable(false);
                    colorButton.setStyle("-fx-padding: 2; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
                    colorButton.setTooltip(new Tooltip(colorName));
                    colorButton.setOnAction(e -> {
                        BiConsumer<String, String> format = formats.get("setHighlightColor");
                        if (format != null) format.accept(rgbColor.get(colorName), "user");
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

    private String normalizeFontValue(String value) {
        if (value == null) return "Arial";
        switch (value.trim().toLowerCase()) {
            case "arial":           return "Arial";
            case "courier new":     return "Courier New";
            case "georgia":         return "Georgia";
            case "times new roman": return "Times New Roman";
            default:                return "Arial";
        }
    }

    private String displayFontName(String fontValue) {
        if (fontValue == null) return "Arial";
        String cleaned = fontValue.trim();
        if (cleaned.isEmpty()) return "Arial";
        String display = cleaned.replace('-', ' ');
        return display.substring(0, 1).toUpperCase() + display.substring(1);
    }
}