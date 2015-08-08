package ika.app;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;

/**
 * Launch process with maximum possible heap space. Code parts from Whitebox GAT
 * http://www.uoguelph.ca/~hydrogeo/Whitebox/
 * A process can only be launched once.
 *
 * @author Bernhard Jenny, Cartography and Geovisualization Group, Oregon State
 * University
 */
public class ProcessLauncher {

    /**
     * The size of the desired heap in MB. This is currently determined 
     * heuristically by findMaximumHeapSize. 
     */
    private int heapSizeMB;
    
    /**
     * The launched process.
     */
    private Process process = null;

    public ProcessLauncher() {
        findMaximumHeapSize();
    }

    /**
     * Finds the maximum heap size and stores the result in this.heapSizeMB.
     */
    private void findMaximumHeapSize() {
        long ONE_GB = 1024 * 1024 * 1024;
        long amountOfMemory = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize();
        if (System.getProperty("sun.arch.data.model").contains("32")) {
            if (196608 < amountOfMemory / 2) {
                heapSizeMB = 1200;
            } else {
                heapSizeMB = (int) (amountOfMemory / 2);
            }
        } else {
            // reserve one third of total memory or 1 GB for other processes,
            // whatever is smaller.
            int heapReserve = (int) Math.min(amountOfMemory / 3, ONE_GB);
            heapSizeMB = (int) ((amountOfMemory - heapReserve) / 1024 / 1024);
            if (heapSizeMB <= 500) {
                heapSizeMB = 1000;
            }
        }
    }

    /**
     * Returns the file path for an icon file in an OS X app bundle.
     * @param fileName Name of the icon file.
     * @return The path to the icon file.
     */
    public String findXDockIconPath(String fileName) {
        try {
            String applicationDirectory = java.net.URLDecoder.decode(getClass().getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF-8");

            applicationDirectory = new File(applicationDirectory).getParent();

            // step one level up out of the "Java" directory
            applicationDirectory = new File(applicationDirectory).getParent();

            if (!applicationDirectory.endsWith(File.separator)) {
                applicationDirectory += File.separator;
            }

            // one level down into the "Resources" directory
            applicationDirectory += "Resources" + File.separator;

            return applicationDirectory + fileName;
        } catch (Throwable ex) {
            return "";
        }
    }

    /**
     * Starts a new process and calls the main method of a specified class.
     *
     * @param className The class with the main method.
     * @param xDockName The name to display in the OS X dock.
     * @param xDockIconPath The icon to display in the OS X dock.
     * @throws IOException Exception if the executable file cannot be found or
     * launched.
     */
    public void startJVM(String className, String xDockName, String xDockIconPath) throws IOException {

        if (process != null) {
            throw new IllegalStateException("process already launched");
        }
        
        String xmx = "-Xmx" + heapSizeMB + "M";
        String xms = "-Xms" + heapSizeMB + "M";

        String separator = System.getProperty("file.separator");
        String classpath = System.getProperty("java.class.path");
        String path = System.getProperty("java.home")
                + separator + "bin" + separator + "java";

        ProcessBuilder processBuilder;
        if (System.getProperty("os.name").contains("Mac")) {
            processBuilder = new ProcessBuilder(path, xmx, xms,
                    "-cp", classpath,
                    "-Xdock:name=" + xDockName,
                    "-Xdock:icon=" + xDockIconPath,
                    className);
        } else {
            processBuilder = new ProcessBuilder(path, xmx, xms, "-cp", classpath, className);
        }
        process = processBuilder.start();
    }

}
