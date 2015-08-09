package ika.gui;

import ch.ethz.karto.swa.atlas.SystemInfo;
import com.fizzysoft.sdu.RecentDocumentsManager;
import ika.geo.*;
import ika.geo.clipboard.GeoTransferable;
import ika.geo.grid.GridChangeVoidOperator;
import ika.geo.grid.GridScaleOperator;
import ika.geo.grid.GridShadeOperator;
import ika.geo.grid.GridToImageOperator;
import ika.geoexport.ESRIASCIIGridExporter;
import ika.geoimport.*;
import ika.geo.grid.TerrainSculptorFilter;
import ika.utils.*;
import ika.map.tools.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * A document window containing a map.
 * @author  Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class TerrainSculptorWindow extends MainWindow
        implements RenderParamsProvider {

    
    // names of grids that are displayed in the map
    public static final ArrayList<String> GRID_NAMES = new ArrayList<String>();

    static {
        GRID_NAMES.add(TerrainSculptorFilter.RESULT_NAME);
        GRID_NAMES.add(TerrainSculptorFilter.ORIGINAL_NAME);
        GRID_NAMES.add(TerrainSculptorFilter.VALLEYS_NAME);
        GRID_NAMES.add(TerrainSculptorFilter.RIDGES_NAME);
        GRID_NAMES.add(TerrainSculptorFilter.COMBINATION_WEIGHT_NAME);
    }
    private TerrainSculptorFilter gridFilter = new TerrainSculptorFilter();
    private ArrayList<GeoGrid> displayGrids;
    private boolean adjustingGUI = false;
    private RecentDocumentsManager rdm;
    private boolean deferredFiltering = false;
    
    /**
     * Creates new form BaseMainWindow
     */
    public TerrainSculptorWindow() {

        this.initRecentDocumentsMenu(this);
        this.initComponents();
        this.initMenusForMac();

        // pass a parent GeoSet to the MapComponent
        this.mapComponent.setGeoSet(new GeoMap());

        // specify the format of displayed coordinates
        this.mapComponent.setCoordinateFormatter(new CoordinateFormatter("###,##0.#", "###,##0.#", 1));

        // register this object so that rendering parameters can be customized.
        this.mapComponent.setRenderParamsProvider(this);

        mapComponent.getPageFormat().setVisible(false);
        
        // add a MapEventListener: When the map changes, the dirty
        // flag is set and the Save menu item updated.
        MapEventListener mel = new MapEventListener() {

            @Override
            public void mapEvent(MapEvent evt) {
                setDocumentDirty();
                updateAllMenus();
            }
        };
        // register the MapEventListener to be informed whenever the map changes.
        GeoSetBroadcaster.addMapEventListener(mel, this.mapComponent.getGeoSet());

        // register the coordinate info panel with the map
        this.coordinateInfoPanel.registerWithMapComponent(this.mapComponent);

        // register the scale info panel with the map
        this.scaleLabel.registerWithMapComponent(this.mapComponent);

        // set the initial tool
        this.mapComponent.setMapTool(new ZoomInTool(mapComponent));

        // maximise the size of this window. Fill the primary screen.
        this.setExtendedState(JFrame.MAXIMIZED_BOTH);
        this.validate();

        // add a window listener that updates the menus when the
        // state of the window changes (minimized, close, focus lost, activated, etc.)
        WindowListener windowListener = new WindowListener() {

            public void windowChanged(WindowEvent e) {
                TerrainSculptorWindow mainWindow = (TerrainSculptorWindow) e.getWindow();
                mainWindow.updateAllMenus();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                this.windowChanged(e);
            }

            @Override
            public void windowClosing(WindowEvent e) {
            }

            @Override
            public void windowClosed(WindowEvent e) {
            }

            @Override
            public void windowIconified(WindowEvent e) {
                this.windowChanged(e);
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
                this.windowChanged(e);
            }

            @Override
            public void windowActivated(WindowEvent e) {
                this.windowChanged(e);
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                this.windowChanged(e);
            }
        };
        this.addWindowListener(windowListener);

        // setup undo/redo
        //this.mapComponent.registerUndoMenuItems(this.undoMenuItem, this.redoMenuItem);

        // initialize the undo/redo manager with the current (empty) map content.
        this.mapComponent.addUndo(null);

        this.getRootPane().addPropertyChangeListener(new java.beans.PropertyChangeListener() {

            @Override
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                windowModifiedPropertyChange(evt);
            }
        });
        writeGUI();
        
        showNoTerrainMessage();
    }

    private String errTitle() {
        return appName() + " Error";
    }
    
    private void initRecentDocumentsMenu(final Component parent) {
        rdm = new RecentDocumentsManager() {

            private Preferences getPreferences() {
                return Preferences.userNodeForPackage(TerrainSculptorWindow.class);
            }

            @Override
            protected byte[] readRecentDocs() {
                return getPreferences().getByteArray("RecentDocuments", null);
            }

            @Override
            protected void writeRecentDocs(byte[] data) {
                getPreferences().putByteArray("RecentDocuments", data);
            }

            @Override
            protected void openFile(File file, ActionEvent event) {
                try {
                    if (file != null) {
                        openDEM(file.getCanonicalPath());
                    }
                } catch (IOException ex) {
                    String msg = "Could not open the terrain model.";
                    ErrorDialog.showErrorDialog(msg, errTitle(), ex, parent);
                }
            }
        };
    }

    private String appName() {
        Properties props = PropertiesLoader.loadProperties("ika.app.Application");
        return props.getProperty("ApplicationName");
    }

    private void writeGUI() {

        try {
            adjustingGUI = true;

            meanFilterLoopsSlider.setValue(gridFilter.getGridFilterLoops());

            ridgesMeanFilterLoopsSlider.setValue(gridFilter.getRidgesMeanFilterLoops());
            ridgesExaggerationSlider.setValue((int) (gridFilter.getRidgesExaggeration() * 100));
            planCurvatureWeightSlider.setValue((int) (gridFilter.getRidgesPlancurvatureWeight() * 100));

            valleysMeanFilterLoopsSlider.setValue(gridFilter.getValleysMeanFilterLoops());
            valleysCurvatureThresholdSlider.setValue((int) (gridFilter.getValleysCurvatureUpperLimit() * 100));
            valleysExaggerationSlider.setValue((int) (gridFilter.getValleysExaggeration() * 100));

            combinationSlopeThresholdSlider.setValue((int) (gridFilter.getCombinationSlopeThreshold()));
        } finally {
            adjustingGUI = false;
        }

    }
    
    private void showNoTerrainMessage() {
        String msg = "Open a terrain model with File > Open Terrain\u2026";
        mapComponent.setInfoString(msg);
        mapComponent.showAll();
    }

    /**
     * Read a grid file asynchronously.
     * @param filePath
     */
    public void openDEM(final String filePath) {

        if (!ESRIASCIIGridReader.canRead(filePath)) {
            showNoTerrainMessage();
            String msg = "The selected file cannot be read.";
            ErrorDialog.showErrorDialog(msg, errTitle(), null, this);
            return;
        }

        // release previous grid to free memory
        gridFilter.setGrid(null);
        displayGrids = null;

        SwingWorkerWithProgressIndicator worker;
        worker = new SwingWorkerWithProgressIndicator<GeoGrid>(
                this, appName() + " - Data Import", "", true) {

            @Override
            public void done() {
                try {
                    GeoGrid grid = get(); // also tests for exceptions
                    // set title of window
                    String name = grid.getName();
                    if (name != null && name.trim().length() > 0) {
                        String title = name.trim();
                        if (Sys.isWindows()) {

                            title += " - " + appName();
                        }
                        setTitle(title);
                    }
                    grid.setName("Original Grid");

                    rdm.addDocument(new File(filePath), null);

                    gridFilter.setGrid(grid);
                    readGUIAndFilter(false);

                } catch (Throwable ex) {
                    showNoTerrainMessage();
                    String exmsg = ex.getMessage();
                    if (exmsg != null && exmsg.contains("user canceled")) {
                        return;
                    }
                    //ex.printStackTrace();

                    String msg = "An error occured";
                    ErrorDialog.showErrorDialog(msg, errTitle(), ex, TerrainSculptorWindow.this);
                    return;
                } finally {
                    complete();
                }
            }

            @Override
            protected GeoGrid doInBackground() throws Exception {
                // read grid from file
                GeoGrid grid = ESRIASCIIGridReader.read(filePath, this);
                if (isAborted()) {
                    throw new IllegalStateException("user canceled");
                }
                return grid;
            }
        };

        worker.setMaxTimeWithoutDialog(1);
        worker.setMessage("Reading terrain model \"" + FileUtils.getFileName(filePath) + "\"");
        worker.execute();
    }

    public void importGrid() {
        String path = ika.utils.FileUtils.askFile(this, "Select an ESRI ASCII Grid", true);
        if (path != null) {
            openDEM(path);
        }
    }

    /**
     * Displays a dialog with information about the original sourceGrid.  */
    public void showGridInfo() {

        try {
            GeoGrid grid = gridFilter.getGrid();
            if (grid == null) {
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("<html> <b>");
            sb.append("Terrain Model");
            sb.append("</b><br><br>");
            sb.append(grid.toStringWithStatistics("<br>"));
            sb.append("</html>");

            String title = "Terrain Model Info";
            JOptionPane.showMessageDialog(this, sb.toString(), title, JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception exc) {
            String msg = "An error occured.";
            ErrorDialog.showErrorDialog(msg, errTitle(), exc, this);
        }
    }

    private void resetMap(boolean hadGrids) {
        String nameOfDisplayGrid = getSelectedGridName();

        GeoGrid grid = getNamedGrid(nameOfDisplayGrid);
        Rectangle2D visArea = mapComponent.getVisibleArea();
        mapComponent.removeAllGeoObjects();
        if (grid == null) {
            return;
        }

        // generate an image for display in the map
        // FIXME: instead of allocating a new GeoImage, the existing image 
        // could be re-used.
        final GeoImage image;
        boolean shading = !("Combination Weight".equals(nameOfDisplayGrid));
        if (shading) {
            image = new GridShadeOperator().operateToImage(grid);
        } else {
            image = new GridToImageOperator().operate(grid);
        }
        image.setSelectable(false);

        mapComponent.addGeoObject(image, false);
        mapComponent.zoomOnRectangle(visArea);
        if (!hadGrids || !mapComponent.isObjectVisibleOnMap(image, true)) {
            mapComponent.showAll();
        }

    }

    private String getSelectedGridName() {

        if (viewFinalCheckBoxMenuItem.isSelected()) {
            return GRID_NAMES.get(0);
        }
        if (viewOriginalCheckBoxMenuItem.isSelected()) {
            return GRID_NAMES.get(1);
        }
        if (viewLowlandsCheckBoxMenuItem.isSelected()) {
            return GRID_NAMES.get(2);
        }
        if (viewMountainsCheckBoxMenuItem.isSelected()) {
            return GRID_NAMES.get(3);
        }
        if (viewCombinationCheckBoxMenuItem.isSelected()) {
            return GRID_NAMES.get(4);
        }

        return null;
    }

    private GeoGrid getNamedGrid(String name) {
        if (name == null || displayGrids == null) {
            return null;
        }
        for (GeoGrid grid : displayGrids) {
            if (grid != null && name.equals(grid.getName())) {
                return grid;
            }
        }
        return null;

    }

    private void filter() {

        TerrainSculptorProgressIndicator worker;
        worker = new TerrainSculptorProgressIndicator<ArrayList<GeoGrid>>(
                this, appName() + " - Filtering", "", true) {

            @Override
            public void done() {

                boolean hadGrids = (displayGrids != null) && displayGrids.size() > 0;
                try {
                    ArrayList<GeoGrid> newGrids = get(); // also tests for exceptions
                    if (newGrids == null) {
                        return;
                    }
                    displayGrids = newGrids;
                    mapComponent.setInfoString("");
                    
                } catch (Throwable ex) {
                    String exmsg = ex.getMessage();
                    if (exmsg != null && exmsg.contains("user canceled")) {
                        // show button to restart filtering
                        CardLayout cl = (CardLayout)(centerPanel.getLayout());
                        cl.show(centerPanel, "filterButton");
                        filteringStatusLabel.setText("Filtering has been canceled.");
                        return;
                    }
                    ex.printStackTrace();
                    String msg = "An error occured";
                    ErrorDialog.showErrorDialog(msg, errTitle(), ex, TerrainSculptorWindow.this);
                } finally {
                    deferredFiltering = isDeferredFiltering();
                    updateEditMenu(); // enable Filter command
                    try {
                        resetExportMenu();
                        resetMap(hadGrids);
                    } finally {
                        complete();
                    }
                }
            }

            @Override
            protected ArrayList<GeoGrid> doInBackground() throws Exception {
                ArrayList<GeoGrid> filteredGrids = gridFilter.filter(this);
                if (isAborted()) {
                    throw new IllegalStateException("user canceled");
                }
                return filteredGrids;
            }
        };

        worker.setDeferredFiltering(deferredFiltering);
        worker.setMaxTimeWithoutDialog(1);
        worker.setMessage("");
        worker.setTotalTasksCount(7);
        worker.setIndeterminate(false);
        worker.start();
        worker.execute();
    }

    private void resetExportMenu() {

        exportMenu.removeAll();
        if (displayGrids == null) {
            return;
        }
        for (GeoGrid geoGrid : displayGrids) {
            JMenuItem menuItem = new JMenuItem();
            if (geoGrid == null || geoGrid.getName() == null) {
                continue;
            }
            menuItem.setText(geoGrid.getName());
            menuItem.addActionListener(new java.awt.event.ActionListener() {

                @Override
                public void actionPerformed(java.awt.event.ActionEvent evt) {
                    String name = ((JMenuItem) evt.getSource()).getText();
                    String path = FileUtils.askFile(null, "Export Grid", name + ".asc", false, "asc");
                    if (path != null) {
                        for (GeoGrid geoGrid : displayGrids) {
                            if (name.equals(geoGrid.getName())) {
                                try {
                                    ESRIASCIIGridExporter.export(geoGrid, path);
                                } catch (IOException ex) {
                                    String msg = "Could not export grid";
                                    ErrorDialog.showErrorDialog(msg, errTitle(), ex, null);
                                }
                                break;
                            }

                        }
                    }
                }
            });
            exportMenu.add(menuItem);
        }

    }

    /** 
     * Mac OS X specific initialization.
     */
    private void initMenusForMac() {
        if (ika.utils.Sys.isMacOSX()) {

            // remove exit menu item on Mac OS X
            this.fileMenu.remove(this.exitMenuSeparator);
            this.fileMenu.remove(this.exitMenuItem);
            this.fileMenu.validate();

            // remove info menu item on Mac OS X
            this.helpMenu.remove(this.infoMenuItem);
            this.helpMenu.validate();
            if (this.helpMenu.getMenuComponentCount() == 0) {
                this.menuBar.remove(helpMenu);
                this.menuBar.validate();
            }

            this.editMenu.validate();

            this.menuBar.remove(helpMenu);

        } else if (ika.utils.Sys.isWindows()) {
            this.menuBar.remove(macHelpMenu);
        }
    }

    /**
     * Customize the passed defaultRenderParams.
     * This implementation does not alter the passed parameters.
     */
    @Override
    public RenderParams getRenderParams(RenderParams defaultRenderParams) {
        return defaultRenderParams;
    }

    @Override
    protected boolean saveDocumentWindow(String filePath) {

        try {
            GeoGrid grid = getNamedGrid("Result");
            ESRIASCIIGridExporter.export(grid, filePath);
            return true;
        } catch (Exception exc) {
            String msg = "The shaded relief image could not be saved.";
            ika.utils.ErrorDialog.showErrorDialog(msg, errTitle(), exc, this);
        }
        return false;

    }

    /**
     * Return data that can be stored in an external file.
     * @return The document content.
     */
    @Override
    protected byte[] getDocumentData() {
        try {
            return ika.utils.Serializer.serialize(mapComponent.getGeoSet(), false);
        } catch (java.io.IOException exc) {
            exc.printStackTrace();
            return null;
        }
    }

    /**
     * Restore the document content from a passed GeoMap.
     * @param data The document content.
     */
    @Override
    protected void setDocumentData(byte[] data) throws Exception {
        GeoMap geoMap = (GeoMap) ika.utils.Serializer.deserialize(data, false);
        this.mapComponent.setGeoSet(geoMap);
    }

    /**
     * Update all menus of this window.
     */
    private void updateAllMenus() {
        // Only update the menu items if this frame is visible. 
        // This avoids menu items being enabled that will be detached from 
        // this frame and will be attached to a utility frame or will be 
        // displayed when no frame is visible on Mac OS X.
        if (this.isVisible()) {
            this.updateFileMenu();
            this.updateEditMenu();
            this.updateViewMenu();
            MainWindow.updateWindowMenu(this.windowMenu, this);
        }
    }

    /**
     * Update the enabled/disabled state of the items in the file menu.
     */
    private void updateFileMenu() {
        this.closeMenuItem.setEnabled(true);
        this.saveMenuItem.setEnabled(gridFilter.getGrid() != null);
        this.saveShadedReliefMenuItem.setEnabled(gridFilter.getGrid() != null);
    }

    private static Component getVisibleCard(JPanel panel) {
        
        for (Component comp : panel.getComponents()) {
            if (comp.isVisible() == true) {
                return comp;
            }
        }
        return null;
    }
    
    /**
     * Update the enabled/disabled state of the items in the edit menu.
     */
    private void updateEditMenu() {

        boolean mapHasSelectedObj = mapComponent.hasSelectedGeoObjects();

        // undo and redo menu items are handled by the Undo manager.

        deleteMenuItem.setEnabled(mapHasSelectedObj);
        copyMenuItem.setEnabled(mapHasSelectedObj);
        cutMenuItem.setEnabled(mapHasSelectedObj);
        pasteMenuItem.setEnabled(GeoTransferable.isClipboardFull());
        filterMenuItem.setEnabled(getVisibleCard(centerPanel) == filterCanceledPanel);
        gridInfoMenuItem.setEnabled(gridFilter.getGrid() != null);
        scaleTerrainMenuItem.setEnabled(gridFilter.getGrid() != null);
        voidValuesMenuItem.setEnabled(gridFilter.getGrid() != null);
        
        deferredFilteringCheckBoxMenuItem.setSelected(deferredFiltering);
    }

    /**
     * Update the enabled/disabled state of the items in the view menu.
     */
    private void updateViewMenu() {
        this.zoomInMenuItem.setEnabled(true);
        this.zoomOutMenuItem.setEnabled(true);
        this.showAllMenuItem.setEnabled(true);
        this.showPageCheckBoxMenuItem.setEnabled(true);
    }

    /**
     * Import data from a URL and add it to the map.
     */
    private void importURL(java.net.URL url) {
        try {
            GeoImporter importer = GeoImporter.findGeoImporter(url);
            // importer.setProgressIndicator(new SwingProgressIndicator(this, "Load Data", null, true));
            importer.read(url, new MapDataReceiver(this.mapComponent),
                    GeoImporter.SAME_THREAD); // !!! ??? NEW_THREAD);
        } catch (Exception exc) {
            exc.printStackTrace();
            ika.utils.ErrorDialog.showErrorDialog("Could not load the data. "
                    + "The format may not be supported.",
                    errTitle());
        }
    }

    /**
     * Export the map to a file. The user is asked to select a file path to a 
     * new file.
     * @param exporter The GeoSetExporter to export the map.
     */
    private void export(ika.geoexport.GeoSetExporter exporter) {
        final double mapScale = this.mapComponent.getScaleFactor();
        exporter.setDisplayMapScale(mapScale);
        PageFormat pageFormat = this.mapComponent.getPageFormat();
        GeoSet geoSet = this.mapComponent.getImportExportGeoSet();
        GeoExportGUI.export(exporter, geoSet, this.getTitle(), this, pageFormat, true);
    }

    /**
     * Export the map to a raster image file. The user is asked to select a
     * file path to a new file.
     * @param format The format of the raster image, e.g. "jpg" or "png".
     */
    private void exportToRasterImage(String format) {
        ika.geoexport.RasterImageExporter exporter = new ika.geoexport.RasterImageExporter();
        exporter.setFormat(format);
        this.export(exporter);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        toolBarButtonGroup = new javax.swing.ButtonGroup();
        viewPopupMenu = new javax.swing.JPopupMenu();
        viewFinalCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        viewOriginalCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        viewLowlandsCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        viewMountainsCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        viewCombinationCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        viewMenuButtonGroup = new javax.swing.ButtonGroup();
        basicAdvancedButtonGroup = new javax.swing.ButtonGroup();
        scaleTerrainPanel = new javax.swing.JPanel();
        javax.swing.JLabel jLabel10 = new javax.swing.JLabel();
        scaleTerrainFormattedTextField = new javax.swing.JFormattedTextField();
        javax.swing.JLabel jLabel11 = new javax.swing.JLabel();
        voidValuesPanel = new javax.swing.JPanel();
        javax.swing.JLabel jLabel13 = new javax.swing.JLabel();
        voidValuesFormattedTextField = new javax.swing.JFormattedTextField();
        topPanel = new javax.swing.JPanel();
        topLeftPanel = new javax.swing.JPanel();
        navigationToolBar = new javax.swing.JToolBar();
        zoomInToggleButton = new javax.swing.JToggleButton();
        zoomOutToggleButton = new javax.swing.JToggleButton();
        handToggleButton = new javax.swing.JToggleButton();
        distanceToggleButton = new javax.swing.JToggleButton();
        jSeparator7 = new javax.swing.JToolBar.Separator();
        showAllButton = new javax.swing.JButton();
        infoToolBar = new javax.swing.JToolBar();
        infoPanel = new javax.swing.JPanel();
        scaleLabel = new ika.gui.ScaleLabel();
        coordinateInfoPanel = new ika.gui.CoordinateInfoPanel();
        viewMenuButton = new ika.gui.MenuToggleButton();
        javax.swing.JPanel leftPanel = new javax.swing.JPanel();
        controlPanel = new javax.swing.JPanel();
        levelOfDetailsPanel = new javax.swing.JPanel();
        javax.swing.JPanel lodPanel = new javax.swing.JPanel();
        meanFilterLoopsSlider = new javax.swing.JSlider();
        javax.swing.JLabel jLabel21 = new javax.swing.JLabel();
        basicAdvancedSelectionPanel = new javax.swing.JPanel();
        basicToggleButton = new javax.swing.JToggleButton();
        basicToggleButton.putClientProperty("JButton.buttonType", "segmentedRoundRect");
        basicToggleButton.putClientProperty("JButton.segmentPosition", "first");
        advancedToggleButton = new javax.swing.JToggleButton();
        advancedToggleButton.putClientProperty("JButton.buttonType", "segmentedRoundRect");
        advancedToggleButton.putClientProperty("JButton.segmentPosition", "last");
        basicAdvancedPanel = new javax.swing.JPanel();
        advancedPanel = new javax.swing.JPanel();
        javax.swing.JTabbedPane jTabbedPane1 = new javax.swing.JTabbedPane();
        valleysPanel = new ika.gui.TransparentMacPanel();
        javax.swing.JLabel jLabel2 = new javax.swing.JLabel();
        valleysMeanFilterLoopsSlider = new javax.swing.JSlider();
        javax.swing.JLabel jLabel8 = new javax.swing.JLabel();
        valleysExaggerationSlider = new javax.swing.JSlider();
        javax.swing.JLabel jLabel12 = new javax.swing.JLabel();
        valleysCurvatureThresholdSlider = new javax.swing.JSlider();
        javax.swing.JSeparator jSeparator3 = new javax.swing.JSeparator();
        ridgesPanel = new ika.gui.TransparentMacPanel();
        javax.swing.JLabel jLabel1 = new javax.swing.JLabel();
        ridgesMeanFilterLoopsSlider = new javax.swing.JSlider();
        javax.swing.JSeparator jSeparator2 = new javax.swing.JSeparator();
        javax.swing.JLabel jLabel7 = new javax.swing.JLabel();
        ridgesExaggerationSlider = new javax.swing.JSlider();
        javax.swing.JLabel jLabel17 = new javax.swing.JLabel();
        planCurvatureWeightSlider = new javax.swing.JSlider();
        combinationPanel = new javax.swing.JPanel();
        javax.swing.JLabel jLabel9 = new javax.swing.JLabel();
        combinationSlopeThresholdSlider = new javax.swing.JSlider();
        javax.swing.JTextArea jTextArea1 = new javax.swing.JTextArea();
        basicPanel = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        scaleSlider = new javax.swing.JSlider();
        javax.swing.JLabel jLabel3 = new javax.swing.JLabel();
        centerPanel = new javax.swing.JPanel();
        mapComponent = new ika.gui.MapComponent();
        filterCanceledPanel = new javax.swing.JPanel();
        filteringStatusLabel = new javax.swing.JLabel();
        filterButton = new javax.swing.JButton();
        menuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        newMenuItem = new javax.swing.JMenuItem();
        openMenuItem = new javax.swing.JMenuItem();
        openRecentMenu = rdm.createOpenRecentMenu();
        javax.swing.JSeparator jSeparator5 = new javax.swing.JSeparator();
        closeMenuItem = new javax.swing.JMenuItem();
        saveMenuItem = new javax.swing.JMenuItem();
        javax.swing.JPopupMenu.Separator jSeparator1 = new javax.swing.JPopupMenu.Separator();
        saveShadedReliefMenuItem = new javax.swing.JMenuItem();
        exitMenuSeparator = new javax.swing.JSeparator();
        exitMenuItem = new javax.swing.JMenuItem();
        editMenu = new javax.swing.JMenu();
        cutMenuItem = new javax.swing.JMenuItem();
        copyMenuItem = new javax.swing.JMenuItem();
        pasteMenuItem = new javax.swing.JMenuItem();
        deleteMenuItem = new javax.swing.JMenuItem();
        jSeparator8 = new javax.swing.JPopupMenu.Separator();
        filterMenuItem = new javax.swing.JMenuItem();
        deferredFilteringCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        javax.swing.JPopupMenu.Separator jSeparator6 = new javax.swing.JPopupMenu.Separator();
        voidValuesMenuItem = new javax.swing.JMenuItem();
        scaleTerrainMenuItem = new javax.swing.JMenuItem();
        javax.swing.JPopupMenu.Separator jSeparator4 = new javax.swing.JPopupMenu.Separator();
        gridInfoMenuItem = new javax.swing.JMenuItem();
        viewMenu = new javax.swing.JMenu();
        zoomInMenuItem = new javax.swing.JMenuItem();
        zoomOutMenuItem = new javax.swing.JMenuItem();
        javax.swing.JSeparator jSeparator12 = new javax.swing.JSeparator();
        showAllMenuItem = new javax.swing.JMenuItem();
        windowMenu = new javax.swing.JMenu();
        minimizeMenuItem = new javax.swing.JMenuItem();
        zoomMenuItem = new javax.swing.JMenuItem();
        windowSeparator = new javax.swing.JSeparator();
        helpMenu = new javax.swing.JMenu();
        onlineManualMenuItem = new javax.swing.JMenuItem();
        infoMenuItem = new javax.swing.JMenuItem();
        systemInfoMenuItem = new javax.swing.JMenuItem();
        macHelpMenu = new javax.swing.JMenu();
        infoMenuItem1 = new javax.swing.JMenuItem();
        systemInfoMenuItem1 = new javax.swing.JMenuItem();
        onlineManualMenuItem1 = new javax.swing.JMenuItem();
        debugMenu = new javax.swing.JMenu();
        memoryMenuItem = new javax.swing.JMenuItem();
        redrawMenuItem = new javax.swing.JMenuItem();
        exportMenu = new javax.swing.JMenu();
        showPageCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();

        viewMenuButtonGroup.add(viewFinalCheckBoxMenuItem);
        viewFinalCheckBoxMenuItem.setSelected(true);
        viewFinalCheckBoxMenuItem.setText("Filtered Terrain");
        viewFinalCheckBoxMenuItem.setToolTipText("");
        viewFinalCheckBoxMenuItem.setName("Result"); // NOI18N
        viewFinalCheckBoxMenuItem.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                viewMenuChanged(evt);
            }
        });
        viewFinalCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewMenuActionHandler(evt);
            }
        });
        viewPopupMenu.add(viewFinalCheckBoxMenuItem);

        viewMenuButtonGroup.add(viewOriginalCheckBoxMenuItem);
        viewOriginalCheckBoxMenuItem.setText("Original Terrain");
        viewOriginalCheckBoxMenuItem.setToolTipText("");
        viewOriginalCheckBoxMenuItem.setName("Original Grid"); // NOI18N
        viewOriginalCheckBoxMenuItem.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                viewMenuChanged(evt);
            }
        });
        viewOriginalCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewMenuActionHandler(evt);
            }
        });
        viewPopupMenu.add(viewOriginalCheckBoxMenuItem);

        viewMenuButtonGroup.add(viewLowlandsCheckBoxMenuItem);
        viewLowlandsCheckBoxMenuItem.setText("Lowlands");
        viewLowlandsCheckBoxMenuItem.setToolTipText("");
        viewLowlandsCheckBoxMenuItem.setName("Valleys"); // NOI18N
        viewLowlandsCheckBoxMenuItem.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                viewMenuChanged(evt);
            }
        });
        viewLowlandsCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewMenuActionHandler(evt);
            }
        });
        viewPopupMenu.add(viewLowlandsCheckBoxMenuItem);

        viewMenuButtonGroup.add(viewMountainsCheckBoxMenuItem);
        viewMountainsCheckBoxMenuItem.setText("Mountains");
        viewMountainsCheckBoxMenuItem.setName("Ridges"); // NOI18N
        viewMountainsCheckBoxMenuItem.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                viewMenuChanged(evt);
            }
        });
        viewMountainsCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewMenuActionHandler(evt);
            }
        });
        viewPopupMenu.add(viewMountainsCheckBoxMenuItem);

        viewMenuButtonGroup.add(viewCombinationCheckBoxMenuItem);
        viewCombinationCheckBoxMenuItem.setText("Combination Mask");
        viewCombinationCheckBoxMenuItem.setToolTipText("");
        viewCombinationCheckBoxMenuItem.setName("Combination Weight"); // NOI18N
        viewCombinationCheckBoxMenuItem.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                viewMenuChanged(evt);
            }
        });
        viewCombinationCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewMenuActionHandler(evt);
            }
        });
        viewPopupMenu.add(viewCombinationCheckBoxMenuItem);

        scaleTerrainPanel.setLayout(new java.awt.GridBagLayout());

        jLabel10.setText("Scale Factor:");
        scaleTerrainPanel.add(jLabel10, new java.awt.GridBagConstraints());

        scaleTerrainFormattedTextField.setPreferredSize(new java.awt.Dimension(200, 28));
        scaleTerrainFormattedTextField.setValue(new Float(1));
        scaleTerrainPanel.add(scaleTerrainFormattedTextField, new java.awt.GridBagConstraints());

        jLabel11.setFont(jLabel11.getFont().deriveFont(jLabel11.getFont().getSize()-2f));
        jLabel11.setText("All values in the terrain grid are scaled by this factor.");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        scaleTerrainPanel.add(jLabel11, gridBagConstraints);

        voidValuesPanel.setLayout(new java.awt.GridBagLayout());

        jLabel13.setText("Change Void Values to");
        voidValuesPanel.add(jLabel13, new java.awt.GridBagConstraints());

        voidValuesFormattedTextField.setPreferredSize(new java.awt.Dimension(200, 28));
        voidValuesFormattedTextField.setValue(new Float(0));
        voidValuesPanel.add(voidValuesFormattedTextField, new java.awt.GridBagConstraints());

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeWindow(evt);
            }
        });

        topPanel.setLayout(new javax.swing.BoxLayout(topPanel, javax.swing.BoxLayout.LINE_AXIS));

        topLeftPanel.setFocusCycleRoot(true);
        topLeftPanel.setPreferredSize(new java.awt.Dimension(500, 50));
        topLeftPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 20, 2));

        toolBarButtonGroup.add(zoomInToggleButton);
        zoomInToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/ZoomIn16x16.gif"))); // NOI18N
        zoomInToggleButton.setSelected(true);
        zoomInToggleButton.setToolTipText("Zoom In");
        zoomInToggleButton.setPreferredSize(new java.awt.Dimension(24, 24));
        zoomInToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomInToggleButtonActionPerformed(evt);
            }
        });
        navigationToolBar.add(zoomInToggleButton);

        toolBarButtonGroup.add(zoomOutToggleButton);
        zoomOutToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/ZoomOut16x16.gif"))); // NOI18N
        zoomOutToggleButton.setToolTipText("Zoom Out");
        zoomOutToggleButton.setPreferredSize(new java.awt.Dimension(24, 24));
        zoomOutToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomOutToggleButtonActionPerformed(evt);
            }
        });
        navigationToolBar.add(zoomOutToggleButton);

        toolBarButtonGroup.add(handToggleButton);
        handToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/Hand16x16.gif"))); // NOI18N
        handToggleButton.setToolTipText("Pan");
        handToggleButton.setPreferredSize(new java.awt.Dimension(24, 24));
        handToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                handToggleButtonActionPerformed(evt);
            }
        });
        navigationToolBar.add(handToggleButton);

        toolBarButtonGroup.add(distanceToggleButton);
        distanceToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/Ruler16x16.gif"))); // NOI18N
        distanceToggleButton.setToolTipText("Measure Distance and Angle");
        distanceToggleButton.setPreferredSize(new java.awt.Dimension(24, 24));
        distanceToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                distanceToggleButtonActionPerformed(evt);
            }
        });
        navigationToolBar.add(distanceToggleButton);
        navigationToolBar.add(jSeparator7);

        showAllButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/ShowAll20x14.png"))); // NOI18N
        showAllButton.setToolTipText("Show All");
        showAllButton.setBorderPainted(false);
        showAllButton.setPreferredSize(new java.awt.Dimension(32, 24));
        showAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showAllButtonActionPerformed(evt);
            }
        });
        navigationToolBar.add(showAllButton);

        topLeftPanel.add(navigationToolBar);

        infoPanel.setLayout(new java.awt.GridBagLayout());

        scaleLabel.setMaximumSize(new java.awt.Dimension(150, 12));
        scaleLabel.setMinimumSize(new java.awt.Dimension(50, 20));
        scaleLabel.setPreferredSize(new java.awt.Dimension(80, 12));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        infoPanel.add(scaleLabel, gridBagConstraints);

        coordinateInfoPanel.setForeground(new java.awt.Color(128, 128, 128));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        infoPanel.add(coordinateInfoPanel, gridBagConstraints);

        infoToolBar.add(infoPanel);

        topLeftPanel.add(infoToolBar);

        viewMenuButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ika/icons/view.png"))); // NOI18N
        viewMenuButton.setText("Filtered Terrain");
        viewMenuButton.setToolTipText("Select the data displayed.");
        viewMenuButton.setBorderPainted(false);
        viewMenuButton.setContentAreaFilled(false);
        viewMenuButton.setFont(new java.awt.Font("SansSerif", 0, 13));
        viewMenuButton.setPopupMenu(viewPopupMenu);
        topLeftPanel.add(viewMenuButton);

        topPanel.add(topLeftPanel);

        getContentPane().add(topPanel, java.awt.BorderLayout.NORTH);

        controlPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(15, 1, 1, 1));
        controlPanel.setLayout(new java.awt.GridBagLayout());

        lodPanel.setLayout(new java.awt.GridBagLayout());

        meanFilterLoopsSlider.setMajorTickSpacing(10);
        meanFilterLoopsSlider.setMaximum(50);
        meanFilterLoopsSlider.setMinorTickSpacing(5);
        meanFilterLoopsSlider.setPaintLabels(true);
        meanFilterLoopsSlider.setPaintTicks(true);
        meanFilterLoopsSlider.setValue(10);
        meanFilterLoopsSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                filterSliderStateChanged(evt);
                lodSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 30, 0, 0);
        lodPanel.add(meanFilterLoopsSlider, gridBagConstraints);
        {
            ika.gui.SliderUtils.setMinMaxSliderLabels(meanFilterLoopsSlider, new String[]{"Detailed", "Smooth"});
            ika.gui.SliderUtils.reapplyFontSize(meanFilterLoopsSlider);
        }

        jLabel21.setText("Level of Detail");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        lodPanel.add(jLabel21, gridBagConstraints);

        levelOfDetailsPanel.add(lodPanel);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        controlPanel.add(levelOfDetailsPanel, gridBagConstraints);

        basicAdvancedSelectionPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 5));

        basicAdvancedButtonGroup.add(basicToggleButton);
        basicToggleButton.setSelected(true);
        basicToggleButton.setText("Basic");
        basicToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                basicToggleButtonActionPerformed(evt);
            }
        });
        basicAdvancedSelectionPanel.add(basicToggleButton);

        basicAdvancedButtonGroup.add(advancedToggleButton);
        advancedToggleButton.setText("Advanced");
        advancedToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                advancedToggleButtonActionPerformed(evt);
            }
        });
        basicAdvancedSelectionPanel.add(advancedToggleButton);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 0, 0);
        controlPanel.add(basicAdvancedSelectionPanel, gridBagConstraints);

        basicAdvancedPanel.setLayout(new java.awt.CardLayout());

        advancedPanel.setLayout(new java.awt.GridBagLayout());

        valleysPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        valleysPanel.setLayout(new java.awt.GridBagLayout());

        jLabel2.setText("Valleys Removal");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        valleysPanel.add(jLabel2, gridBagConstraints);

        valleysMeanFilterLoopsSlider.setMajorTickSpacing(15);
        valleysMeanFilterLoopsSlider.setMaximum(20);
        valleysMeanFilterLoopsSlider.setMinorTickSpacing(5);
        valleysMeanFilterLoopsSlider.setPaintTicks(true);
        valleysMeanFilterLoopsSlider.setValue(5);
        valleysMeanFilterLoopsSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                filterSliderStateChanged(evt);
                valleysSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        valleysPanel.add(valleysMeanFilterLoopsSlider, gridBagConstraints);

        jLabel8.setText("Valley Depth");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        valleysPanel.add(jLabel8, gridBagConstraints);

        valleysExaggerationSlider.setMajorTickSpacing(100);
        valleysExaggerationSlider.setMaximum(500);
        valleysExaggerationSlider.setMinorTickSpacing(50);
        valleysExaggerationSlider.setPaintTicks(true);
        valleysExaggerationSlider.setValue(40);
        valleysExaggerationSlider.setInverted(true);
        valleysExaggerationSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                filterSliderStateChanged(evt);
                valleysSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        valleysPanel.add(valleysExaggerationSlider, gridBagConstraints);

        jLabel12.setText("Valley Width");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 0, 0);
        valleysPanel.add(jLabel12, gridBagConstraints);

        valleysCurvatureThresholdSlider.setMajorTickSpacing(50);
        valleysCurvatureThresholdSlider.setMinorTickSpacing(10);
        valleysCurvatureThresholdSlider.setPaintTicks(true);
        valleysCurvatureThresholdSlider.setInverted(true);
        valleysCurvatureThresholdSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                filterSliderStateChanged(evt);
                valleysSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        valleysPanel.add(valleysCurvatureThresholdSlider, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 20, 0);
        valleysPanel.add(jSeparator3, gridBagConstraints);

        jTabbedPane1.addTab("Lowlands", valleysPanel);

        ridgesPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        ridgesPanel.setLayout(new java.awt.GridBagLayout());

        jLabel1.setText("Ridges Removal");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        ridgesPanel.add(jLabel1, gridBagConstraints);

        ridgesMeanFilterLoopsSlider.setMajorTickSpacing(15);
        ridgesMeanFilterLoopsSlider.setMaximum(20);
        ridgesMeanFilterLoopsSlider.setMinorTickSpacing(5);
        ridgesMeanFilterLoopsSlider.setPaintTicks(true);
        ridgesMeanFilterLoopsSlider.setValue(5);
        ridgesMeanFilterLoopsSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                filterSliderStateChanged(evt);
                ridgesSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        ridgesPanel.add(ridgesMeanFilterLoopsSlider, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 20, 0);
        ridgesPanel.add(jSeparator2, gridBagConstraints);

        jLabel7.setText("Ridges Exaggeration");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        ridgesPanel.add(jLabel7, gridBagConstraints);

        ridgesExaggerationSlider.setMajorTickSpacing(500);
        ridgesExaggerationSlider.setMaximum(2000);
        ridgesExaggerationSlider.setMinimum(110);
        ridgesExaggerationSlider.setMinorTickSpacing(250);
        ridgesExaggerationSlider.setPaintTicks(true);
        ridgesExaggerationSlider.setValue(500);
        ridgesExaggerationSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                filterSliderStateChanged(evt);
                ridgesSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        ridgesPanel.add(ridgesExaggerationSlider, gridBagConstraints);

        jLabel17.setText("Ridges Sharpness");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 0, 0);
        ridgesPanel.add(jLabel17, gridBagConstraints);

        planCurvatureWeightSlider.setMajorTickSpacing(50);
        planCurvatureWeightSlider.setMaximum(150);
        planCurvatureWeightSlider.setMinorTickSpacing(25);
        planCurvatureWeightSlider.setPaintTicks(true);
        planCurvatureWeightSlider.setValue(150);
        planCurvatureWeightSlider.setValueIsAdjusting(true);
        planCurvatureWeightSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                filterSliderStateChanged(evt);
                ridgesSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        ridgesPanel.add(planCurvatureWeightSlider, gridBagConstraints);

        jTabbedPane1.addTab("Mountains", ridgesPanel);

        jTabbedPane1.setSelectedIndex(1);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 20, 0);
        advancedPanel.add(jTabbedPane1, gridBagConstraints);

        combinationPanel.setLayout(new java.awt.GridBagLayout());

        jLabel9.setText("Lowland-Mountain Mixer");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        combinationPanel.add(jLabel9, gridBagConstraints);

        combinationSlopeThresholdSlider.setMajorTickSpacing(15);
        combinationSlopeThresholdSlider.setMaximum(45);
        combinationSlopeThresholdSlider.setMinorTickSpacing(5);
        combinationSlopeThresholdSlider.setPaintTicks(true);
        combinationSlopeThresholdSlider.setToolTipText("Adjust the combination mask: the lowland shading is used in black areas, the mountain shading in white areas.");
        combinationSlopeThresholdSlider.setValue(15);
        combinationSlopeThresholdSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                combinationSlopeThresholdSliderStateChanged(evt);
                filterSliderStateChanged(evt);
            }
        });
        {
            //Create the label table
            Hashtable labelTable = new Hashtable();
            labelTable.put( new Integer( 0 ), new JLabel("Lowlands") );
            labelTable.put( new Integer( 45 ), new JLabel("Mountains") );
            combinationSlopeThresholdSlider.setLabelTable( labelTable );
        }
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        combinationPanel.add(combinationSlopeThresholdSlider, gridBagConstraints);

        jTextArea1.setColumns(20);
        jTextArea1.setFont(new java.awt.Font("SansSerif", 0, 11));
        jTextArea1.setLineWrap(true);
        jTextArea1.setRows(5);
        jTextArea1.setText("Adjust the combination mask: the lowland shading is used where the combination mask is black, and the mountain shading is used where the combination mask is white.");
        jTextArea1.setWrapStyleWord(true);
        jTextArea1.setOpaque(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        combinationPanel.add(jTextArea1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        advancedPanel.add(combinationPanel, gridBagConstraints);

        basicAdvancedPanel.add(advancedPanel, "advancedCard");

        basicPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 5, 20));

        jPanel1.setLayout(new java.awt.GridBagLayout());

        scaleSlider.setMajorTickSpacing(50);
        scaleSlider.setPaintLabels(true);
        scaleSlider.setPaintTicks(true);
        scaleSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                scaleSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 30, 0, 0);
        jPanel1.add(scaleSlider, gridBagConstraints);
        {
            ika.gui.SliderUtils.setMinMaxSliderLabels(scaleSlider, new String[]{"Large Scale", "Small Scale"});
            ika.gui.SliderUtils.reapplyFontSize(scaleSlider);
        }

        jLabel3.setText("Scale");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel1.add(jLabel3, gridBagConstraints);

        basicPanel.add(jPanel1);

        basicAdvancedPanel.add(basicPanel, "basicCard");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        controlPanel.add(basicAdvancedPanel, gridBagConstraints);
        CardLayout cl = (CardLayout) (basicAdvancedPanel.getLayout());
        cl.show(basicAdvancedPanel, "basicCard");

        leftPanel.add(controlPanel);

        getContentPane().add(leftPanel, java.awt.BorderLayout.WEST);

        centerPanel.setLayout(new java.awt.CardLayout());

        mapComponent.setBackground(new java.awt.Color(255, 255, 255));
        mapComponent.setInfoString("");
        mapComponent.setMinimumSize(new java.awt.Dimension(100, 200));
        mapComponent.setPreferredSize(new java.awt.Dimension(200, 200));
        centerPanel.add(mapComponent, "map");

        filterCanceledPanel.setBackground(new java.awt.Color(255, 255, 255));
        filterCanceledPanel.setLayout(new java.awt.GridBagLayout());

        filteringStatusLabel.setForeground(java.awt.Color.GRAY);
        filteringStatusLabel.setText("Filtering has been canceled.");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(12, 0, 0, 0);
        filterCanceledPanel.add(filteringStatusLabel, gridBagConstraints);

        filterButton.setText("Filter");
        filterButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filterButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        filterCanceledPanel.add(filterButton, gridBagConstraints);

        centerPanel.add(filterCanceledPanel, "filterButton");

        getContentPane().add(centerPanel, java.awt.BorderLayout.CENTER);

        fileMenu.setText("File");

        newMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N,
            java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    newMenuItem.setText("New Window");
    newMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            newMenuItemActionPerformed(evt);
        }
    });
    fileMenu.add(newMenuItem);

    openMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
