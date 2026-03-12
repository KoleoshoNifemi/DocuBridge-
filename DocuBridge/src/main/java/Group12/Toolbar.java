package Group12;

import javafx.stage.Screen;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class Toolbar {

    private double dpi;
    private VBox toolbarContainer;

    private double readDPI() {
        return Screen.getPrimary().getDpi();
    }

    private void createToolbar() {
        MenuBar menuBar = new MenuBar();

        // Add all the menus
        menuBar.getMenus().addAll(
                createMenu("File", new String[] {"New", "Open", "Save", "Save As"}),
                createMenu("Edit", new String[]{"Undo", "Redo", "Find"}),
                createMenu("Insert", new String[]{"Image", "Table"}),
                createMenu("Format", new String[]{"Align Left", "Align Center", "Align Right"})
        );

        toolbarContainer = new VBox(menuBar);
    }

    // Create Menu with items
    private Menu createMenu(String menuName, String[] itemNames) {
        Menu menu = new Menu(menuName);
        for (String name : itemNames) {
            menu.getItems().add(new MenuItem(name));
        }
        return menu;
    }

    public Toolbar() {
        dpi = readDPI();
        createToolbar();
    }

    public VBox getView() {
        return toolbarContainer;
    }
}
