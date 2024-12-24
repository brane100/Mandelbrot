module m.mandel {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires javafx.swing;


    opens m.mandel to javafx.fxml;
    exports m.mandel;
}