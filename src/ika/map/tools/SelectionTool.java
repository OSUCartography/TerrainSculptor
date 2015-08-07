/*
 * SelectionTool.java
 *
 * Created on April 7, 2005, 7:21 PM
 */

package ika.map.tools;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import ika.geo.*;
import ika.gui.MapComponent;

/**
 * SelectionTool - a tool to select GeoObjects by mouse clicks and mouse drags.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class SelectionTool extends RectangleTool implements CombinableTool {
    
    /**
     * Tolerance for selection of objects by mouse clicks.
     */
    protected static final int CLICK_PIXEL_TOLERANCE = 2;
    
    /**
     * Create a new instance.
     * @param mapComponent The MapComponent for which this MapTool provides its services.
     */
    public SelectionTool(MapComponent mapComponent) {
        super(mapComponent);
    }
    
    /**
     * A drag ends, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void endDrag(Point2D.Double point, MouseEvent evt) {
        Rectangle2D.Double rect = this.getRectangle();
        super.endDrag(point, evt);
        
        final boolean selectionChanged =
                this.mapComponent.selectByRectangle(rect, evt.isShiftDown());
        
        setDefaultCursor();
    }
    
    /**
     * The mouse was clicked, while this MapTool was the active one.
     * @param point The location of the mouse in world coordinates.
     * @param evt The original event.
     */
    public void mouseClicked(Point2D.Double point, MouseEvent evt) {
        //super.mouseClicked(point, evt);
        
        // try selecting objects close to the mouse click.
        final boolean selectionChanged = mapComponent.selectByPoint(
                point, evt.isShiftDown(), 
                SelectionTool.CLICK_PIXEL_TOLERANCE);
    }
    
    protected String getCursorName() {
        return "selectionarrow";
    }

    public boolean adjustCursor(Point2D.Double point) {
        this.setDefaultCursor();
        return true;
    }
}
