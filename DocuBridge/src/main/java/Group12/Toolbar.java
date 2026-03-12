package Group12;

import javafx.stage.Screen;

public class Toolbar {
    private double dpi;

    private double readDPI(){
        Screen s = Screen.getPrimary();
        return (s.getDpi());
    }
    public Toolbar() {
        dpi =  readDPI();
        //fill code here
    }
}

/*  Look into JavaFX MenuBar & ButtonBar
    MenuBar for the things like File, Edit, Insert, Format, etc
    ButtonBar for the buttons like bold, italics, etc
 */