openMenuItem.setText("Open Terrain…");
openMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        openMenuItemActionPerformed(evt);
    }
    });
    fileMenu.add(openMenuItem);

    openRecentMenu.setText("Open Recent Terrain Model");
    fileMenu.add(openRecentMenu);
    fileMenu.add(jSeparator5);

    closeMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
closeMenuItem.setText("Close");
closeMenuItem.setEnabled(false);
closeMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        closeMenuItemActionPerformed(evt);
    }
    });
    fileMenu.add(closeMenuItem);

    saveMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
saveMenuItem.setText("Save Filtered Terrain…");
saveMenuItem.setEnabled(false);
saveMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        saveMenuItemActionPerformed(evt);
    }
    });
    fileMenu.add(saveMenuItem);
    fileMenu.add(jSeparator1);

    saveShadedReliefMenuItem.setText("Save Shaded Relief…");
    saveShadedReliefMenuItem.setEnabled(false);
    saveShadedReliefMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            saveShadedReliefMenuItemActionPerformed(evt);
        }
    });
    fileMenu.add(saveShadedReliefMenuItem);
    fileMenu.add(exitMenuSeparator);

    exitMenuItem.setText("Exit");
    exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            exitMenuItemActionPerformed(evt);
        }
    });
    fileMenu.add(exitMenuItem);

    menuBar.add(fileMenu);

    editMenu.setText("Edit");

    cutMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
