package Group12;

import javafx.stage.Screen;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class Toolbar {

    private double dpi;
    private VBox toolbarContainer;

    public Toolbar() {
        dpi = readDPI();
        createToolbar();
    }

    private double readDPI() {
        return Screen.getPrimary().getDpi();
    }

    private void createToolbar() {
        MenuBar menuBar = new MenuBar();

        // add all the menus
        menuBar.getMenus().addAll(
                createMenu("File", "New", "Open", "Save", "Save As"),
                createMenu("Edit", "Undo", "Redo", "Find"),
                createMenu("Insert", "Image", "Table"),
                createMenu("Format", "Align Left", "Align Center", "Align Right")
        );

        toolbarContainer = new VBox(menuBar);
    }

    // create Menu with items
    private Menu createMenu(String menuName, String... itemNames) {
        Menu menu = new Menu(menuName);
        for (String name : itemNames) {
            menu.getItems().add(new MenuItem(name));
        }
        return menu;
    }

    public VBox getView() {
        return toolbarContainer;
    }
}