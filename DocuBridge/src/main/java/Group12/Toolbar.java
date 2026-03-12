package Group12;

import javafx.stage.Screen;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class Toolbar {

    private double dpi;
    private VBox toolbarContainer;

    private double readDPI(){
        Screen s = Screen.getPrimary();
        return s.getDpi();
    }

    public Toolbar() {
        dpi = readDPI();
        createToolbar();
    }

    private void createToolbar(){

        MenuBar menuBar = new MenuBar();

        // File Menu
        Menu file = new Menu("File");
        file.getItems().addAll(
                new MenuItem("New"),
                new MenuItem("Open"),
                new MenuItem("Save"),
                new MenuItem("Save As")
        );

        // Edit Menu
        Menu edit = new Menu("Edit");
        edit.getItems().addAll(
                new MenuItem("Undo"),
                new MenuItem("Redo"),
                new MenuItem("Find")
        );

        // Insert Menu
        Menu insert = new Menu("Insert");
        insert.getItems().addAll(
                new MenuItem("Image"),
                new MenuItem("Table")
        );

        // Format menu
        Menu format = new Menu("Format");

        MenuItem alignLeft = new MenuItem("Align Left");
        MenuItem alignCenter = new MenuItem("Align Center");
        MenuItem alignRight = new MenuItem("Align Right");

        format.getItems().addAll(alignLeft, alignCenter, alignRight);

        // add menus to toolbar
        menuBar.getMenus().addAll(file, edit, insert, format);

        toolbarContainer = new VBox(menuBar);
    }

    public VBox getView(){
        return toolbarContainer;
    }
}