cutMenuItem.setText("Cut");
cutMenuItem.setEnabled(false);
cutMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        cutMenuItemActionPerformed(evt);
    }
    });
    editMenu.add(cutMenuItem);

    copyMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
copyMenuItem.setText("Copy");
copyMenuItem.setEnabled(false);
copyMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        copyMenuItemActionPerformed(evt);
    }
    });
    editMenu.add(copyMenuItem);

    pasteMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
pasteMenuItem.setText("Paste");
pasteMenuItem.setEnabled(false);
pasteMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        pasteMenuItemActionPerformed(evt);
    }
    });
    editMenu.add(pasteMenuItem);

    deleteMenuItem.setText("Delete");
    deleteMenuItem.setEnabled(false);
    deleteMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            deleteMenuItemActionPerformed(evt);
        }
    });
    editMenu.add(deleteMenuItem);
    editMenu.add(jSeparator8);

    filterMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
filterMenuItem.setText("Filter");
filterMenuItem.setEnabled(false);
filterMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        filterMenuItemActionPerformed(evt);
    }
    });
    editMenu.add(filterMenuItem);

    deferredFilteringCheckBoxMenuItem.setText("Deferred Filtering");
    deferredFilteringCheckBoxMenuItem.setToolTipText("Recommended for large terrains that take long to filter.");
    deferredFilteringCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            deferredFilteringCheckBoxMenuItemActionPerformed(evt);
        }
    });
    editMenu.add(deferredFilteringCheckBoxMenuItem);
    editMenu.add(jSeparator6);

    voidValuesMenuItem.setText("Change Void Values…");
    voidValuesMenuItem.setEnabled(false);
    voidValuesMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            voidValuesMenuItemActionPerformed(evt);
        }
    });
    editMenu.add(voidValuesMenuItem);

    scaleTerrainMenuItem.setText("Scale Terrain…");
    scaleTerrainMenuItem.setEnabled(false);
    scaleTerrainMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            scaleTerrainMenuItemActionPerformed(evt);
        }
    });
    editMenu.add(scaleTerrainMenuItem);
    editMenu.add(jSeparator4);

    gridInfoMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_I,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
