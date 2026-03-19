package Group12;

import javafx.application.Platform;
import javafx.stage.Screen;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.geometry.Orientation;
import javafx.scene.control.Separator;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.HashMap;
import java.util.function.BiConsumer;

public class Toolbar {

    private double dpi;
    private VBox toolbarContainer;
    private HashMap<String, Runnable> clipBoard;
    private HashMap<String, BiConsumer<String, String>> formats;
    private Button undo;
    private Button redo;
    private Button bold;
    private Button italic;
    private Button underline;



    private double readDPI() {
        return Screen.getPrimary().getDpi();
    }

    private void createToolbar() {
        MenuBar menuBar = new MenuBar();

        // MenuBar items
        menuBar.getMenus().addAll(
                createMenu("File", new String[] {"New", "Open", "Save", "Save As"}),
                createMenu("Edit", new String[]{"Undo", "Redo", "Find"}),
                createMenu("Insert", new String[]{"Image", "Table", "Link"}),
                createMenu("Format", new String[]{"Align Left", "Align Center", "Align Right"})
        );

        ToolBar formatToolbar = new ToolBar();

        // Font list
        ComboBox<String> font = new ComboBox<>();
        font.getItems().addAll("Calibri", "Segoe UI", "Arial", "Helvetica");
        font.setPrefWidth(100);
        font.setPromptText("Font");

        // Font sizes list
        ComboBox<Integer> fontSizeCombo = new ComboBox<>();
        fontSizeCombo.getItems().addAll(8, 9, 10, 11, 12, 14, 16, 18, 24, 30, 36, 48, 60, 72, 96);
        fontSizeCombo.setEditable(true);
        fontSizeCombo.setPrefWidth(92);
        fontSizeCombo.setPromptText("Font Size");

        // Color data
        Color[] colors = {null, Color.YELLOW, Color.LIME, Color.CYAN, Color.MAGENTA, Color.BLUE,
                Color.RED, Color.NAVY, Color.TEAL, Color.GREEN, Color.PURPLE,
                Color.MAROON, Color.GRAY, Color.SILVER, Color.BLACK};
        String[] names = {"No Color", "Yellow", "Lime", "Cyan", "Magenta", "Blue",
                "Red", "Navy", "Teal", "Green", "Purple", "Maroon", "Gray", "Silver", "Black"};

        // Alignment dropdown
        ComboBox<String> alignCombo = new ComboBox<>();
        alignCombo.getItems().addAll("Align Left", "Align Center", "Align Right");
        alignCombo.setPrefWidth(110);
        alignCombo.setPromptText("Alignment");

        // Headers dropdown
        ComboBox<String> headersCombo = new ComboBox<>();
        headersCombo.getItems().addAll("No Header", "Header 1", "Header 2", "Header 3", "Header 4", "Header 5", "Header 6");
        headersCombo.setPrefWidth(110);
        headersCombo.setPromptText("Headers");
        headersCombo.setOnAction(event -> {
            String selected = headersCombo.getValue();
            if (selected != null) {
                BiConsumer<String, String> formatFn = formats.get("format");
                if (formatFn != null) {
                    if (selected.equals("No Header")) {
                        formatFn.accept("header", "false");
                    } else {
                        // Extract the level number from the selected string (e.g. "Header 1" -> "1")
                        String level = selected.replace("Header ", "");
                        formatFn.accept("header", level);
                    }
                }
            }
        });

        // Translation dropdown
        MenuButton translationMenu = createTranslationMenu();

        // ToolBar buttons in the requested order:
        // Undo, Redo, Bold, Italic, Subscript, Superscript, Strikethrough,
        // Font, Font Size, Font Color, Highlight, Alignment, Headers, Translation
        formatToolbar.getItems().addAll(
                createButton("Undo",  "-fx-font-size: 14;",      "undo",        null,          null),
                createButton("Redo",  "-fx-font-size: 14;",      "redo",        null,          null),
                createSeparator(),
                createButton("B",     "-fx-font-weight: bold;",  "format",      "bold",        "user"),
                createButton("I",     "-fx-font-style: italic;", "format",      "italic",      "user"),
                createButton("U",     "-fx-underline: true;",    "format",      "underline",   "user"),
                createSeparator(),
                createButton("Aₓ",   "-fx-font-size: 14;",      "applyScript", "sub",         "user"),
                createButton("Aˣ",   "-fx-font-size: 14;",      "applyScript", "super",       "user"),
                createButton("S\u0336", "-fx-font-size: 14;",   "format",      "strike",      "user"),
                createSeparator(),
                font,
                fontSizeCombo,
                createSeparator(),
                createColorMenu("Font Color", colors, names, false),
                createColorMenu("Highlight",  colors, names, true),
                createSeparator(),
                alignCombo,
                headersCombo,
                createSeparator(),
                translationMenu
        );

        toolbarContainer = new VBox(menuBar, formatToolbar);
    }

