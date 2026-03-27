package Group12;

//plain wrapper so jpackage can launch a JavaFX app from a JAR without hitting the
//"Application class must extend Application" restriction on the main-class manifest entry
public class Launcher {
    public static void main(String[] args) {
        Main.main(args);
    }
}