gridInfoMenuItem.setText("Terrain Model Info…");
gridInfoMenuItem.setEnabled(false);
gridInfoMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        gridInfoMenuItemActionPerformed(evt);
    }
    });
    editMenu.add(gridInfoMenuItem);

    menuBar.add(editMenu);

    viewMenu.setText("View");

    zoomInMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ADD,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
zoomInMenuItem.setText("Zoom In");
zoomInMenuItem.setEnabled(false);
zoomInMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        zoomInMenuItemActionPerformed(evt);
    }
    });
    viewMenu.add(zoomInMenuItem);

    zoomOutMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SUBTRACT,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
zoomOutMenuItem.setText("Zoom Out");
zoomOutMenuItem.setEnabled(false);
zoomOutMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        zoomOutMenuItemActionPerformed(evt);
    }
    });
    viewMenu.add(zoomOutMenuItem);
    viewMenu.add(jSeparator12);

    showAllMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_NUMPAD0,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
showAllMenuItem.setText("Show All");
showAllMenuItem.setEnabled(false);
showAllMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        showAllMenuItemActionPerformed(evt);
    }
    });
    viewMenu.add(showAllMenuItem);

    menuBar.add(viewMenu);

    windowMenu.setText("Window");
    windowMenu.setName("WindowsMenu"); // NOI18N

    minimizeMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M,
        java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
