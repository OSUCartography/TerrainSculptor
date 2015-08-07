package ika.geo.grid;

import ika.geo.GeoGrid;

/**
 * A weighted and scaled combination of a grid with itself:
 * out = in * w * scale + in * (1 - w)
 * out = in * (w * scale + 1 - w)
 * out = in * (w * (scale - 1) + 1)
 * @author jenny
 */
public class WeightedScaleOperator extends ThreadedGridOperator {

    private GeoGrid weightGrid;
    private float scale;

    public WeightedScaleOperator(GeoGrid weightGrid, float scale) {
        this.weightGrid = weightGrid;
        this.scale = scale;
    }
    
    @Override
    public String getName() {
        return "Scaled Combination";
    }

    @Override
    public void operate(GeoGrid src, GeoGrid dst, int startRow, int endRow) {

        final float scale_1 = scale - 1f;
        final int nCols = src.getCols();
        for (int row = startRow; row < endRow; ++row) {
            float[] srcRow = src.getGrid()[row];
            float[] wRow = weightGrid.getGrid()[row];
            float[] dstRow = dst.getGrid()[row];
            for (int col = 0; col < nCols; ++col) {
                dstRow[col] = srcRow[col] * (wRow[col] * scale_1 + 1f);
            }
        }
    }

}
