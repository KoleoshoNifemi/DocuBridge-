package Group12;

import javafx.stage.Screen;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.geometry.Orientation;
import javafx.scene.control.Separator;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class Toolbar {

    private double dpi;
    private VBox toolbarContainer;

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

        // font sizes list
        ComboBox<Double> fontSizeCombo = new ComboBox<>();
        fontSizeCombo.getItems().addAll(8.0, 9.0, 10.0, 11.0, 12.0, 14.0, 18.0, 24.0, 30.0, 36.0, 48.0, 60.0, 72.0, 96.0);
        fontSizeCombo.setEditable(true);
        fontSizeCombo.setPrefWidth(92);
        fontSizeCombo.setPromptText("Font Size");

        // alignment list
        ComboBox<String> alignCombo = new ComboBox<>();
        alignCombo.getItems().addAll("Align Left", "Align Center", "Align Right");
        alignCombo.setPrefWidth(110);
        alignCombo.setPromptText("Alignment");

        // Font List
        ComboBox<String> font = new ComboBox<>();
        font.getItems().addAll("Calibri", "Segoe UI", "Arial", "Helvetica");
        font.setPrefWidth(80);
        font.setPromptText("Font");

        // Color data
        Color[] colors = {null, Color.YELLOW, Color.LIME, Color.CYAN, Color.MAGENTA, Color.BLUE,
                Color.RED, Color.NAVY, Color.TEAL, Color.GREEN, Color.PURPLE,
                Color.MAROON, Color.GRAY, Color.SILVER, Color.BLACK};
        String[] names = {"No Color", "Yellow", "Lime", "Cyan", "Magenta", "Blue",
                "Red", "Navy", "Teal", "Green", "Purple", "Maroon", "Gray", "Silver", "Black"};

        // ToolBar buttons
        formatToolbar.getItems().addAll(
                createButton("Undo", "-fx-font-size: 14;"),
                createButton("Redo", "-fx-font-size: 14;"),
                createButton("B", "-fx-font-weight: bold;"),
                createButton("I", "-fx-font-style: italic;"),
                createButton("U", "-fx-underline: true;"),
                createButton("Aₓ", "-fx-font-size: 14;"),
                createButton("Aˣ", "-fx-font-size: 14;"),
                createSeparator(),
                font,
                fontSizeCombo,
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

    // Create Button
    private Button createButton(String text, String style) {
        Button button = new Button(text);
        button.setStyle(style);
        return button;
    }

    public Toolbar() {
        dpi = readDPI();
        createToolbar();
    }

    public VBox getView() {
        return toolbarContainer;
    }
}