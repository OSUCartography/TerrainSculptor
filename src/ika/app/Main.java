package ika.app;

import com.sanityinc.jargs.CmdLineParser;
import com.sanityinc.jargs.CmdLineParser.IllegalOptionValueException;
import com.sanityinc.jargs.CmdLineParser.Option;
import ika.utils.IconUtils;
import ika.geo.GeoGrid;
import ika.geo.grid.TerrainSculptorFilter;
import ika.geoexport.ESRIASCIIGridExporter;
import ika.geoimport.ESRIASCIIGridReader;
import ika.gui.*;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Main entry point.
 * 
 * Command line option contributed by Adrian Weber, University of Bern.
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 * @author Adrian Weber, Centre for Development and Environment, University of Bern
 */
public class Main {

    /**
     * An ProgressIndicator implementation which sends messages to the standard
     * output.
     */
    private static class CmdLineProgress implements ProgressIndicator {

        private boolean aborted = false;

        private int tasksCount = 0;

        @Override
        public void start() {
        }

        @Override
        public void abort() {
            this.aborted = true;
        }

        @Override
        public void completeProgress() {
        }

        @Override
        public boolean progress(int percentage) {
            System.out.print(percentage + "%\r");
            return true;
        }

        @Override
        public boolean isAborted() {
            return this.aborted;
        }

        @Override
        public void disableCancel() {
        }

        @Override
        public void enableCancel() {
        }

        @Override
        public void setMessage(String msg) {
            System.out.println(msg);
        }

        @Override
        public void setTotalTasksCount(int tasksCount) {
            this.tasksCount = tasksCount;
        }

        @Override
        public int getTotalTasksCount() {
            return this.tasksCount;
        }

        @Override
        public void nextTask() {
        }

        @Override
        public void nextTask(String message) {
        }

        @Override
        public int currentTask() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }

    /**
     * Prints how to use TerrainSculptor from the command line.
     */
    private static void printUsage() {
        System.err.println(
                "Usage: TerrainSculptor [--detail int (0 < int < 50)]\n"
                + "                       [--lowlandmountain int (0 < int < 45)]\n"
                + "                       [--valleysremoval int (0 < int < 20)]\n"
                + "                       [--valleydepth int (0 < int < 500)]\n"
                + "                       [--valleywidth int (0 < int < 100)]\n"
                + "                       [--ridgesremoval int (0 < int < 20)]\n"
                + "                       [--ridgesexaggeration int (110 < int < 2000)]\n"
                + "                       [--ridgessharpness int (0 < int < 150)]\n"
                + "                       src_dem dst_dem");
    }

    /**
     * An command line option that expects an integer value within a defined
     * range.
     */
    public static class IntegerRangeOption extends Option.IntegerOption {

        private int minValue;
        private int maxValue;
        private int defaultValue;

        /**
         *
         * @param longForm
         * @param minValue Minimal accepted value
         * @param maxValue Maximal accepted value
         * @param defaultValue The default value
         */
        public IntegerRangeOption(String longForm, int minValue, int maxValue, int defaultValue) {
            super(longForm);
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.defaultValue = defaultValue;
        }

        @Override
        protected Integer parseValue(String arg, java.util.Locale locale)
                throws IllegalOptionValueException {
            // Return the default value is argument is not set
            if (arg == null) {
                return this.defaultValue;
            }
            int value;
            // Try to parse the argument
            try {
                value = Integer.parseInt(arg);
            } catch (java.lang.NumberFormatException e) {
                throw new IllegalOptionValueException(this, arg);
            }
            // Check if the values is within the set range
            if (value < this.minValue || value > this.maxValue) {
                throw new IllegalOptionValueException(this, arg);
            }
            return value;
        }

        @Override
        public Integer getDefaultValue() {
            return this.defaultValue;
        }

    }

