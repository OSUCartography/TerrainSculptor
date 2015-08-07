package ika.geo.grid;

import ika.geo.GeoGrid;

/**
 * Clip all values larger than a threshold and then scale the values by a 
 * constant factor.
 * @author jenny
 */
public class ClipScaleOperator extends ThreadedGridOperator {

    private float thresholdValue = 0.f;
    private float scale = 1;

    /** Creates a new instance of GridThresholdOperator */
    public ClipScaleOperator() {
    }

    @Override
    public String getName() {
        return "Clip Scale";
    }

    @Override
    public void operate(GeoGrid src, GeoGrid dst, int startRow, int endRow) {

        float[][] srcGrid = src.getGrid();
        float[][] dstGrid = dst.getGrid();
        final int ncols = src.getCols();
        float max = thresholdValue * scale;

        for (int row = startRow; row < endRow; ++row) {
            float[] srcRow = srcGrid[row];
            float[] dstRow = dstGrid[row];
            for (int col = 0; col < ncols; ++col) {
                final float v = srcRow[col];
                dstRow[col] = v > thresholdValue ? max : v * scale;
            }
        }
    }

    public float getThresholdValue() {
        return thresholdValue;
    }

    public void setThresholdValue(float minValue) {
        this.thresholdValue = minValue;
    }

    /**
     * @return the scale
     */
    public float getScale() {
        return scale;
    }

    /**
     * @param scale the scale to set
     */
    public void setScale(float scale) {
        this.scale = scale;
    }
}
