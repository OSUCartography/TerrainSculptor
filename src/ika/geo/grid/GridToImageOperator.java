/*
 * GridToImageOperator.java
 *
 * Created on February 3, 2006, 11:40 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ika.geo.grid;

import ika.geo.*;
import java.awt.image.*;
import java.util.Arrays;

/**
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
public class GridToImageOperator implements GridOperator{
    
    /** Creates a new instance of GridToImageOperator */
    public GridToImageOperator() {
    }
    
    public String getName() {
        return "Grid To Image";
    }
    
    public GeoImage operate(GeoGrid geoGrid) {
        
        if (geoGrid == null) {
            throw new IllegalArgumentException();
        }
        
        final int nrows = geoGrid.getRows();
        final int ncols = geoGrid.getCols();
        if (nrows == 0 || ncols == 0) {
            return null;
        }
        
        final float[] minMax = geoGrid.getMinMax();
        final float min = minMax[0];
        final float oldRange = minMax[1] - minMax[0];
        
        float[][] srcGrid = geoGrid.getGrid();
        byte[] pixels = new byte [nrows * ncols];
        
        int px = 0;
        if (oldRange != 0) {
            for (int row = 0; row < nrows; ++row) {
                float[] srcRow = srcGrid[row];
                for (int col = 0; col < ncols; ++col) {
                    pixels[px++] = (byte)((srcRow[col] - min) / oldRange * 255.f);
                }
            }
        } else {
            // make white image
            Arrays.fill(pixels, (byte)255);
        }
        
        // Create a BufferedImage of the gray values in bytes.
        BufferedImage bufferedImage = new BufferedImage(ncols, nrows,
                BufferedImage.TYPE_BYTE_GRAY);
        
        // Get the writable raster so that data can be changed.
        WritableRaster wr = bufferedImage.getRaster();
        
        // write the byte data to the raster
        wr.setDataElements(0, 0, ncols, nrows, pixels);
        
        final double cellSize = geoGrid.getCellSize();
        final double gridWest = geoGrid.getWest() - cellSize / 2.;
        final double gridNorth = geoGrid.getNorth() - cellSize / 2.;
        return new GeoImage(bufferedImage, gridWest, gridNorth, cellSize);
    }
    
}
