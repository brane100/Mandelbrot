import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.BrokenBarrierException;

public class MTask implements Runnable {
    private final int threadIndex;
    private final CyclicBarrier barrier;
    private final int WIDTH, HEIGHT;
    private final int cores;
    private final double X_POS, Y_POS, ZOOM;
    private final int MAX_ITER;
    private final int startRow, endRow;
    private final int[][] pixelArray;

    public MTask(int threadIndex,
                 int startRow,
                 int endRow,
                 CyclicBarrier barrier,
                 int defaultHeight,
                 int defaultWidth,
                 int cores,
                 double X_POS,
                 double Y_POS,
                 double ZOOM,
                 int MAX_ITER,
                 int[][] pixelArray) {
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
        this.pixelArray = (pixelArray != null) ? pixelArray : new int[defaultWidth][defaultHeight];;
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
                pixelArray[x][y] = pixelColor;
            }
        }

        try {
            barrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }
    }

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

    private boolean isInPeriod2Bulb(double cX, double cY) {
        return (cX + 1) * (cX + 1) + cY * cY < 0.0625;
    }

    private boolean isInMainCardioid(double cX, double cY) {
        double q = (cX - 0.25) * (cX - 0.25) + cY * cY;
        return q * (q + (cX - 0.25)) < 0.25 * cY * cY;
    }

    private int calculateColor(double iter) {
        if (iter == MAX_ITER) {
            return 0xFF000000; // Black for points inside the Mandelbrot set
        } else {
            int r = (int) Math.abs(iter % 256);
            int g = (int) Math.abs((iter * 3) % 256);
            int b = (int) Math.abs((iter * 5) % 256);
            return (255 << 24) | (r << 16) | (g << 8) | b;
        }
    }
}