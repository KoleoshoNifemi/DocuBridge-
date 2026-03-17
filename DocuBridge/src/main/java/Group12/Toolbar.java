package Group12;

import javafx.stage.Screen;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.geometry.Orientation;
import javafx.scene.control.Separator;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

public class Toolbar {

    private double dpi;
    private VBox toolbarContainer;
    private HashMap<String, Runnable> voidFunctions;
    private HashMap<String, BiConsumer<String, String>> formats;
    private HashMap<String, Callable<Object>> returnFunctions;
    private Callable<Object> getTextProperties;
    private Button bold;
    private Button italic;
    private Button underline;
    private Button strikethrough;
    private Button  subscript;
    private Button superscript;




    private double readDPI() {
        return Screen.getPrimary().getDpi();
    }

    private void displayTextProperties(){

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

        // alignment list
        ComboBox<String> alignCombo = new ComboBox<>();
        alignCombo.getItems().addAll("Align Left", "Align Center", "Align Right");
        alignCombo.setPrefWidth(110);
        alignCombo.setPromptText("Alignment");

        // Font List
        ComboBox<String> font = new ComboBox<>();
        font.getItems().addAll("Arial", "Courier New", "Georgia", "Times New Roman");

                font.setPrefWidth(100);
        font.setPromptText("Font");

        // Color data
        Color[] colors = {null, Color.YELLOW, Color.LIME, Color.CYAN, Color.MAGENTA, Color.BLUE,
                Color.RED, Color.NAVY, Color.TEAL, Color.GREEN, Color.PURPLE,
                Color.MAROON, Color.GRAY, Color.SILVER, Color.BLACK};
        String[] names = {"No Color", "Yellow", "Lime", "Cyan", "Magenta", "Blue",
                "Red", "Navy", "Teal", "Green", "Purple", "Maroon", "Gray", "Silver", "Black"};

        // ToolBar buttons
        formatToolbar.getItems().addAll(
                createButton("Undo", "-fx-font-size: 14;", "undo", null, null),
                createButton("Redo", "-fx-font-size: 14;", "redo", null, null),
                bold = (createButton("B", "-fx-font-weight: bold;", "format", "bold", "user")),
                italic = (createButton("I", "-fx-font-style: italic;", "format", "italic", "user")),
                underline = (createButton("U", "-fx-underline: true;", "format", "underline", "user")),
                subscript = (createButton("Aₓ", "-fx-font-size: 14;", "applyScript", "sub", "user")),
                superscript = (createButton("Aˣ", "-fx-font-size: 14;", "applyScript", "super", "user")),
                createSeparator(),
                font,
                createFontSizeOptions(),
                alignCombo,
                createColorMenu("Highlight", colors, names, true),
                createColorMenu("Font Color", colors, names, false),
                createSeparator()
        );

        toolbarContainer = new VBox(menuBar, formatToolbar);
    }

    // create the color menu grid
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

    // creates the color buttons
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

    private ComboBox<Integer> createFontSizeOptions(){
        ComboBox<Integer> fontSizeCombo = new ComboBox<>();
        fontSizeCombo.getItems().addAll(8, 9, 10, 11, 12, 14, 16, 18, 24, 30, 36, 48, 60, 72);
        fontSizeCombo.setEditable(true);
        fontSizeCombo.setPrefWidth(92);
        fontSizeCombo.setPromptText("Font Size");

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
                int px = (int) Math.round(size * 1.333);
                fontSizeCombo.setValue(size); // normalize back to Integer
                formats.get("setFontSize").accept(px + "px", "user");
            }
        });
        return fontSizeCombo;
    }

    // Create Button
    private Button createButton(String text, String style, String functionName, String param1, String param2) {
        Button button = new Button(text);
        button.setStyle(style);
        setButtonActions(button, functionName, param1, param2);
        return button;
    }

    private void setButtonActions(Button button, String functionName, String param1, String param2) {
        if (voidFunctions.containsKey(functionName)) {
            button.setOnAction(event -> {
                Runnable function = voidFunctions.get(functionName);
                function.run();
            });
        }else{
            button.setOnAction(event -> {
                BiConsumer<String, String> format = formats.get(functionName);
                format.accept(param1, param2);
            });
        }
    }

    public Toolbar(HashMap<String, Runnable> voidFunctions,
                   HashMap<String, BiConsumer<String, String>> formats) {
        dpi = readDPI();
        this.voidFunctions = voidFunctions;
        this.formats = formats;
        this.returnFunctions = returnFunctions;
        getTextProperties = returnFunctions.get("getFormatProperties");
        createToolbar();
    }

    public VBox getView() {
        return toolbarContainer;
    }
}