package ika.geoimport;

import ika.geo.GeoGrid;
import ika.gui.ProgressIndicator;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Scanner;
import java.util.StringTokenizer;

public class ESRIASCIIGridReader {

    private static class ESRIASCIIGridHeader {

        protected int cols = 0;
        protected int rows = 0;
        protected double west = Double.NaN;
        protected double south = Double.NaN;
        protected double cellSize = Double.NaN;
        protected float noDataValue = Float.NaN;

        /*
         * returns whether valid values have been found
         */
        protected boolean isValid() {

            return (cols > 0 
                    && rows > 0
                    && cellSize > 0
                    && !Double.isNaN(west)
                    && !Double.isNaN(south));
            // noDataValue is optional
        }

        /**
         * Reads cols, rows, west, south, cellSize and noDataValue from the header.
         * Throws an exception if this is not a valid header.
         * @param scanner Scanner must be initialized to use dot as decimal separator.
         * @throws IOException
         */
        private void readHeader(Scanner scanner) throws IOException {

            cols = rows = 0;
            west = south = cellSize = Double.NaN;
            noDataValue = Float.NaN;

            while (scanner.hasNext()) {

                if (scanner.hasNextDouble()) {
                    // next line starts with number, must be grid
                    break;
                }

                String str = scanner.next().trim().toLowerCase();
                if (str.equals("ncols")) {
                    this.cols = scanner.nextInt();
                } else if (str.equals("nrows")) {
                    this.rows = scanner.nextInt();
                } else if (str.equals("xllcenter") || str.equals("xllcorner")) {
                    this.west = scanner.nextDouble();
                } else if (str.equals("yllcenter") || str.equals("yllcorner")) {
                    this.south = scanner.nextDouble();
                } else if (str.equals("cellsize")) {
                    this.cellSize = scanner.nextDouble();
                } else if (str.startsWith("nodata")) {
                    this.noDataValue = scanner.nextFloat();
                } else {

                    // make sure the line starts with a number
                    if (!scanner.hasNextDouble()) {
                        throw new IOException();
                    }

                    // done reading the header
                    break;
                }
            }

            if (!isValid()) {
                throw new IOException();
            }
        }
    }

    /**
     * Returns whether a scanner references valid data that can be read.
     * @param scanner
     * @return
     * @throws IOException
     */
    public static boolean canRead(Scanner scanner) {
        try {
            ESRIASCIIGridHeader header = new ESRIASCIIGridHeader();
            header.readHeader(scanner);
            return header.isValid();
        } catch (Exception exc) {
            return false;
        }
    }

    public static boolean canRead(String filePath) {
        Scanner scanner = null;
        try {
            scanner = createUSScanner(new FileInputStream(filePath));
            return ESRIASCIIGridReader.canRead(scanner);
        } catch (Exception exc) {
            return false;
        } finally {
            if (scanner != null) {
                try {
                    scanner.close();
                } catch (Throwable exc) {
                }
            }
        }
    }

    /** Read a Grid from a file in ESRI ASCII format.
     * @param fileName The path to the file to be read.
     * @return The read grid.
     */
    public static GeoGrid read(String filePath) throws java.io.IOException {
        return ESRIASCIIGridReader.read(filePath, null);
    }

    /** Read a Grid from a file in ESRI ASCII format.
     * @param fileName The path to the file to be read.
     * @param progress A WorkerProgress to inform about the progress.
     * @return The read grid.
     */
    public static GeoGrid read(String filePath, ProgressIndicator progressIndicator)
            throws java.io.IOException {

        File file = new File(filePath);
        FileInputStream fis = new FileInputStream(file.getAbsolutePath());
        GeoGrid grid = ESRIASCIIGridReader.read(fis, progressIndicator);
        if (progressIndicator != null && progressIndicator.isAborted()) {
            return null;
        }
        String name = file.getName();
        if (!"".equals(name)) {
            grid.setName(name);
        }
        return grid;
    }

    /** Read a Grid from a stream in ESRI ASCII format.
     * @param is The stream to read from. The stream is closed at the end.
     * @param progress A WorkerProgress to inform about the progress.
     * @return The read grid.
     */
    public static GeoGrid read(InputStream input, ProgressIndicator progressIndicator)
            throws IOException {

        // initialize the progress monitor at the beginning
        if (progressIndicator != null) {
            progressIndicator.start();
        }

        Scanner scanner = createUSScanner(new BufferedInputStream(input));       
        try {
            ESRIASCIIGridHeader header = new ESRIASCIIGridHeader();
            header.readHeader(scanner);

            GeoGrid grid = new GeoGrid(header.cols, header.rows, header.cellSize);
            grid.setWest(header.west);
            grid.setNorth(header.south + (header.rows - 1) * header.cellSize);

            // use legacy StringTokenizer, which is considerably faster than
            // the Scanner class, which uses regular expressions.
            StringTokenizer tokenizer = new StringTokenizer(scanner.nextLine(), " ");

            // read grid values. Rows are ordered top to bottom.
            for (int row = 0; row < header.rows; row++) {
                // update progress info
                if (progressIndicator != null) {
                    int perc = (int) ((double) (row + 1) / header.rows * 100);
                    if (!progressIndicator.progress(perc)) {
                        return null;
                    }
                }

                // read one row
                for (int col = 0; col < header.cols; col++) {

                    // a logical row in the grid does not necesseraly correspond
                    // to a line in the file!
                    if (!tokenizer.hasMoreTokens()) {
                        tokenizer = new StringTokenizer(scanner.nextLine(), " ");
                    }
                    final float v = Float.parseFloat(tokenizer.nextToken());
                    if (v == header.noDataValue || Float.isNaN(v)) {
                        grid.setValue(Float.NaN, col, row);
                    } else {
                        grid.setValue(v, col, row);
                    }
                }
            }
            return grid;
        } finally {
            try {
                // this closes the input stream
                scanner.close();
            } catch (Exception exc) {
            }
        }

    }

    /**
     * Creates a scanner for ASCII text with a period as decimal separator.
     * @param is
     * @return
     * @throws FileNotFoundException
     */
    private static Scanner createUSScanner(InputStream is) throws FileNotFoundException {
        Scanner scanner = new Scanner(is, "US-ASCII");
        scanner.useLocale(Locale.US);
        return scanner;
    }
}
