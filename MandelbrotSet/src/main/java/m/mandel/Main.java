package m.mandel;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CyclicBarrier;

public class Main extends Application {

    private static double DEFAULT_SCREEN_WIDTH = 800;
    private static double DEFAULT_SCREEN_HEIGHT = 600;

    // Fractal image
    private Canvas fractalCanvas;
    private GraphicsContext graphicsContext;

    // Number of max iterations
    private final int maxIter = 2000;
//    Parallel mode: In this mode we divide the computational work of the previous method in a new method among a specified number of threads, which take care of equal  part of the fractal computation and return the results on the canvas as soon as all of them are done. Then the canvas updates the image.
//    Distributive mode: Same as parallel but instead of threads we are using MPI to distribute the work among different nodes in a cluster. The results are then sent back to the main node and the canvas updates the image.
    // Zoom factor
    private double zoom = 1.0;
    private final double zoomFactor = 0.9;

    // Coordinates
    private double xPos = -0.5;
    private double yPos = 0;

    // Flag to check if a computation is ongoing
    private boolean isComputing = false;

    private CyclicBarrier barrier;

    private TextField widthInput;
    private TextField heightInput;

    private int cores = Runtime.getRuntime().availableProcessors();
    private WritableImage writableImage;
    private PixelWriter pixelWriter;

    private String mode;

    public static void main(String[] args) {
        launch(args);
    }

//    USTE DISTRIBUTIVEN DEL I TO E TO

    @Override
    public void start(Stage primaryStage) {

        Parameters parameters = getParameters();

        // Retrieve width and height parameters
        String widthParam = parameters.getNamed().get("width");
        String heightParam = parameters.getNamed().get("height");
        mode = parameters.getNamed().get("mode");

        System.out.println(widthParam + " " + heightParam + " " + mode);

        // Set the title of the application window
        primaryStage.setTitle("Mandelbrot Set");

        // Add a close request handler to the primary stage
        primaryStage.setOnCloseRequest(event -> {
            // Exit the application
            System.exit(0);
        });

        // Set the icon of the application window
        Image icon = new Image("mbs.png");
        primaryStage.getIcons().add(icon);

        // Initialize the fractal canvas and graphics context
        DEFAULT_SCREEN_WIDTH = (widthParam != null) ? Integer.parseInt(widthParam) : DEFAULT_SCREEN_WIDTH;
        DEFAULT_SCREEN_HEIGHT = (heightParam != null) ? Integer.parseInt(heightParam) : DEFAULT_SCREEN_HEIGHT;

        // Create the fractal canvas
        this.fractalCanvas = new Canvas(DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_HEIGHT);
        this.graphicsContext = fractalCanvas.getGraphicsContext2D();

        // Initialize text fields
        widthInput = new TextField();
        heightInput = new TextField();

        // Create the input box with the text fields and the save button
        HBox inputBox = gethBox();

        // Create the root pane with the fractal canvas and input box
        StackPane root = new StackPane(fractalCanvas, inputBox);

        // Create the scene with the root pane and set dimensions
        Scene scene = new Scene(root, DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);

        // Add key event listener for user interaction
        //scene.setOnKeyPressed(this::handleKeyPress);
        scene.setOnKeyReleased(this::handleKeyPress);

        // Set focus on the fractal canvas
        fractalCanvas.setFocusTraversable(true);
        fractalCanvas.requestFocus();

        // Add key event listeners
//        fractalCanvas.setOnKeyPressed(this::handleKeyPress);

        // Show the stage
        switch (mode) {
            case "sequential" -> computeSequential();
            case "parallel" -> computeParallel();
        }
        primaryStage.show();
    }

    // tocna implementacija na barrierata

    //    da go paraleliziram kodo i izbrisam netocnio
//    da probam samo so executor service
//da go napram univerzalni zoom za site modoj i presmetki so pritisok na kopcinata