    // Creates the translation menu with language options
    private MenuButton createTranslationMenu() {
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
                // "No Translation" clears any active translation; other options trigger translate
                if (language.equals("No Translation")) {
                    // No-op or hook into a clearTranslation handler if added later
                    System.out.println("Translation cleared.");
                } else {
                    // Hook into a translation handler passed via formats map if available
                    BiConsumer<String, String> translateFn = formats.get("translate");
                    if (translateFn != null) {
                        translateFn.accept(language, "user");
                    } else {
                        System.out.println("Translate to: " + language);
                    }
                }
            });
            translationMenu.getItems().add(langItem);
        }

        return translationMenu;
    }

    // Create the color menu grid
    private MenuButton createColorMenu(String menuName, Color[] colors, String[] names, boolean isHighlight) {
        MenuButton colorMenu = new MenuButton(menuName);
        colorMenu.setStyle("-fx-font-size: 14;");

        GridPane colorGrid = new GridPane();
        colorGrid.setHgap(5);
        colorGrid.setVgap(5);
        colorGrid.setStyle("-fx-padding: 10; -fx-background-color: white;");

        int startIndex = isHighlight ? 0 : 1;  // Skip first (transparent) for font colors
        int index = startIndex;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) {
                if (index < colors.length) {
                    Color color = colors[index] == null ? Color.TRANSPARENT : colors[index];
                    String colorName = names[index];

                    Button colorButton = createColorButton(color, colorName);
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

    // Creates the color buttons
    private Button createColorButton(Color color, String colorName) {
        Rectangle colorRect = new Rectangle(30, 30);
        colorRect.setFill(color);

        Button colorButton = new Button();
        colorButton.setGraphic(colorRect);
        colorButton.setFocusTraversable(false);
        colorButton.setStyle("-fx-padding: 2; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        colorButton.setTooltip(new Tooltip(colorName));

        return colorButton;
    }

    private Separator createSeparator() {
        return new Separator(Orientation.VERTICAL);
    }

    // Create Menu with items
    private Menu createMenu(String menuName, String[] itemNames) {
        Menu menu = new Menu(menuName);
        for (String name : itemNames) {
            menu.getItems().add(new MenuItem(name));
        }
        return menu;
    }

    // Create Button
    private Button createButton(String text, String style, String functionName, String param1, String param2) {
        Button button = new Button(text);
        button.setStyle(style);
        setButtonActions(button, functionName, param1, param2);
        return button;
    }

    private void setButtonActions(Button button, String functionName, String param1, String param2) {
        if (clipBoard.containsKey(functionName)) {
            button.setOnAction(event -> {
                Runnable function = clipBoard.get(functionName);
                function.run();
            });
        } else {
            button.setOnAction(event -> {
                BiConsumer<String, String> format = formats.get(functionName);
                format.accept(param1, param2);
            });
        }
    }

    public Toolbar(HashMap<String, Runnable> clipBoard, HashMap<String, BiConsumer<String, String>> formats) {
        dpi = readDPI();
        this.clipBoard = clipBoard;
        this.formats = formats;
        createToolbar();
    }

    public VBox getView() {
        return toolbarContainer;
    }
}