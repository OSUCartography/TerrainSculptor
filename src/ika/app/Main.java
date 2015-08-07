package ika.app;

import ika.utils.IconUtils;
import javax.swing.*;
import ika.gui.*;

/**
 * Main entry point.
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class Main {

    /**
     * main routine for the application.
     *
     * @param args the command line arguments
     */
    public static void main(String args[]) {

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

        // set the Quaqua Look and Feel in the UIManager to use the mac color chooser
        /*
         try {
         if (ika.utils.Sys.isMacOSX())
         UIManager.setLookAndFeel("ch.randelshofer.quaqua.QuaquaLookAndFeel");
         } catch (Exception e) {
         e.printStackTrace();
         }
         */
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

    }
}