    /**
     * main routine for the application.
     *
     * @param args the command line arguments
     */
    public static void main(String args[]) {

        // Create a new command line parser
        CmdLineParser parser = new CmdLineParser();
        // Add all different options from the advanced interface.
        // Minimum, maximum and default values come from initComponents method
        // in TerrainSculptorWindow class
        IntegerRangeOption gridFilterLoopsOption = new IntegerRangeOption("detail", 0, 50, 10);
        parser.addOption(gridFilterLoopsOption);
        IntegerRangeOption combinationSlopeThresholdOption = new IntegerRangeOption("lowlandmountain", 0, 45, 15);
        parser.addOption(combinationSlopeThresholdOption);
        IntegerRangeOption valleysMeanFilterLoopsOption = new IntegerRangeOption("valleysremoval", 0, 20, 5);
        parser.addOption(valleysMeanFilterLoopsOption);
        IntegerRangeOption valleysExaggerationOption = new IntegerRangeOption("valleydepth", 0, 500, 40);
        parser.addOption(valleysExaggerationOption);
        IntegerRangeOption valleysCurvatureThresholdOption = new IntegerRangeOption("valleywidth", 0, 100, 50);
        parser.addOption(valleysCurvatureThresholdOption);
        IntegerRangeOption ridgesMeanFilterLoopsOption = new IntegerRangeOption("ridgesremoval", 0, 20, 5);
        parser.addOption(ridgesMeanFilterLoopsOption);
        IntegerRangeOption ridgesExaggerationOption = new IntegerRangeOption("ridgesexaggeration", 110, 2000, 500);
        parser.addOption(ridgesExaggerationOption);
        IntegerRangeOption planCurvatureWeightOption = new IntegerRangeOption("ridgessharpness", 0, 150, 150);
        parser.addOption(planCurvatureWeightOption);
        // Add a help option which prints the usage
        Option<Boolean> help = parser.addBooleanOption('h', "help");

        // Try to parse the arguments
        try {
            parser.parse(args);
        } catch (CmdLineParser.OptionException e) {
            System.err.println(e.getMessage());
            printUsage();
            System.exit(2);
        }

        // Print the usage statement if the help option is set
        if (parser.getOptionValue(help, false)) {
            printUsage();
            System.exit(0);
        }

        // Get the remaining arguments. 
        String[] remainingArgs = parser.getRemainingArgs();
        // If neither a source nor a destination elevation model is set, start
        // the GUI application.
        if (remainingArgs.length != 2) {

            // on Mac OS X: take the menu bar out of the window and put it on top
            // of the main screen.
            if (ika.utils.Sys.isMacOSX()) {
                System.setProperty("apple.laf.useScreenMenuBar", "true");
            }

            // use the standard look and feel
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            // set icon for JOptionPane dialogs. This is done automatically on Mac 10.5.
            if (!ika.utils.Sys.isMacOSX_10_5_orHigherWithJava5()) {
                java.util.Properties props
                        = ika.utils.PropertiesLoader.loadProperties("ika.app.Application");
                IconUtils.setOptionPaneIcons(props.getProperty("ApplicationIcon"));
            }

            // RepaintManager.setCurrentManager(new ThreadCheckingRepaintManager(false));
            // Replace title of progress monitor dialog by empty string.
            UIManager.put("ProgressMonitor.progressText", "");

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    // create a new empty window
                    TerrainSculptorWindow w = (TerrainSculptorWindow) MainWindow.newDocumentWindow();
                    w.setExtendedState(w.getExtendedState() | JFrame.MAXIMIZED_BOTH);

                    /*
                     // initialize output and error stream for display in a window
                     String appName = ika.app.ApplicationInfo.getApplicationName();
                     String outTitle = appName + " - Standard Output";
                     String errTitle = appName + " - Error Messages";
                     new ika.utils.StdErrOutWindows(null, outTitle, errTitle);
                     */
                }
            });            
        } else {

            // In other cases start the application in batch mode with the arguments
            // set on the command line.
            
            CmdLineProgress p = new CmdLineProgress();

            // Create a new grid filter
            TerrainSculptorFilter gridFilter = new TerrainSculptorFilter();

            // Get paths to input and output elevation model
            String inputFilePath = remainingArgs[0];
            String outputFilePath = remainingArgs[1];

            try {
                // Read the input grid
                p.setMessage("Reading grid");
                GeoGrid grid = ESRIASCIIGridReader.read(inputFilePath, p);

                // Add it to the grid filter
                gridFilter.setGrid(grid);

                // Set all grid filter options. This is analog to readGUI()
                // in class TerrainSculptorWindow
                gridFilter.setGridFilterLoops(parser.getOptionValue(
                        gridFilterLoopsOption,
                        gridFilterLoopsOption.getDefaultValue()));
                gridFilter.setValleysMeanFilterLoops(parser.getOptionValue(
                        valleysMeanFilterLoopsOption,
                        valleysMeanFilterLoopsOption.getDefaultValue()));
                gridFilter.setValleysExaggeration(parser.getOptionValue(
                        valleysExaggerationOption,
                        valleysExaggerationOption.getDefaultValue()) / 100f);
                gridFilter.setValleysCurvatureUpperLimit(parser.getOptionValue(
                        valleysCurvatureThresholdOption,
                        valleysCurvatureThresholdOption.getDefaultValue()) / 100f);
                gridFilter.setRidgesMeanFilterLoops(parser.getOptionValue(
                        ridgesMeanFilterLoopsOption,
                        ridgesMeanFilterLoopsOption.getDefaultValue()));
                gridFilter.setRidgesExaggeration(parser.getOptionValue(
                        ridgesExaggerationOption,
                        ridgesExaggerationOption.getDefaultValue()) / 100f);
                gridFilter.setCombinationSlopeThreshold(parser.getOptionValue(
                        combinationSlopeThresholdOption,
                        combinationSlopeThresholdOption.getDefaultValue()));
                gridFilter.setRidgesPlancurvatureWeight(parser.getOptionValue(
                        planCurvatureWeightOption,
                        planCurvatureWeightOption.getDefaultValue()) / 100f);

                // and filter the input grid
                java.util.ArrayList<GeoGrid> grids = gridFilter.filter(p);

                // Get the resulting filtered grid and save it to the output path
                for (GeoGrid currentGrid : grids) {
                    if (currentGrid.getName().equals("Result")) {
                        p.setMessage("Exporting grid");
                        ESRIASCIIGridExporter.export(currentGrid, outputFilePath);
                        System.exit(0);
                    }
                }
            } catch (java.io.IOException e) {
                System.err.println(e.getMessage());
                System.exit(2);
            }
        }

    }
}