minimizeMenuItem.setText("Minimize");
minimizeMenuItem.setEnabled(false);
minimizeMenuItem.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        minimizeMenuItemActionPerformed(evt);
    }
    });
    windowMenu.add(minimizeMenuItem);

    zoomMenuItem.setText("Zoom");
    zoomMenuItem.setEnabled(false);
    zoomMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            zoomMenuItemActionPerformed(evt);
        }
    });
    windowMenu.add(zoomMenuItem);
    windowMenu.add(windowSeparator);

    menuBar.add(windowMenu);

    helpMenu.setText("?");

    onlineManualMenuItem.setText("Terrain Sculptor Online Manual");
    onlineManualMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            onlineManualMenuItemActionPerformed(evt);
        }
    });
    helpMenu.add(onlineManualMenuItem);

    infoMenuItem.setText("Info");
    infoMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            infoMenuItemActionPerformed(evt);
        }
    });
    helpMenu.add(infoMenuItem);

    systemInfoMenuItem.setText("System Info");
    systemInfoMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            systemInfoMenuItemActionPerformed(evt);
        }
    });
    helpMenu.add(systemInfoMenuItem);

    menuBar.add(helpMenu);

    macHelpMenu.setText("Help");

    infoMenuItem1.setText("Info");
    infoMenuItem1.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            infoMenuItem1ActionPerformed(evt);
        }
    });
    macHelpMenu.add(infoMenuItem1);

    systemInfoMenuItem1.setText("System Info");
    systemInfoMenuItem1.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            systemInfoMenuItem1ActionPerformed(evt);
        }
    });
    macHelpMenu.add(systemInfoMenuItem1);

    onlineManualMenuItem1.setText("Terrain Sculptor Online Manual");
    onlineManualMenuItem1.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            onlineManualMenuItemActionPerformed(evt);
        }
    });
    macHelpMenu.add(onlineManualMenuItem1);

    menuBar.add(macHelpMenu);

    debugMenu.setText("Debug");

    memoryMenuItem.setText("Memory Usage…");
    memoryMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            memoryMenuItemActionPerformed(evt);
        }
    });
    debugMenu.add(memoryMenuItem);

    redrawMenuItem.setText("Redraw Map");
    redrawMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            redrawMenuItemActionPerformed(evt);
        }
    });
    debugMenu.add(redrawMenuItem);

    exportMenu.setText("Export");
    debugMenu.add(exportMenu);

    showPageCheckBoxMenuItem.setText("Show Map Outline");
    showPageCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            showPageCheckBoxMenuItemActionPerformed(evt);
        }
    });
    debugMenu.add(showPageCheckBoxMenuItem);

    menuBar.add(debugMenu);
    menuBar.remove(debugMenu);

    setJMenuBar(menuBar);

    pack();
    }// </editor-fold>//GEN-END:initComponents

    private void newMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newMenuItemActionPerformed
        MainWindow.newDocumentWindow();
    }//GEN-LAST:event_newMenuItemActionPerformed

    private void openMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openMenuItemActionPerformed
        importGrid();
    }//GEN-LAST:event_openMenuItemActionPerformed

    private void closeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeMenuItemActionPerformed
        closeDocumentWindow();
    }//GEN-LAST:event_closeMenuItemActionPerformed

    private void saveMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveMenuItemActionPerformed
        saveDocumentWindow();
    }//GEN-LAST:event_saveMenuItemActionPerformed

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        // this handler is not used on Macintosh. On Windows and other platforms
        // only this window is closed.
        closeDocumentWindow();
    }//GEN-LAST:event_exitMenuItemActionPerformed

    private void cutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cutMenuItemActionPerformed
        // create a GeoSet with copies of the currently selected GeoObjects
        GeoSet copyGeoSet = new GeoSet();
        this.mapComponent.getGeoSet().cloneIfSelected(copyGeoSet);
        if (copyGeoSet.getNumberOfChildren() == 0) {
            return;
        }

        // put the selected GeoObjects onto the clipboard
        GeoTransferable.storeInSystemClipboard(copyGeoSet);

        // delete the selected GeoObjects
        this.mapComponent.removeSelectedGeoObjects();

        this.mapComponent.addUndo("Cut");
    }//GEN-LAST:event_cutMenuItemActionPerformed

    private void copyMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyMenuItemActionPerformed

        // create a GeoSet with copies of the currently selected GeoObjects
        GeoSet copyGeoSet = new GeoSet();
        this.mapComponent.getGeoSet().cloneIfSelected(copyGeoSet);
        if (copyGeoSet.getNumberOfChildren() == 0) {
            return;
        }
        copyGeoSet = (GeoSet) copyGeoSet.getGeoObject(0);

        // put the selected objects onto the clipboard
        GeoTransferable.storeInSystemClipboard(copyGeoSet);

        // update the "Paste" command in the edit menu
        this.updateEditMenu();
    }//GEN-LAST:event_copyMenuItemActionPerformed

    private void pasteMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pasteMenuItemActionPerformed
        GeoSet geoSet = GeoTransferable.retreiveSystemClipboardCopy();
        if (geoSet == null) {
            return;
        }

        // make all pasted objects visible to show the result of the paste action.
        geoSet.setVisible(true);

        this.mapComponent.deselectAllAndAddChildren(geoSet);
        this.mapComponent.addUndo("Paste");

        // make sure the pasted objects are visible in the map
        if (this.mapComponent.isObjectVisibleOnMap(geoSet) == false) {
            this.mapComponent.showAll();
        }
    }//GEN-LAST:event_pasteMenuItemActionPerformed

    private void deleteMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteMenuItemActionPerformed
        this.mapComponent.removeSelectedGeoObjects();
        this.mapComponent.addUndo("Delete");
    }//GEN-LAST:event_deleteMenuItemActionPerformed

    private void zoomInMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomInMenuItemActionPerformed
        this.mapComponent.zoomIn();
    }//GEN-LAST:event_zoomInMenuItemActionPerformed

    private void zoomOutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomOutMenuItemActionPerformed
        this.mapComponent.zoomOut();
    }//GEN-LAST:event_zoomOutMenuItemActionPerformed

    private void showAllMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showAllMenuItemActionPerformed
        mapComponent.showAll();
    }//GEN-LAST:event_showAllMenuItemActionPerformed

    private void minimizeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_minimizeMenuItemActionPerformed
        this.setState(Frame.ICONIFIED);
    }//GEN-LAST:event_minimizeMenuItemActionPerformed

    private void zoomMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomMenuItemActionPerformed
        if ((this.getExtendedState() & Frame.MAXIMIZED_BOTH) != MAXIMIZED_BOTH) {
            this.setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            this.setExtendedState(JFrame.NORMAL);
        }
        this.validate();
    }//GEN-LAST:event_zoomMenuItemActionPerformed

    private void infoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_infoMenuItemActionPerformed
        ika.gui.ProgramInfoPanel.showApplicationInfo();
    }//GEN-LAST:event_infoMenuItemActionPerformed

    private void memoryMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_memoryMenuItemActionPerformed
        MemoryUsagePanel.showMemoryUsagePanel();
    }//GEN-LAST:event_memoryMenuItemActionPerformed

    private void redrawMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_redrawMenuItemActionPerformed
        this.mapComponent.repaint();
    }//GEN-LAST:event_redrawMenuItemActionPerformed

    private void zoomInToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomInToggleButtonActionPerformed
        this.mapComponent.setMapTool(new ZoomInTool(this.mapComponent));
    }//GEN-LAST:event_zoomInToggleButtonActionPerformed

    private void zoomOutToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomOutToggleButtonActionPerformed
        this.mapComponent.setMapTool(new ZoomOutTool(this.mapComponent));
    }//GEN-LAST:event_zoomOutToggleButtonActionPerformed

    private void handToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_handToggleButtonActionPerformed
        this.mapComponent.setMapTool(new PanTool(this.mapComponent));
    }//GEN-LAST:event_handToggleButtonActionPerformed

    private void distanceToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_distanceToggleButtonActionPerformed
        MeasureTool tool = new MeasureTool(this.mapComponent);
        tool.addMeasureToolListener(this.coordinateInfoPanel);
        this.mapComponent.setMapTool(tool);
    }//GEN-LAST:event_distanceToggleButtonActionPerformed

    private void showAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showAllButtonActionPerformed
        this.mapComponent.showAll();
    }//GEN-LAST:event_showAllButtonActionPerformed

    private void closeWindow(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_closeWindow
        this.closeDocumentWindow();
    }//GEN-LAST:event_closeWindow

    private void readGUI() {
        gridFilter.setGridFilterLoops(meanFilterLoopsSlider.getValue());
        gridFilter.setValleysMeanFilterLoops(valleysMeanFilterLoopsSlider.getValue());
        gridFilter.setValleysExaggeration(valleysExaggerationSlider.getValue() / 100f);
        gridFilter.setValleysCurvatureUpperLimit(valleysCurvatureThresholdSlider.getValue() / 100f);
        gridFilter.setRidgesMeanFilterLoops(ridgesMeanFilterLoopsSlider.getValue());
        gridFilter.setRidgesExaggeration(ridgesExaggerationSlider.getValue() / 100f);
        gridFilter.setCombinationSlopeThreshold(combinationSlopeThresholdSlider.getValue());
        gridFilter.setRidgesPlancurvatureWeight(planCurvatureWeightSlider.getValue() / 100f);
    }

