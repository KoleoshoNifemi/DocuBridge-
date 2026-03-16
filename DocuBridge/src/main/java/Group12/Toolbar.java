package Group12;

import javafx.stage.Screen;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.geometry.Orientation;

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
                createMenu("Insert", new String[]{"Image", "Table"}),
                createMenu("Format", new String[]{"Align Left", "Align Center", "Align Right"})
        );

        ToolBar formatToolbar = new ToolBar();

        Separator separator = new Separator(Orientation.VERTICAL);

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

        // ToolBar buttons
        formatToolbar.getItems().addAll(
                createButton("Undo", "-fx-font-size: 14;"),
                createButton("Redo", "-fx-font-size: 14;"),
                createButton("B", "-fx-font-weight: bold;"),
                createButton("I", "-fx-font-style: italic;"),
                createButton("U", "-fx-underline: true;"),
                separator,
                fontSizeCombo,
                alignCombo
        );

        toolbarContainer = new VBox(menuBar, formatToolbar);
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