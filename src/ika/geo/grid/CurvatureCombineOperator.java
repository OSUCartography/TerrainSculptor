package ika.geo.grid;

import ika.geo.GeoGrid;

/**
 *
 * @author jenny
 */
public class CurvatureCombineOperator extends ThreadedGridOperator {

    private float scale;
    private GeoGrid maxCurv;
    private float maxCurvMax, maxCurvMin;
    
    @Override
    public String getName() {
        return "Plan Curvature Combination";
    }

    @Override
    public void operate(GeoGrid planCurv, GeoGrid dst, int startRow, int endRow) {

        float[][] planCurvGrid = planCurv.getGrid();
        float[][] maxCurvGrid = maxCurv.getGrid();
        float[][] dstGrid = dst.getGrid();
        final int nCols = planCurv.getCols();

        float[] planCurvMinMax = planCurv.getMinMax();
        final float scalePos = scale / Math.abs(planCurvMinMax[1]);
        final float scaleNeg = scale / Math.abs(planCurvMinMax[0]);
        final float maxCurvRange = maxCurvMax - maxCurvMin;
        final float f = 1f / maxCurvRange;
        
        for (int row = startRow; row < endRow; ++row) {
            float[] planCurvRow = planCurvGrid[row];
            float[] maxCurvRow = maxCurvGrid[row];
            float[] dstRow = dstGrid[row];
            for (int col = 0; col < nCols; ++col) {
                
                // scale plan curvature to -scale..+scale, 0 remains 0.
                final float pc = planCurvRow[col];
                final float scaledPlanCurv = pc > 0 ? pc * scalePos : pc * scaleNeg;
                
                // scale maximum curvature to range 0..1
                final float scaledMaxCurv = (maxCurvRow[col] - maxCurvMin) * f;
                
                dstRow[col] = scaledPlanCurv + scaledMaxCurv;
            }
        }

    }

    /**
     * @param scale the scale to set
     */
    public void setScale(float scale) {
        this.scale = scale;
    }

    /**
     * @param maxCurv the maxCurv to set
     */
    public void setMaxCurv(GeoGrid maxCurv) {
        this.maxCurv = maxCurv;
        float[] maxCurvMinMax = maxCurv.getMinMax();
        this.maxCurvMin = maxCurvMinMax[0];
        this.maxCurvMax = maxCurvMinMax[1];
    }

}