private void saveShadedReliefMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveShadedReliefMenuItemActionPerformed

    try {
        GeoGrid grid = getNamedGrid("Result");
        if (grid == null) {
            return;
        }
        GeoImage geoImage = new GridShadeOperator().operateToImage(grid);
        String path = FileUtils.askFile(null, "Save Shaded Relief", "shading.png", false, "png");
        if (path != null) {
            BufferedImage image = geoImage.getBufferedImage();
            ImageIO.write(image, "png", new File(path));
        }
    } catch (IOException ex) {
        String msg = "The shaded relief image could not be saved.";
        ika.utils.ErrorDialog.showErrorDialog(msg, errTitle(), ex, this);
    }
}//GEN-LAST:event_saveShadedReliefMenuItemActionPerformed

private void viewMenuActionHandler(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewMenuActionHandler
    if (adjustingGUI) {
        return;
    }
    JCheckBoxMenuItem mi = (JCheckBoxMenuItem) (evt.getSource());
    if (mi.isSelected()) {
        resetMap(displayGrids != null && displayGrids.size() > 0);
    }
}//GEN-LAST:event_viewMenuActionHandler

    /**
     * Interpolate value for scale slider between medium and small scale.
     * @param y50 y value at 50 (medium scale)
     * @param y100 y value at 100 (small scale)
     * @param x Between 50 and 100
     * @return
     */
    private float smallScaleInterpolation(float y50, float y100, float x) {
        float m = (y100 - y50) / 50f;
        float c = y100 - m * 100f;
        return m * x + c;
    }

    /**
     * Interpolate value for scale slider between large and medium scale.
     * @param y0 y value at 0 (large scale)
     * @param y50 y value at 50 (medium scale)
     * @param x x value between 0 and 50
     * @return
     */
    private float largeScaleInterpolation(float y0, float y50, float x) {
        float m = (y50 - y0) / 50f;
        return m * x + y0;
    }

    private void interpolateBasicParameters(int w) {

        final int ridgesMeanFilterLoops;
        final float ridgesExaggeration;
        final float ridgesPlanCurvatureWeight;
        final int valleysMeanFilterLoops;
        final float valleysExaggeration;
        final float valleysCurvatureUpperLimit;
        final float combinationSlopeThreshold;

        if (w < 50) {
            ridgesMeanFilterLoops = Math.round(largeScaleInterpolation(2.f, 5f, w));
            ridgesExaggeration = 1.25f;
            ridgesPlanCurvatureWeight = 1.5f;
            valleysMeanFilterLoops = Math.round(largeScaleInterpolation(2.f, 10f, w));
            valleysExaggeration = largeScaleInterpolation(0.7f, 0.4f, w);
            valleysCurvatureUpperLimit = largeScaleInterpolation(0.8f, 0.5f, w);
            combinationSlopeThreshold = largeScaleInterpolation(25f, 15f, w);
        } else {
            ridgesMeanFilterLoops = Math.round(smallScaleInterpolation(5f, 10f, w));
            ridgesExaggeration = smallScaleInterpolation(1.25f, 20f, w);
            ridgesPlanCurvatureWeight = smallScaleInterpolation(1.5f, 3f, w);
            valleysMeanFilterLoops = Math.round(smallScaleInterpolation(10f, 30f, w));
            valleysExaggeration = smallScaleInterpolation(0.4f, 0f, w);
            valleysCurvatureUpperLimit = smallScaleInterpolation(0.5f, 0.05f, w);
            combinationSlopeThreshold = smallScaleInterpolation(15f, 2.5f, w);
        }

        gridFilter.setValleysMeanFilterLoops(valleysMeanFilterLoops);
        gridFilter.setRidgesMeanFilterLoops(ridgesMeanFilterLoops);
        gridFilter.setRidgesExaggeration(ridgesExaggeration);
        gridFilter.setRidgesPlancurvatureWeight(ridgesPlanCurvatureWeight);
        gridFilter.setValleysExaggeration(valleysExaggeration);
        gridFilter.setValleysCurvatureUpperLimit(valleysCurvatureUpperLimit);
        gridFilter.setCombinationSlopeThreshold(combinationSlopeThreshold);

        // write advanced GUI
        writeGUI();
        readGUIAndFilter(false);
    }