    private void computeSequential() {
        long t0 = System.currentTimeMillis();
//        graphicsContext.clearRect(0, 0, fractalCanvas.getWidth(), fractalCanvas.getHeight());

        double scale = Math.min(DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_HEIGHT) / 3.0 * zoom;


        for (int y = 0; y < DEFAULT_SCREEN_HEIGHT; y++) {
            double cY = yPos + (y - DEFAULT_SCREEN_HEIGHT / 2.0) / scale;
            for (int x = 0; x < DEFAULT_SCREEN_WIDTH; x++) {
                double cX = xPos + (x - DEFAULT_SCREEN_WIDTH / 2.0) / scale;
                if (isInMainCardioid(cX, cY) || isInPeriod2Bulb(cX, cY)) {
                    graphicsContext.getPixelWriter().setArgb(x, y, 0xFF000000);
                } else {
                    int iter = computePixel(cX, cY);
                    int pixelColor = calculateColor(iter);
                    graphicsContext.getPixelWriter().setArgb(x, y, pixelColor);
                }
            }
        }
        // Output computation time
        System.out.println("Sequential time: " + (System.currentTimeMillis() - t0) + "ms");
        isComputing = false;
    }

    // Method to check if a point is inside the main cardioid of the Mandelbrot set
    // cardioid - a plane curve generated by a point on the circumference of a circle as it rolls on the outside of a fixed circle
    private boolean isInMainCardioid(double cX, double cY) {
        double q = (cX - 0.25) * (cX - 0.25) + cY * cY;
        return q * (q + (cX - 0.25)) < 0.25 * cY * cY;
    }

    // Method to check if a point is inside the period-2 bulb of the Mandelbrot set
    // period-2 bulb - a bulb-like shape in the Mandelbrot set
    private boolean isInPeriod2Bulb(double cX, double cY) {
        return (cX + 1) * (cX + 1) + cY * cY < 0.0625;
    }

    // Method to calculate the color based on the iteration count
    private int calculateColor(int iter) {
        if (iter == maxIter) {
            return 0xFF000000; // Black for points inside the Mandelbrot set
        } else {
            // Generate a color based on the iteration count
            int r = iter % 256;
            int g = (iter * 3) % 256;
            int b = (iter * 5) % 256;
            return (255 << 24) | (r << 16) | (g << 8) | b;
        }
    }

    // Method to compute the Mandelbrot set iteration for a given point
    // Here cX and cY are the coordinates of the point in the complex plane
    // and the main algorithm assigns a color to the point based on escape time
    private int computePixel(double cX, double cY) {
        double zy;
        double zx = zy = 0;

        int iter;
        for (iter = 0; iter < maxIter && zx * zx + zy * zy < 4; iter++) {
            double tmp = zx * zx - zy * zy + cX;
            zy = 2.0 * zx * zy + cY;
            zx = tmp;
        }

        return iter;
    }

    private void computeParallel() {

        long t0 = System.currentTimeMillis();

        this.barrier = new CyclicBarrier(cores, () -> {
//            Platform.runLater(() -> graphicsContext.drawImage(writableImage, 0, 0));
//            System.out.println(Thread.currentThread().getName() + " did the computation: " + (System.currentTimeMillis() - t0) + "ms");
            // Update the fractal canvas with the computed image from every thread
            isComputing = false;
        });

        this.writableImage = new WritableImage((int) DEFAULT_SCREEN_WIDTH, (int) DEFAULT_SCREEN_HEIGHT);
        this.pixelWriter = writableImage.getPixelWriter();

        int chunkSize = (int) DEFAULT_SCREEN_HEIGHT / cores;

        for (int i = 0; i < cores; i++) {
            int startRow = i * chunkSize;
            int endRow = (i == cores - 1) ? (int) DEFAULT_SCREEN_HEIGHT : startRow + chunkSize;
            new Thread(new MTask(i,
                    startRow,
                    endRow,
                    barrier,
                    (int) DEFAULT_SCREEN_HEIGHT,
                    (int) DEFAULT_SCREEN_WIDTH,
                    cores,
                    xPos,
                    yPos,
                    zoom,
                    maxIter,
                    writableImage,
                    pixelWriter,
                    graphicsContext)).start();
        }

        // Wait for all tasks to finish
        try {
            barrier.await();
            System.out.println("All threads have reached the barrier. Barrier action executed.");
        } catch (Exception e) {
            e.printStackTrace();
        }
//SINHRONIZIIRAJ GO ISPISO VEKJE EDNAS I POCNI DISTRIBUTIVNO
//                ZABRZAJ GO I OPTIMIZIRAJ GO
//                PRIKAZI NA 2D GRAFIK REZULTATO
        // Output computation time
        System.out.println("Parallel time: " + (System.currentTimeMillis() - t0) + "ms ");
        isComputing = false;
    }

