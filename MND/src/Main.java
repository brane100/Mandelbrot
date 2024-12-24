import mpi.MPI;
import mpi.MPIException;
import java.util.Scanner;
import java.util.concurrent.CyclicBarrier;

public class Main {

    private static int DEFAULT_SCREEN_WIDTH = 800;
    private static int DEFAULT_SCREEN_HEIGHT = 600;

    private final int maxIter = 2000;
    private double zoom = 1.0;
    private final double zoomFactor = 0.9;

    private double xPos = -0.5;
    private double yPos = 0;

    private String mode;

    private final int cores = Runtime.getRuntime().availableProcessors();

    private boolean isComputing;

    private int[][] pixels;


    public static void main(String[] args) throws MPIException {
        new Main().run(args);
    }

    private void run(String[] args) throws MPIException {
        // Parse command-line arguments
        for (String arg : args) {
            if (arg.startsWith("--width=")) {
                DEFAULT_SCREEN_WIDTH = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--height=")) {
                DEFAULT_SCREEN_HEIGHT = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--mode=")) {
                mode = arg.split("=")[1];
            }
        }

        if ("distributive".equals(mode)) {
            MPI.Init(args);
            if (MPI.COMM_WORLD.Rank() == 0)
                System.out.println("Width: " + DEFAULT_SCREEN_WIDTH + ", Height: " + DEFAULT_SCREEN_HEIGHT + ", Mode: " + mode);
        } else {
            System.out.println("Width: " + DEFAULT_SCREEN_WIDTH + ", Height: " + DEFAULT_SCREEN_HEIGHT + ", Mode: " + mode);
        }

        pixels = new int[DEFAULT_SCREEN_WIDTH][DEFAULT_SCREEN_HEIGHT];

        // Execute the selected mode
        switch (mode) {
            case "sequential" -> computeSequential();
            case "parallel" -> computeParallel();
            case "distributive" -> computeDistributive();
            default -> System.out.println("Invalid mode. Please specify 'sequential', 'parallel', or 'distributive'.");
        }

//        System.out.println("*****************************************");
//        System.out.println("Available commands: +/- (zoom in/out), w, a, s, d (direction), q (quit)");
//        System.out.println("*****************************************");

//        Scanner scanner = new Scanner(System.in);
        String input = "++++++++aaaaaaa++++++++++ssssssssss++++++++++++++awwww++++++++++++d--ssswwwwwwwwwwwww+++++++++++++++++++";

//        do {
//            System.out.print("Enter a command: ");
//            input = scanner.nextLine();

            // Split the input string into individual commands
            String[] commands = input.split("\\s*");

            // If multiple commands are entered, execute them sequentially
            for (String command : commands) {

                // Ignore empty input
                if (command.isEmpty()) {
                    continue;
                }

                // If computing is ongoing, ignore input
                if (isComputing) {
                    continue;
                }

                // Handle zoom in/out and panning based on input character
                switch (command) {
                    case "+":
                        zoom /= zoomFactor;
                        break;
                    case "-":
                        if (zoom > zoomFactor) {
                            zoom *= zoomFactor;
                        }
                        break;
                    case "w": // Simulate UP arrow key
                        yPos -= 0.1 / zoom;
                        break;
                    case "s": // Simulate DOWN arrow key
                        yPos += 0.1 / zoom;
                        break;
                    case "a": // Simulate LEFT arrow key
                        xPos -= 0.1 / zoom;
                        break;
                    case "d": // Simulate RIGHT arrow key
                        xPos += 0.1 / zoom;
                        break;
                    case "q":
                        System.out.println("Exiting... Done!");
                        System.exit(0);
                    default:
                        System.out.println("Invalid input. Use +, -, w, s, a, d, or q to quit.");
                        continue;
                }

                // Mark computation as started
                isComputing = true;

                // Print the current state for debugging
                // System.out.println("Zoom: " + zoom + ", Position: (" + xPos + ", " + yPos + ")");

                // Trigger the computation based on the mode
                switch (mode) {
                    case "sequential" -> computeSequential();
                    case "parallel" -> computeParallel();
                    case "distributive" -> computeDistributive();
                }

                // Mark computation as finished
                isComputing = false;
            }

//        } while (!input.equals("q"));

//        if ("distributive".equals(mode)) {
//            MPI.Finalize();
//        }
    }

    private void computeSequential() {
        long t0 = System.currentTimeMillis();

        double scale = Math.min(DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_HEIGHT) / 3.0 * zoom;

        pixels = new int[DEFAULT_SCREEN_WIDTH][DEFAULT_SCREEN_HEIGHT];

        for (int y = 0; y < DEFAULT_SCREEN_HEIGHT; y++) {
            double cY = yPos + (y - DEFAULT_SCREEN_HEIGHT / 2.0) / scale;
            generalCompute(scale, y, cY, pixels);
        }


        System.out.println((System.currentTimeMillis() - t0)  /*+"ms"*/);
//        System.out.println("Sequential computation time: " + (System.currentTimeMillis() - t0) + "ms");
    }

    private void generalCompute(double scale, int y, double cY, int[][] pixels) {
        for (int x = 0; x < DEFAULT_SCREEN_WIDTH; x++) {
            double cX = xPos + (x - DEFAULT_SCREEN_WIDTH / 2.0) / scale;
            if (isInMainCardioid(cX, cY) || isInPeriod2Bulb(cX, cY)) {
                pixels[x][y] = 0xFF000000; // Black for points inside the Mandelbrot set
            } else {
                int iter = computePixel(cX, cY);
                pixels[x][y] = calculateColor(iter);
            }
        }
    }

