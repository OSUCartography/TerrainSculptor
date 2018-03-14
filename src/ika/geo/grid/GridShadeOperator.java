/*
 * GridShadeOperator.java
 *
 * Created on January 28, 2006, 6:28 PM
 *
 */
package ika.geo.grid;

import ika.geo.*;
import ika.utils.ImageUtils;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */
public class GridShadeOperator implements GridOperator {

    private final double zenithRad = Math.toRadians(45);
    private final double azimuthRad = Math.toRadians(315);

    /**
     * Creates a new instance of GridShadeOperator
     */
    public GridShadeOperator() {
    }

    @Override
    public String getName() {
        return "Grid Shade";
    }

    @Override
    public GeoGrid operate(GeoGrid geoGrid) {
        throw new UnsupportedOperationException();
    }

    private int[] imageBuffer(BufferedImage img) {
        return ((DataBufferInt) (img.getRaster().getDataBuffer())).getData();
    }
    
    public ika.geo.GeoImage operateToImage(GeoGrid grid) {        

        final int imgCols = Math.max(2, grid.getCols() - 2);
        final int imgRows = Math.max(2, grid.getRows() - 2);
        if (imgCols <= 2 || imgRows <= 2) {
            return null;
        }

        BufferedImage image = new BufferedImage(imgCols, imgRows, BufferedImage.TYPE_INT_ARGB);
        final int[] imageBuffer = imageBuffer(image);
                    
        // create a light vector
        double sinz = Math.sin(zenithRad);
        double lx = Math.sin(azimuthRad) * sinz;
        double ly = Math.cos(azimuthRad) * sinz;
        double lz = Math.cos(zenithRad);
        
        // the cell size to calculate the horizontal components of vectors
        double cellSize = grid.getProjectedCellSize();
        double nz = 2 * cellSize;
        double nz_sq = nz * nz;

        int nCols = grid.getCols();
        int nRows = grid.getRows();

        int grayID = 0;
        for (int row = 1; row < nRows - 1; row++) {
            for (int col = 1; col < nCols - 1; col++) {
                final double w = grid.getValue(col - 1, row);
                final double e = grid.getValue(col + 1, row);
                final double s = grid.getValue(col, row + 1);
                final double n = grid.getValue(col, row - 1);
                final double dx = e - w;
                final double dy = n - s;

                // normal vector on vertex
                final double nx = -dx;
                final double ny = -dy;

                // compute the dot product of the normal and the light vector. This
                // results in a value between -1 (surface faces directly away from
                // light) and 1 (surface faces directly toward light)
                double normalLength = Math.sqrt(nx * nx + ny * ny + nz_sq);
                double dotProduct = (nx * lx + ny * ly + nz * lz) / normalLength;

                // scale dot product from [-1, +1] to a gray value in [0, 255]
                double gray = (dotProduct + 1) / 2 * 255;
                final int g = (int) gray;
                imageBuffer[grayID++] = g | (g << 8) | (g << 16) | 0xFF000000;
            }
        }

        GeoImage newImage = new GeoImage(image, imgCols, imgRows, cellSize);
        newImage.setWest(grid.getWest());
        newImage.setNorth(grid.getNorth());
        return newImage;
    }

}