private void scaleSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_scaleSliderStateChanged
    if (!scaleSlider.getValueIsAdjusting()) {
        interpolateBasicParameters(scaleSlider.getValue());
    }
}//GEN-LAST:event_scaleSliderStateChanged

private void basicToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_basicToggleButtonActionPerformed
    CardLayout cl = (CardLayout) (basicAdvancedPanel.getLayout());
    cl.show(basicAdvancedPanel, "basicCard");

    // apply current settings of scale slider
    interpolateBasicParameters(scaleSlider.getValue());
}//GEN-LAST:event_basicToggleButtonActionPerformed

private void advancedToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_advancedToggleButtonActionPerformed
    CardLayout cl = (CardLayout) (basicAdvancedPanel.getLayout());
    cl.show(basicAdvancedPanel, "advancedCard");
}//GEN-LAST:event_advancedToggleButtonActionPerformed

private void systemInfoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_systemInfoMenuItemActionPerformed
    new SystemInfo(this);
}//GEN-LAST:event_systemInfoMenuItemActionPerformed

private void onlineManualMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_onlineManualMenuItemActionPerformed
    Properties props =
            ika.utils.PropertiesLoader.loadProperties("ika.app.Application.properties");
    String url = props.getProperty("HelpWebPage");
    if (Desktop.isDesktopSupported()) {
        try {
            URI uri = new URI(url);
            Desktop.getDesktop().browse(uri);
        } catch (URISyntaxException ex) {
            Logger.getLogger(TerrainSculptorWindow.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(TerrainSculptorWindow.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}//GEN-LAST:event_onlineManualMenuItemActionPerformed

private void systemInfoMenuItem1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_systemInfoMenuItem1ActionPerformed
    new SystemInfo(this);
}//GEN-LAST:event_systemInfoMenuItem1ActionPerformed

private void gridInfoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gridInfoMenuItemActionPerformed
    showGridInfo();
}//GEN-LAST:event_gridInfoMenuItemActionPerformed

    private void noTerrainErrorMessage(JFrame frame) {
        String msg = "<html>There is no terrain model loaded.<br>"
                + "Please first open one.";
        JOptionPane.showMessageDialog(frame, msg, errTitle(), JOptionPane.INFORMATION_MESSAGE);
    }

private void scaleTerrainMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scaleTerrainMenuItemActionPerformed

    GeoGrid grid = gridFilter.getGrid();
    if (grid == null) {
        noTerrainErrorMessage(this);
        return;
    }

    int option = JOptionPane.showOptionDialog(this,
            scaleTerrainPanel,
            "Scale Terrain",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null, null, null);
    if (option != JOptionPane.OK_OPTION) {
        return;
    }

    try {
        scaleTerrainFormattedTextField.commitEdit();
        java.lang.Number f = (java.lang.Number) (scaleTerrainFormattedTextField.getValue());
        float scale = f.floatValue();
        GridScaleOperator op = new GridScaleOperator(scale);
        GeoGrid scaledGrid = op.operate(grid);
        gridFilter.setGrid(scaledGrid);
        readGUIAndFilter(false);
    } catch (Exception exc) {
        String msg = "An error occured while scaling the terrain.";
        ErrorDialog.showErrorDialog(msg, errTitle(), exc, this);
    }
}//GEN-LAST:event_scaleTerrainMenuItemActionPerformed

private void voidValuesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_voidValuesMenuItemActionPerformed
    GeoGrid grid = gridFilter.getGrid();
    if (grid == null) {
        noTerrainErrorMessage(this);
        return;
    }

    int option = JOptionPane.showOptionDialog(this,
            voidValuesPanel,
            "Change Void Values",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null, null, null);
    if (option != JOptionPane.OK_OPTION) {
        return;
    }

    try {
        voidValuesFormattedTextField.commitEdit();
        java.lang.Number f = (java.lang.Number) (voidValuesFormattedTextField.getValue());
        GridChangeVoidOperator op = new GridChangeVoidOperator(f.floatValue());
        gridFilter.setGrid(op.operate(grid));
        readGUIAndFilter(false);
    } catch (Exception exc) {
        ErrorDialog.showErrorDialog("An error occured while chaning void values.",
                errTitle(), exc, this);
    }
}//GEN-LAST:event_voidValuesMenuItemActionPerformed