    // sporedi gi dvete crtanja i napraj go koe e podobro i pobrzo i uste eden parametar dali da e sekvencijaln, paralelen ili distribuirano
//    how could I implement caching in the computation so it can calculate it faster and using old calculation data
//    caching for zooming also
    private HBox gethBox() {
        Button saveButton = new Button("Save as PNG");
        saveButton.setOnAction(e -> saveImage());

        Label labelWidth = new Label("Width:");
        Label labelHeight = new Label("Height:");

        // Set the text color of the labels to white
        labelWidth.setStyle("-fx-text-fill: white;");
        labelHeight.setStyle("-fx-text-fill: white;");

        HBox inputBox = new HBox(labelWidth, widthInput, labelHeight, heightInput, saveButton);

        // Set spacing and alignment for the input box
        inputBox.setSpacing(15);
        inputBox.setAlignment(Pos.BOTTOM_CENTER);
        return inputBox;
    }

    private void saveImage() {
        // Retrieve user-specified width and height
        int width = Integer.parseInt(widthInput.getText());
        int height = Integer.parseInt(heightInput.getText());

        // Update canvas size
        fractalCanvas.setWidth(width);
        fractalCanvas.setHeight(height);

        // Save the fractal as PNG with the specified width and height
        saveFractalAsPNG(Integer.parseInt(widthInput.getText()), height);

        // Reset canvas size to default and recompute the fractal
        fractalCanvas.setWidth(DEFAULT_SCREEN_WIDTH);
        fractalCanvas.setHeight(DEFAULT_SCREEN_HEIGHT);
        computeParallel();
    }

    void saveFractalAsPNG(int width, int height) {
        FileChooser fileChooser = new FileChooser();
        // save as JPEG or PNG
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Picture", "*.png", "*.jpeg")); // save as JPEG or PNG
        File file = fileChooser.showSaveDialog(null);

        if (file != null) {
            try {
                WritableImage writableImage = new WritableImage(width, height);
                fractalCanvas.snapshot(null, writableImage);

                BufferedImage bufferedImage = SwingFXUtils.fromFXImage(writableImage, null);
                ImageIO.write(bufferedImage, "png", file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleKeyPress(KeyEvent event) {
        // Early return if a computation is already ongoing+
        // keyevent to return something when released or pressed

        if (event.getEventType().equals(KeyEvent.KEY_PRESSED)) {
            System.out.println("Pressed: " + event.getText());
        }
        if (event.getEventType().equals(KeyEvent.KEY_RELEASED)) {
            System.out.println("Released: " + event.getText());
        }

        System.out.println("Pocetok: " + isComputing);
        if (isComputing) return;

        // Zoom in or out
        if (event.getText().equals("+")) {
            zoom /= zoomFactor;
        } else if (event.getText().equals("-")) {
            // Ensure zooming out does not exceed initial zoom level
            if (zoom > zoomFactor) {
                zoom *= zoomFactor;
            }
        } else if (event.getCode() == KeyCode.UP) {
            yPos -= 0.1 / zoom;
        } else if (event.getCode() == KeyCode.DOWN) {
            yPos += 0.1 / zoom;
        } else if (event.getCode() == KeyCode.LEFT) {
            xPos -= 0.1 / zoom;
        } else if (event.getCode() == KeyCode.RIGHT) {
            xPos += 0.1 / zoom;
        } else return;

        System.out.println("Sredina: " + isComputing);
        // Only start the timer if a computation is not already in progress, which in this case is false
        isComputing = true;
        System.out.println("Sredina2: " + isComputing);

        switch (mode) {
            case "sequential" -> computeSequential();
            case "parallel" -> computeParallel();
        }

        System.out.println("Kraj: " + isComputing);
    }
}