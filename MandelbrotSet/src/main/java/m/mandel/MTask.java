package m.mandel;

import javafx.application.Platform;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.BrokenBarrierException;

public class MTask implements Runnable {
    private final int threadIndex;
    private final CyclicBarrier barrier;
    private final int WIDTH, HEIGHT;
    private final int cores;
    private final double X_POS, Y_POS, ZOOM;
    private final int MAX_ITER;
    private final WritableImage writableImage;
    private final PixelWriter pixelWriter;
    private final int startRow, endRow;
    private GraphicsContext graphicsContext;

    public MTask(int threadIndex, int startRow, int endRow, CyclicBarrier barrier, int defaultHeight, int defaultWidth, int cores, double X_POS, double Y_POS, double ZOOM, int MAX_ITER, WritableImage writableImage, PixelWriter pixelWriter, GraphicsContext gc) {
        this.threadIndex = threadIndex;
        this.startRow = startRow;
        this.endRow = endRow;
        this.barrier = barrier;
        this.WIDTH = defaultWidth;
        this.HEIGHT = defaultHeight;
        this.cores = cores;
        this.X_POS = X_POS;
        this.Y_POS = Y_POS;
        this.ZOOM = ZOOM;
        this.MAX_ITER = MAX_ITER;
        this.writableImage = writableImage;
        this.pixelWriter = pixelWriter;
        this.graphicsContext = gc;
    }

    @Override
    public void run() {

        double scale = Math.min(WIDTH, HEIGHT) / 3.0 * ZOOM;

        for (int y = startRow; y < endRow; y++) {
            double cY = (Y_POS + (y - HEIGHT / 2.0) / scale);
            for (int x = 0; x < WIDTH; x++) {
                double cX = (X_POS + (x - WIDTH / 2.0) / scale);

                int pixelColor;
                if (isInMainCardioid(cX, cY) || isInPeriod2Bulb(cX, cY)) {
                    pixelColor = 0xFF000000;
                } else {
                    double iter = computePixel(cX, cY);
                    pixelColor = calculateColor(iter);
                }
                pixelWriter.setArgb(x, y, pixelColor);
//                    kako da go zabrzam kodo bez thread overhead da mi bidi pobrz od sekvencijalnio
//                        kako da go napravam so sekoj thread da go izcrtuva svojot del od mandelbrot bez screen tearing
            }
        }

        try {
            barrier.await();
            Platform.runLater(() -> graphicsContext.drawImage(writableImage, 0, 0));
        } catch (InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }
    }
//vidi zosto gresno vadi na ekran, oti site deloj od ekrano ko presmetki ne se prikazani
//    @Override
//    public void run() {
//        for (int y = startRow; y < endRow; y++) {
//            double cY = Y_POS + (y - (HEIGHT / 2.0)) * ZOOM;
//            for (int x = 0; x < WIDTH; x++) {
//                double cX = X_POS + (x - (WIDTH / 2.0)) * ZOOM;
//
//                if (isInMainCardioid(cX, cY) || isInPeriod2Bulb(cX, cY)) {
//                    synchronized (pixelWriter) {
//                        pixelWriter.setArgb(x, y, 0xFF000000);
//                    }
//                } else {
//                    int iter = computePixel(cX, cY);
//                    int pixelColor = calculateColor(iter);
//                    synchronized (pixelWriter) {
//                        pixelWriter.setArgb(x, y, pixelColor);
////                        DOVRSI GO TAKA SO SEKOJ THREAD DA GO IZCRTA SVOJO DEL OD MANDELBROTO
//                    }
//                }
//            }
//        }
//
//        try {
//            barrier.await();
//        } catch (InterruptedException | BrokenBarrierException e) {
//            e.printStackTrace();
//        }
//
//        Platform.runLater(() -> {
//            synchronized (writableImage) {
//                writableImage.notify();
//            }
//        });
//
//        // make current thread update its computation part on the canvas
//        // this is necessary because the last thread to finish will not update the canvas
//        // if we don't do this
//    }

    private int computePixel(double cX, double cY) {
        double zx = 0, zy = 0;
        int iter;
        for (iter = 0; iter < MAX_ITER && zx * zx + zy * zy < 4; iter++) {
            double tmp = zx * zx - zy * zy + cX;
            zy = 2.0 * zx * zy + cY;
            zx = tmp;
        }
        return iter;
    }

    /*private double computePixel(double cX, double cY) {
        double x = 0, y = 0;
        double iteration = 0;
        int maxIteration = 1000;
        double bailout = 1 << 16;

        while (x * x + y * y <= bailout && iteration < maxIteration) {
            double xtemp = x * x - y * y + cX;
            y = 2 * x * y + cY;
            x = xtemp;
            iteration++;
        }

        if (iteration < maxIteration) {
            double log_zn = Math.log(x * x + y * y) / 2;
            double nu = Math.log(log_zn / Math.log(2)) / Math.log(2);
            iteration = iteration + 1 - nu;
        }

        return iteration;
    }*/

    private boolean isInPeriod2Bulb(double cX, double cY) {
        return (cX + 1) * (cX + 1) + cY * cY < 0.0625;
    }

    private boolean isInMainCardioid(double cX, double cY) {
        double q = (cX - 0.25) * (cX - 0.25) + cY * cY;
        return q * (q + (cX - 0.25)) < 0.25 * cY * cY;
    }

//    private int calculateColor(double iteration) {
//        int paletteSize = 256; // Assuming a palette of 256 colors
//        int color1Index = (int) Math.floor(iteration) % paletteSize;
//        int color2Index = (color1Index + 1) % paletteSize;
//        double fraction = iteration % 1;
//
//        Color color1 = palette[color1Index];
//        Color color2 = palette[color2Index];
//
//// After
//        int r = (int) ((color1.getRed() * 255 * (1 - fraction)) + (color2.getRed() * 255 * fraction));
//        int g = (int) ((color1.getGreen() * 255 * (1 - fraction)) + (color2.getGreen() * 255 * fraction));
//        int b = (int) ((color1.getBlue() * 255 * (1 - fraction)) + (color2.getBlue() * 255 * fraction));
//
//        return (255 << 24) | (r << 16) | (g << 8) | b;
//    }

    private int calculateColor(double iter) {
        if (iter == MAX_ITER) {
            return 0xFF000000; // Black for points inside the Mandelbrot set
        } else {
            int r = (int) Math.abs(iter % 256);
            int g =(int) Math.abs((iter * 3) % 256);
            int b = (int) Math.abs((iter * 5) % 256);
            return (255 << 24) | (r << 16) | (g << 8) | b;
        }
    }
}