    private void computeParallel() {

        long t0 = System.currentTimeMillis();

        //            isComputing = false;
        CyclicBarrier barrier = new CyclicBarrier(cores, () -> {
//            isComputing = false;
        });

        int chunkSize = DEFAULT_SCREEN_HEIGHT / cores;

        for (int i = 0; i < cores; i++) {
            int startRow = i * chunkSize;
            int endRow = (i == cores - 1) ? DEFAULT_SCREEN_HEIGHT : startRow + chunkSize;
            new Thread(new MTask(i,
                    startRow,
                    endRow,
                    barrier,
                    DEFAULT_SCREEN_HEIGHT,
                    DEFAULT_SCREEN_WIDTH,
                    cores,
                    xPos,
                    yPos,
                    zoom,
                    maxIter,
                    pixels)).start();
        }

        // Wait for all tasks to finish
        try {
            barrier.await();
//            System.out.println("All threads have reached the barrier. Barrier action executed.");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Output computation time
        System.out.println(System.currentTimeMillis()-t0);
//        System.out.println("Parallel time: " + (System.currentTimeMillis() - t0)  /*+"ms "*/);
//        isComputing = false;
    }

    private void computeDistributive() throws MPIException {
        long t0 = System.currentTimeMillis();

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        int rowsPerProcess = DEFAULT_SCREEN_HEIGHT / size;
        int remainder = DEFAULT_SCREEN_HEIGHT % size;

        // Scatter: Allocate extra row to the last process if height is not evenly divisible
        int[] sendCounts = new int[size];
        int[] displs = new int[size];
        int[][] localPixels;

        for (int i = 0; i < size; i++) {
            sendCounts[i] = rowsPerProcess * DEFAULT_SCREEN_WIDTH;
            if (i == size - 1) {
                sendCounts[i] += remainder * DEFAULT_SCREEN_WIDTH;
            }
            displs[i] = i * rowsPerProcess * DEFAULT_SCREEN_WIDTH;
        }

        int localHeight = sendCounts[rank] / DEFAULT_SCREEN_WIDTH;
        localPixels = new int[DEFAULT_SCREEN_WIDTH][localHeight];

        // Each process calculates its portion of the Mandelbrot set
        double scale = Math.min(DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_HEIGHT) / 3.0 * zoom;
        for (int y = 0; y < localHeight; y++) {
            double cY = yPos + ((rank * rowsPerProcess + y) - DEFAULT_SCREEN_HEIGHT / 2.0) / scale;
            generalCompute(scale, y, cY, localPixels);
        }

        long computationTime = System.currentTimeMillis() - t0;

        // Gather all the computed pixels back to the root process
        int[][] gatheredPixels = new int[DEFAULT_SCREEN_WIDTH][DEFAULT_SCREEN_HEIGHT];
        int[] gatheredPixelsFlat = new int[DEFAULT_SCREEN_WIDTH * DEFAULT_SCREEN_HEIGHT];
        int[] localPixelsFlat = new int[DEFAULT_SCREEN_WIDTH * localHeight];

        // Flatten the local pixel array for MPI_Gather
        for (int y = 0; y < localHeight; y++) {
            for (int x = 0; x < DEFAULT_SCREEN_WIDTH; x++) {
                localPixelsFlat[y * DEFAULT_SCREEN_WIDTH + x] = localPixels[x][y];
            }
        }

        // Gather the flattened pixel arrays at the root process
        MPI.COMM_WORLD.Gatherv(localPixelsFlat, 0, localPixelsFlat.length, MPI.INT, gatheredPixelsFlat, 0, sendCounts, displs, MPI.INT, 0);

        // Gather the computation times at the root process
        long[] computationTimes = new long[size];
        MPI.COMM_WORLD.Gather(new long[]{computationTime}, 0, 1, MPI.LONG, computationTimes, 0, 1, MPI.LONG, 0);

        // Unflatten the gathered array in the root process
        if (rank == 0) {
            for (int y = 0; y < DEFAULT_SCREEN_HEIGHT; y++) {
                for (int x = 0; x < DEFAULT_SCREEN_WIDTH; x++) {
                    gatheredPixels[x][y] = gatheredPixelsFlat[y * DEFAULT_SCREEN_WIDTH + x];
                }
            }
//            System.out.println("Distributive computation completed.");
            System.out.println(/*"Total computation time: " + */(System.currentTimeMillis() - t0)/* + "ms"*/);

            // Print the computation times for each process
            for (int i = 0; i < size; i++) {
//                System.out.println("Process " + i + " took " + computationTimes[i] + "ms to compute its part.");
            }
        }
    }

    private int computePixel(double cX, double cY) {
        double zx = 0, zy = 0;

        int iter;
        for (iter = 0; iter < maxIter && zx * zx + zy * zy < 4; iter++) {
            double tmp = zx * zx - zy * zy + cX;
            zy = 2.0 * zx * zy + cY;
            zx = tmp;
        }

        return iter;
    }

    private int calculateColor(int iter) {
        if (iter == maxIter) {
            return 0xFF000000; // Black for points inside the Mandelbrot set
        } else {
            int r = iter % 256;
            int g = (iter * 3) % 256;
            int b = (iter * 5) % 256;
            return (255 << 24) | (r << 16) | (g << 8) | b;
        }
    }

    private boolean isInMainCardioid(double cX, double cY) {
        double q = (cX - 0.25) * (cX - 0.25) + cY * cY;
        return q * (q + (cX - 0.25)) < 0.25 * cY * cY;
    }

    private boolean isInPeriod2Bulb(double cX, double cY) {
        return (cX + 1) * (cX + 1) + cY * cY < 0.0625;
    }
}