private void readGUIAndFilter(boolean manuallyTriggeredFiltering) {
    
    boolean filter = manuallyTriggeredFiltering || !deferredFiltering;
    CardLayout cl = (CardLayout)(centerPanel.getLayout());
    cl.show(centerPanel, filter ? "map" : "filterButton");
    filteringStatusLabel.setText("Filtering is deferred.");
    updateEditMenu(); // enable Filter command
    if (!filter) {
        return;
    }

    if (gridFilter.getGrid() == null) {
        showNoTerrainMessage();
    } else {
        readGUI();
        
        if (adjustingGUI) {
            return;
        }

        // don't show the original grid, as no graphical response would be 
        // visible after a change to the GUI
        try {
            adjustingGUI = true;
            if (viewOriginalCheckBoxMenuItem.isSelected()) {
                viewFinalCheckBoxMenuItem.setSelected(true);
            }
        } finally {
            adjustingGUI = false;
        }

        try {
            filter();
        } catch (Throwable ex) {
            String exmsg = ex.getMessage();
            if (exmsg != null && exmsg.contains("user canceled")) {
                return;
            }
            String msg = "An error occured";
            ErrorDialog.showErrorDialog(msg, errTitle(), ex, TerrainSculptorWindow.this);
        }
    }
}

private void filterSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_filterSliderStateChanged
    JSlider slider = (JSlider) evt.getSource();
    if (!slider.getValueIsAdjusting() && !adjustingGUI) {
        readGUIAndFilter(false);
    }
}//GEN-LAST:event_filterSliderStateChanged

private void showPageCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showPageCheckBoxMenuItemActionPerformed
    boolean show = this.showPageCheckBoxMenuItem.isSelected();
    mapComponent.getPageFormat().setVisible(show);
}//GEN-LAST:event_showPageCheckBoxMenuItemActionPerformed

private void filterButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filterButtonActionPerformed
    readGUIAndFilter(true);
}//GEN-LAST:event_filterButtonActionPerformed

private void combinationSlopeThresholdSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_combinationSlopeThresholdSliderStateChanged
    if (((JSlider) evt.getSource()).getValueIsAdjusting()) {
        return;
    }
    if (!viewCombinationCheckBoxMenuItem.isSelected()
            && !viewFinalCheckBoxMenuItem.isSelected()) {
        viewFinalCheckBoxMenuItem.setSelected(true);
    }
}//GEN-LAST:event_combinationSlopeThresholdSliderStateChanged

private void viewMenuChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_viewMenuChanged
    if (evt.getStateChange() == ItemEvent.SELECTED) {
        JCheckBoxMenuItem menuItem = (JCheckBoxMenuItem) evt.getSource();
        viewMenuButton.setText(menuItem.getText());
    }    
}//GEN-LAST:event_viewMenuChanged

private void ridgesSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_ridgesSliderStateChanged
    if (((JSlider) evt.getSource()).getValueIsAdjusting()) {
        return;
    }            
    if (!viewMountainsCheckBoxMenuItem.isSelected()
            && !viewFinalCheckBoxMenuItem.isSelected()) {
        viewFinalCheckBoxMenuItem.setSelected(true);
    }
}//GEN-LAST:event_ridgesSliderStateChanged

private void valleysSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_valleysSliderStateChanged
    if (((JSlider) evt.getSource()).getValueIsAdjusting()) {
        return;
    }            
    if (!viewLowlandsCheckBoxMenuItem.isSelected()
            && !viewFinalCheckBoxMenuItem.isSelected()) {
        viewFinalCheckBoxMenuItem.setSelected(true);
    }
}//GEN-LAST:event_valleysSliderStateChanged

private void lodSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_lodSliderStateChanged
    if (((JSlider) evt.getSource()).getValueIsAdjusting()) {
        return;
    }            
    if (viewOriginalCheckBoxMenuItem.isSelected()){
        viewFinalCheckBoxMenuItem.setSelected(true);
    }
}//GEN-LAST:event_lodSliderStateChanged

private void infoMenuItem1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_infoMenuItem1ActionPerformed
    ika.gui.ProgramInfoPanel.showApplicationInfo();
}//GEN-LAST:event_infoMenuItem1ActionPerformed

private void filterMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filterMenuItemActionPerformed
    readGUIAndFilter(true);
}//GEN-LAST:event_filterMenuItemActionPerformed

private void deferredFilteringCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deferredFilteringCheckBoxMenuItemActionPerformed
    deferredFiltering = deferredFilteringCheckBoxMenuItem.isSelected();
}//GEN-LAST:event_deferredFilteringCheckBoxMenuItemActionPerformed

    /**
     * A property change listener for the root pane that adjusts the enabled
     * state of the save menu depending on the windowModified property attached
     * to the root pane.
     */
    private void windowModifiedPropertyChange(java.beans.PropertyChangeEvent evt) {

        // only treat changes to the windowModified property
        if (!"windowModified".equals(evt.getPropertyName())) {
            return;
        }

        // retrieve the value of the windowModified property
        Boolean windowModified = null;
        if (saveMenuItem != null && this.getRootPane() != null) {
            windowModified =
                    (Boolean) this.getRootPane().getClientProperty("windowModified");
        }

        // enable or disable the saveMenu accordingly
        if (windowModified != null) {
            this.saveMenuItem.setEnabled(windowModified.booleanValue());
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel advancedPanel;
    private javax.swing.JToggleButton advancedToggleButton;
    private javax.swing.ButtonGroup basicAdvancedButtonGroup;
    private javax.swing.JPanel basicAdvancedPanel;
    private javax.swing.JPanel basicAdvancedSelectionPanel;
    private javax.swing.JPanel basicPanel;
    private javax.swing.JToggleButton basicToggleButton;
    private javax.swing.JPanel centerPanel;
    private javax.swing.JMenuItem closeMenuItem;
    private javax.swing.JPanel combinationPanel;
    private javax.swing.JSlider combinationSlopeThresholdSlider;
    private javax.swing.JPanel controlPanel;
    private ika.gui.CoordinateInfoPanel coordinateInfoPanel;
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JMenuItem cutMenuItem;
    private javax.swing.JMenu debugMenu;
    private javax.swing.JCheckBoxMenuItem deferredFilteringCheckBoxMenuItem;
    private javax.swing.JMenuItem deleteMenuItem;
    private javax.swing.JToggleButton distanceToggleButton;
    private javax.swing.JMenu editMenu;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JSeparator exitMenuSeparator;
    private javax.swing.JMenu exportMenu;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JButton filterButton;
    private javax.swing.JPanel filterCanceledPanel;
    private javax.swing.JMenuItem filterMenuItem;
    private javax.swing.JLabel filteringStatusLabel;
    private javax.swing.JMenuItem gridInfoMenuItem;
    private javax.swing.JToggleButton handToggleButton;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JMenuItem infoMenuItem;
    private javax.swing.JMenuItem infoMenuItem1;
    private javax.swing.JPanel infoPanel;
    private javax.swing.JToolBar infoToolBar;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JToolBar.Separator jSeparator7;
    private javax.swing.JPopupMenu.Separator jSeparator8;
    private javax.swing.JPanel levelOfDetailsPanel;
    private javax.swing.JMenu macHelpMenu;
    private ika.gui.MapComponent mapComponent;
    private javax.swing.JSlider meanFilterLoopsSlider;
    private javax.swing.JMenuItem memoryMenuItem;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JMenuItem minimizeMenuItem;
    private javax.swing.JToolBar navigationToolBar;
    private javax.swing.JMenuItem newMenuItem;
    private javax.swing.JMenuItem onlineManualMenuItem;
    private javax.swing.JMenuItem onlineManualMenuItem1;
    private javax.swing.JMenuItem openMenuItem;
    private javax.swing.JMenu openRecentMenu;
    private javax.swing.JMenuItem pasteMenuItem;
    private javax.swing.JSlider planCurvatureWeightSlider;
    private javax.swing.JMenuItem redrawMenuItem;
    private javax.swing.JSlider ridgesExaggerationSlider;
    private javax.swing.JSlider ridgesMeanFilterLoopsSlider;
    private javax.swing.JPanel ridgesPanel;
    private javax.swing.JMenuItem saveMenuItem;
    private javax.swing.JMenuItem saveShadedReliefMenuItem;
    private ika.gui.ScaleLabel scaleLabel;
    private javax.swing.JSlider scaleSlider;
    private javax.swing.JFormattedTextField scaleTerrainFormattedTextField;
    private javax.swing.JMenuItem scaleTerrainMenuItem;
    private javax.swing.JPanel scaleTerrainPanel;
    private javax.swing.JButton showAllButton;
    private javax.swing.JMenuItem showAllMenuItem;
    private javax.swing.JCheckBoxMenuItem showPageCheckBoxMenuItem;
    private javax.swing.JMenuItem systemInfoMenuItem;
    private javax.swing.JMenuItem systemInfoMenuItem1;
    private javax.swing.ButtonGroup toolBarButtonGroup;
    private javax.swing.JPanel topLeftPanel;
    private javax.swing.JPanel topPanel;
    private javax.swing.JSlider valleysCurvatureThresholdSlider;
    private javax.swing.JSlider valleysExaggerationSlider;
    private javax.swing.JSlider valleysMeanFilterLoopsSlider;
    private javax.swing.JPanel valleysPanel;
    private javax.swing.JCheckBoxMenuItem viewCombinationCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem viewFinalCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem viewLowlandsCheckBoxMenuItem;
    private javax.swing.JMenu viewMenu;
    private ika.gui.MenuToggleButton viewMenuButton;
    private javax.swing.ButtonGroup viewMenuButtonGroup;
    private javax.swing.JCheckBoxMenuItem viewMountainsCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem viewOriginalCheckBoxMenuItem;
    private javax.swing.JPopupMenu viewPopupMenu;
    private javax.swing.JFormattedTextField voidValuesFormattedTextField;
    private javax.swing.JMenuItem voidValuesMenuItem;
    private javax.swing.JPanel voidValuesPanel;
    private javax.swing.JMenu windowMenu;
    private javax.swing.JSeparator windowSeparator;
    private javax.swing.JMenuItem zoomInMenuItem;
    private javax.swing.JToggleButton zoomInToggleButton;
    private javax.swing.JMenuItem zoomMenuItem;
    private javax.swing.JMenuItem zoomOutMenuItem;
    private javax.swing.JToggleButton zoomOutToggleButton;
    // End of variables declaration//GEN-END:variables
}
