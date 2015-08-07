package ika.geo.grid;

import ika.geo.GeoGrid;
import ika.gui.ProgressIndicator;
import java.util.ArrayList;

public class TerrainSculptorFilter {

    public static final String RESULT_NAME = "Result";
    public static final String ORIGINAL_NAME = "Original Grid";
    public static final String RIDGES_NAME = "Ridges";
    public static final String VALLEYS_NAME = "Valleys";
    public static final String COMBINATION_WEIGHT_NAME = "Combination Weight";

    
    /**
     * Instances of each work package.
     */
    private final LODWorkPackage lodWP;
    private final FlatMaskWorkPackage flatMaskWP;
    private final RidgesWeightWorkPackage ridgesWeightWP;
    private final RidgesExaggerationWorkPackage ridgesExaggerationWP;
    private final ValleysExaggerationWorkPackage valleysExaggerationWP;
    private final ValleysWeightWorkPackage valleysWeightWP;
    private final FinalCombinationWorkPackage finalCombinationWP;
    /**
     * All work packages in an array for convenient iteration.
     */
    private ArrayList<WorkPackage> workPackages;
    /**
     * The original grid, which may contain void (NaN) values. If no void values
     * are in the grid, this.originalGrid is identical to this.detailedGrid
     */
    private GeoGrid originalGrid;
    /**
     * The grid to filter without void (NaN) values. It is the same object as
     * this.originalGrid if the original does not contain void values.
     */
    private GeoGrid detailedGrid;
    /**
     * A grid with slope values derived from this.detailedGrid
     */
    private GeoGrid slopeGrid;
    /**
     * The Gaussian low-pass filter is an instance variable, as it cashes an 
     * internal grid to reduce the number of memory allocations.
     */
    private GridGaussLowPassOperator lowPassOp;
    
    private int gridFilterLoops = 10;
    private float ridgesPlancurvatureWeight = 1.5f;
    private int ridgesMeanFilterLoops = 5;
    private int ridgesMeanFilterLoopsForCombination = 1;
    private int valleysMeanFilterLoops = 5;
    private float ridgesCurvatureUpperLimit = 1.0f;
    private float valleysCurvatureUpperLimit = 0.5f;
    private float ridgesExaggeration = 1.25f;
    private float valleysExaggeration = 0.4f;
    private float combinationSlopeThreshold = 15; // in degrees
    
    /**
     * An object pool of GeoGrids that caches initialized grids to minimize the 
     * cost of initializing grids. All grids have the same size and cell size.
     */
    private class GridPool {

        /**
         * An array holding all available grids.
         */
        private ArrayList<GeoGrid> pool = new ArrayList(10);
        /**
         * The horizontal size of the grids in this pool.
         */
        private int cols;
        /**
         * The vertical size of the grids in this pool.
         */
        private int rows;
        /**
         * The cell size of the grids in this pool
         */
        private double cellSize;
        /**
         * The western border of the grid.
         */
        private double west;
        /**
         * The norther border of the grid.
         */
        private double north;

        /**
         * Set the dimension, cell size and spatial extension of the grids in 
         * this pool. The values of the passed grid are used. If the dimensions
         * differ from previous settings, the pool is reset, i.e. all grids in 
         * the pool are discarded.
         * @param cols
         * @param rows
         * @param cellSize 
         */
        public void init(GeoGrid grid) {

            if (grid.getCols() < 3 || grid.getRows() < 3 || grid.getCellSize() < 0) {
                throw new IllegalArgumentException("grid with illegal dimensions");
            }

            if (grid.getCols() != this.cols
                    || grid.getRows() != this.rows
                    || grid.getCellSize() != this.cellSize
                    || grid.getWest() != this.west
                    || grid.getNorth() != north) {
                pool.clear();
                this.cols = grid.getCols();
                this.rows = grid.getRows();
                this.cellSize = grid.getCellSize();
                this.west = grid.getWest();
                this.north = grid.getNorth();
            }

        }

        /**
         * The client calls acquire() to obtain a GeoGrid. Values in the returned
         * grid may have been initialized previously to any values.
         * @return 
         */
        public GeoGrid acquire() {

            if (pool.isEmpty()) {
                GeoGrid grid = new GeoGrid(cols, rows, cellSize);
                grid.setWest(west);
                grid.setNorth(north);
                return grid;
            } else {
                return pool.remove(pool.size() - 1);
            }
        }

        /**
         * The client has to call release() once the grid is no longer needed.
         * @param grid 
         */
        public void release(GeoGrid grid) {
            if (grid != null
                    && !pool.contains(grid)
                    && grid.getCols() == cols
                    && grid.getRows() == rows
                    && grid.getCellSize() == cellSize
                    && grid.getWest() == west
                    && grid.getNorth() == north) {
                pool.add(grid);
            }
        }
    }
    /**
     * An instance of the grid pool. This is not a static class instance, as
     * the pool only handles grids of a specific size.
     */
    private GridPool gridPool = new GridPool();

    /**
     * A work package computes an intermediate result. Some work packages use
     * the result of other work packages as input. The result of each work
     * package is stored in this.result. The work package is called to update
     * its result if the parameters used by the work package have changed.
     */
    private abstract class WorkPackage {

        protected GeoGrid result;

        /**
         * Returns whether any parameter for this work package changed and the
         * result must be recomputed.
         * @return 
         */
        public abstract boolean parametersChanged();

        /**
         * copy current parameters for use with parametersChanged(). Copying
         * the parameters must not be done in process(), as parametersChanged()
         * must return the same value before and after process() is called.
         */
        public abstract void storeParameters();

        /**
         * computes the result of this work package.
         */
        public abstract void process();

        /**
         * Returns a message for display in the progress dialog.
         * @return 
         */
        public abstract String getProgressMessage();

        /**
         * Reset the result and the parameters.
         */
        public abstract void reset();

        /**
         * Initialize a grid for storing the result of this work package.
         * @param name 
         */
        protected void initResult(String name) {

            if (result == null
                    || result.getCols() != detailedGrid.getCols()
                    || result.getRows() != detailedGrid.getRows()
                    || result.getCellSize() != detailedGrid.getCellSize()
                    || result.getWest() != detailedGrid.getWest()
                    || result.getNorth() != detailedGrid.getNorth()) {

                int cols = detailedGrid.getCols();
                int rows = detailedGrid.getRows();
                result = new GeoGrid(cols, rows, detailedGrid.getCellSize());
                result.setWest(detailedGrid.getWest());
                result.setNorth(detailedGrid.getNorth());
                result.setName(name);
            }
        }
    }

    /**
     * This work package applies a Gaussian low-pass filter to the detailed
     * terrain model.
     */
    private class LODWorkPackage extends WorkPackage {

        private int prevGridFilterLoops;

        public LODWorkPackage() {
            reset();
        }

        @Override
        public boolean parametersChanged() {
            return prevGridFilterLoops != gridFilterLoops || result == null;
        }

        @Override
        public void storeParameters() {
            prevGridFilterLoops = gridFilterLoops;
        }

        @Override
        public void process() {
            initResult("initial low-pass filter");
            lowPassOp.operate(detailedGrid, result, 0.4 * gridFilterLoops); // FIXME          
        }

        @Override
        public String getProgressMessage() {
            return "Reducing Level of Detail";
        }

        @Override
        public final void reset() {
            prevGridFilterLoops = -1;
            result = null;
        }
    }

    private class RidgesWeightWorkPackage extends WorkPackage {

        private int prevRidgesMeanFilterLoops;
        private double prevRidgesPlancurvatureWeight;
        private int prevRidgesMeanFilterLoopsForCombination;

        public RidgesWeightWorkPackage() {
            reset();
        }

        @Override
        public boolean parametersChanged() {
            return prevRidgesMeanFilterLoops != ridgesMeanFilterLoops
                    || prevRidgesPlancurvatureWeight != ridgesPlancurvatureWeight
                    || prevRidgesMeanFilterLoopsForCombination != ridgesMeanFilterLoopsForCombination
                    || result == null;
        }

        @Override
        public void storeParameters() {
            prevRidgesMeanFilterLoops = ridgesMeanFilterLoops;
            prevRidgesPlancurvatureWeight = ridgesPlancurvatureWeight;
            prevRidgesMeanFilterLoopsForCombination = ridgesMeanFilterLoopsForCombination;
        }

        /**
         * Generates a grid indicating the location of mountain ridges. Returns 
         * weight values in 0..1.
         * @return 
         */
        @Override
        public void process() {

            GeoGrid maxCurv = null;
            GeoGrid planCurv = null;

            try {

                maxCurv = gridPool.acquire();
                planCurv = gridPool.acquire();

                initResult("ridges weight");

                lowPassOp.operate(detailedGrid, result, 0.7 * ridgesMeanFilterLoops); // FIXME

                // maximum curvature, all values must be larger than 0
                new PositiveMaximumCurvatureOperator().operate(result, maxCurv);

                // plan curvature
                new GridPlanCurvatureOperator().operate(result, planCurv);

                CurvatureCombineOperator curvCombineOp = new CurvatureCombineOperator();
                curvCombineOp.setScale(-ridgesPlancurvatureWeight);
                curvCombineOp.setMaxCurv(maxCurv);
                curvCombineOp.operate(planCurv, result);

                // filter combined grid
                lowPassOp.operate(result, result, 0.7 * ridgesMeanFilterLoopsForCombination);

                // scale to 0..1
                new GridScaleToRangeOperator(0, 1).operate(result, result);
            } finally {
                gridPool.release(maxCurv);
                gridPool.release(planCurv);
            }

        }

        @Override
        public String getProgressMessage() {
            return "Filtering Ridges";
        }

        @Override
        public final void reset() {
            prevRidgesMeanFilterLoops = -1;
            prevRidgesPlancurvatureWeight = Double.NaN;
            prevRidgesMeanFilterLoopsForCombination = -1;
            result = null;
        }
    }

    private class RidgesExaggerationWorkPackage extends WorkPackage {

        private double prevRidgesExaggeration;

        public RidgesExaggerationWorkPackage() {
            reset();
        }

        @Override
        public boolean parametersChanged() {
            return prevRidgesExaggeration != ridgesExaggeration
                    || lodWP.parametersChanged()
                    || ridgesWeightWP.parametersChanged()
                    || result == null;
        }

        @Override
        public void storeParameters() {
            prevRidgesExaggeration = ridgesExaggeration;
        }

        @Override
        public void process() {
            initResult(RIDGES_NAME);
            result.setName(RIDGES_NAME);
            GeoGrid src = lodWP.result;
            GeoGrid ridgesWeight = ridgesWeightWP.result;
            new WeightedScaleOperator(ridgesWeight, ridgesExaggeration).operate(src, result);
        }

        @Override
        public String getProgressMessage() {
            return "";
        }

        @Override
        public final void reset() {
            prevRidgesExaggeration = -1;
            result = null;
        }
    }

    /**
     * Generates a grid indicating the location of valleys.
     */
    private class ValleysWeightWorkPackage extends WorkPackage {

        private int prevValleysMeanFilterLoops;
        private double prevValleysCurvatureUpperLimit;

        public ValleysWeightWorkPackage() {
            reset();
        }

        @Override
        public boolean parametersChanged() {
            return prevValleysMeanFilterLoops != valleysMeanFilterLoops
                    || prevValleysCurvatureUpperLimit != valleysCurvatureUpperLimit
                    || result == null;
        }

        @Override
        public void storeParameters() {
            prevValleysMeanFilterLoops = valleysMeanFilterLoops;
            prevValleysCurvatureUpperLimit = valleysCurvatureUpperLimit;
        }

        /**
         * Generates a grid indicating the location of valleys. Returns 
         * weight values in 0..1.
         * @return 
         */
        @Override
        public void process() {
            GeoGrid minCurv = null;
            try {
                initResult("valleys weight");

                if (valleysCurvatureUpperLimit == 0) {
                    new GridAssignOperator(0).operate(result, result);
                    return;
                }

                lowPassOp.operate(detailedGrid, result, 0.7 * valleysMeanFilterLoops); // FIXME

                // compute minimum curvature, the absolute values of negative curvature is returned
                minCurv = gridPool.acquire();
                new NegativeMinimumCurvatureOperator().operate(result, minCurv);

                // scale to 0..1
                // instead of doing new GridScaleToRangeOperator(0, 1).operate(minCurv, g);
                // scale the threshold and the scale factor by the maximum curvature value
                float minCurvatureMax = minCurv.getMinMax()[1];

                // limit to upper threshold
                ClipScaleOperator clipScaleOp = new ClipScaleOperator();
                clipScaleOp.setThresholdValue(valleysCurvatureUpperLimit * minCurvatureMax);
                clipScaleOp.setScale(1f / (valleysCurvatureUpperLimit * minCurvatureMax));
                clipScaleOp.operate(minCurv, result);
            } finally {
                gridPool.release(minCurv);
            }
        }

        @Override
        public String getProgressMessage() {
            return "Filtering Valleys";
        }

        @Override
        public final void reset() {
            prevValleysMeanFilterLoops = -1;
            prevValleysCurvatureUpperLimit = Double.NaN;
            result = null;
        }
    }

    private class ValleysExaggerationWorkPackage extends WorkPackage {

        private double prevValleysExaggeration;

        public ValleysExaggerationWorkPackage() {
            reset();
        }

        @Override
        public boolean parametersChanged() {
            return prevValleysExaggeration != valleysExaggeration
                    || lodWP.parametersChanged()
                    || valleysWeightWP.parametersChanged()
                    || result == null;
        }

        @Override
        public void storeParameters() {
            prevValleysExaggeration = valleysExaggeration;
        }

        @Override
        public void process() {
            initResult(VALLEYS_NAME);
            GeoGrid srcGrid = lodWP.result;
            GeoGrid wGrid = valleysWeightWP.result;
            new WeightedScaleOperator(wGrid, valleysExaggeration).operate(srcGrid, result);
        }

        @Override
        public String getProgressMessage() {
            return "Finding Flat Areas";
        }

        @Override
        public final void reset() {
            prevValleysExaggeration = -1;
            result = null;
        }
    }

    private class FlatMaskWorkPackage extends WorkPackage {

        private int prevGridFilterLoops;
        private double prevCombinationSlopeThreshold;

        public FlatMaskWorkPackage() {
            reset();
        }

        @Override
        public boolean parametersChanged() {
            return prevGridFilterLoops != gridFilterLoops
                    || prevCombinationSlopeThreshold != combinationSlopeThreshold
                    || result == null;
        }

        @Override
        public void storeParameters() {
            prevGridFilterLoops = gridFilterLoops;
            prevCombinationSlopeThreshold = combinationSlopeThreshold;
        }

        /**
         * Generates a grid for combining flat valley areas with mountainous areas.
         * @return 
         */
        @Override
        public void process() {
            
            initResult(COMBINATION_WEIGHT_NAME);

            if (combinationSlopeThreshold == 0) {
                new GridAssignOperator(1).operate(result, result);
                return;
            }
            
            // low-pass filtered slope for smooth transitions
            lowPassOp.operate(slopeGrid, result, 0.4 * gridFilterLoops); // FIXME
            
            
            // cut off large slope values
            ClipScaleOperator clipScaleOp = new ClipScaleOperator();
            float maxRad = (float) Math.toRadians(combinationSlopeThreshold);
            clipScaleOp.setThresholdValue(maxRad);
            clipScaleOp.setScale(1f / maxRad);
            clipScaleOp.operate(result, result);

            // filter thresholded slope again to break sharp bevels
            lowPassOp.operate(result, result, 0.4 * gridFilterLoops); // FIXME
            
        }

        @Override
        public String getProgressMessage() {
            return "Finding Flat Areas";
        }

        @Override
        public final void reset() {
            prevGridFilterLoops = -1;
            prevCombinationSlopeThreshold = Double.NaN;
            result = null;
        }
    }

    private class FinalCombinationWorkPackage extends WorkPackage {

        public FinalCombinationWorkPackage() {
            reset();
        }

        @Override
        public boolean parametersChanged() {
            return valleysExaggerationWP.parametersChanged()
                    || flatMaskWP.parametersChanged()
                    || ridgesExaggerationWP.parametersChanged();
        }

        @Override
        public void storeParameters() {
        }

        /**
         * Generates a grid for combining flat valley areas with mountainous areas.
         * @return 
         */
        @Override
        public void process() {
            initResult(RESULT_NAME);
            GridCombineOperator combineOp = new GridCombineOperator();
            combineOp.setSrc2(valleysExaggerationWP.result);
            combineOp.setWeightGrid(flatMaskWP.result);
            combineOp.setMask(originalGrid); // the original may contain void values
            combineOp.operate(ridgesExaggerationWP.result, result);
        }

        @Override
        public String getProgressMessage() {
            return "Combining Valleys and Ridges Using Flat Areas";
        }

        @Override
        public final void reset() {
            result = null;
        }
    }
    
    public TerrainSculptorFilter() {

        lowPassOp = new GridGaussLowPassOperator();
        lowPassOp.setRelativeFilterSize(8);
        
        lodWP = new LODWorkPackage();
        flatMaskWP = new FlatMaskWorkPackage();
        ridgesWeightWP = new RidgesWeightWorkPackage();
        ridgesExaggerationWP = new RidgesExaggerationWorkPackage();
        valleysExaggerationWP = new ValleysExaggerationWorkPackage();
        valleysWeightWP = new ValleysWeightWorkPackage();
        finalCombinationWP = new FinalCombinationWorkPackage();

        // order the work packages by mutual dependencies: first independent, then
        // dependent packages.
        workPackages = new ArrayList<WorkPackage>(7);
        workPackages.add(lodWP);
        workPackages.add(ridgesWeightWP);
        workPackages.add(flatMaskWP);
        workPackages.add(valleysWeightWP);
        workPackages.add(ridgesExaggerationWP);
        workPackages.add(valleysExaggerationWP);
        workPackages.add(finalCombinationWP);
    }

    @Override
    public String toString() {
        String nl = System.getProperty("line.separator");

        StringBuilder sb = new StringBuilder();

        sb.append("gridFilterLoops ").append(gridFilterLoops).append(nl);
        sb.append("valleysMeanFilterLoops ").append(valleysMeanFilterLoops).append(nl);
        sb.append("valleysExaggeration ").append(valleysExaggeration).append(nl);
        sb.append("valleysCurvatureUpperLimit ").append(valleysCurvatureUpperLimit).append(nl);

        sb.append("ridgesPlancurvatureWeight ").append(ridgesPlancurvatureWeight).append(nl);
        sb.append("ridgesMeanFilterLoops ").append(ridgesMeanFilterLoops).append(nl);
        sb.append("ridgesMeanFilterLoopsForCombination ").append(ridgesMeanFilterLoopsForCombination).append(nl);
        sb.append("ridgesCurvatureUpperLimit ").append(ridgesCurvatureUpperLimit).append(nl);
        sb.append("ridgesExaggeration ").append(ridgesExaggeration).append(nl);

        sb.append("combinationSlopeThreshold ").append(combinationSlopeThreshold).append(nl);

        return sb.toString();
    }

    private void initProgress(ProgressIndicator progress) {
        progress.setTotalTasksCount(0);
        for (WorkPackage wp : workPackages) {
            if (wp.parametersChanged()) {
                progress.setTotalTasksCount(progress.getTotalTasksCount() + 1);
            }
        }
    }

    private boolean updateProgress(ProgressIndicator progress, WorkPackage wp) {
        int wpID = workPackages.indexOf(wp);
        if (wpID > 0) {
            progress.nextTask();
        }
        if (wpID >= 0) {
            progress.setMessage(wp.getProgressMessage());
        }
        return progress.progress(0);
    }

    /**
     * Filter this.detailedGrid.
     * @param progress
     * @return 
     */
    public ArrayList<GeoGrid> filter(ProgressIndicator progress) {
       
        ArrayList<GeoGrid> displayGrids = new ArrayList(workPackages.size());
        if (detailedGrid == null) {
            return displayGrids;
        } else {
            displayGrids.add(originalGrid);
        }

        gridPool.init(detailedGrid);
        initProgress(progress);

        // compute work package results
        for (WorkPackage wp : workPackages) {
            if (wp.parametersChanged()) {
                if (!updateProgress(progress, wp)) {
                    return null;
                }
                wp.process();
            }
            
            // store result for display
            displayGrids.add(wp.result);
        }
        
        // store parameters
        for (WorkPackage wp : workPackages) {
            wp.storeParameters();
        }

        return displayGrids;
    }

    public GeoGrid getGrid() {
        return originalGrid;
    }

    public void setGrid(GeoGrid newGrid) {
        originalGrid = newGrid;
        if (newGrid == null) {
            detailedGrid = null;
            slopeGrid = null;
        } else {
            if (newGrid.getStatistics().voidCount > 0) {
                // change NaN values to 0
                detailedGrid = new GridChangeVoidOperator(0).operate(newGrid);
            } else {
                detailedGrid = newGrid;
            }

            // compute slope from grid without void values
            slopeGrid = new GridSlopeOperator().operate(detailedGrid);
        }

        for (WorkPackage wp : workPackages) {
            wp.reset();
        }
    }

    public void setRidgesMeanFilterLoops(int ridgesMeanFilterLoops) {
        this.ridgesMeanFilterLoops = ridgesMeanFilterLoops;
    }

    public void setValleysMeanFilterLoops(int valleysMeanFilterLoops) {
        this.valleysMeanFilterLoops = valleysMeanFilterLoops;
    }

    public void setRidgesExaggeration(float ridgesExaggeration) {
        this.ridgesExaggeration = ridgesExaggeration;
    }

    public void setValleysExaggeration(float valleysExaggeration) {
        this.valleysExaggeration = valleysExaggeration;
    }

    /**
     * Slope threshold in degrees.
     * @param combinationSlopeThreshold
     */
    public void setCombinationSlopeThreshold(float combinationSlopeThreshold) {
        this.combinationSlopeThreshold = combinationSlopeThreshold;
    }

    public void setRidgesCurvatureUpperLimit(float ridgesCurvatureUpperLimit) {
        this.ridgesCurvatureUpperLimit = ridgesCurvatureUpperLimit;
    }

    public void setValleysCurvatureUpperLimit(float valleysCurvatureUpperLimit) {
        this.valleysCurvatureUpperLimit = valleysCurvatureUpperLimit;
    }

    public int getRidgesMeanFilterLoops() {
        return ridgesMeanFilterLoops;
    }

    public int getValleysMeanFilterLoops() {
        return valleysMeanFilterLoops;
    }

    public float getRidgesCurvatureUpperLimit() {
        return ridgesCurvatureUpperLimit;
    }

    public float getValleysCurvatureUpperLimit() {
        return valleysCurvatureUpperLimit;
    }

    public float getRidgesExaggeration() {
        return ridgesExaggeration;
    }

    public float getValleysExaggeration() {
        return valleysExaggeration;
    }

    public float getCombinationSlopeThreshold() {
        return combinationSlopeThreshold;
    }

    /**
     * @return the ridgesMeanFilterLoopsForCombination
     */
    public int getRidgesMeanFilterLoopsForCombination() {
        return ridgesMeanFilterLoopsForCombination;
    }

    /**
     * @param ridgesMeanFilterLoopsForCombination the ridgesMeanFilterLoopsForCombination to set
     */
    public void setRidgesMeanFilterLoopsForCombination(int ridgesMeanFilterLoopsForCombination) {
        this.ridgesMeanFilterLoopsForCombination = ridgesMeanFilterLoopsForCombination;
    }

    /**
     * @return the ridgesPlancurvatureWeight
     */
    public float getRidgesPlancurvatureWeight() {
        return ridgesPlancurvatureWeight;
    }

    /**
     * @param ridgesPlancurvatureWeight the ridgesPlancurvatureWeight to set
     */
    public void setRidgesPlancurvatureWeight(float ridgesPlancurvatureWeight) {
        this.ridgesPlancurvatureWeight = ridgesPlancurvatureWeight;
    }

    /**
     * @return the gridFilterLoops
     */
    public int getGridFilterLoops() {
        return gridFilterLoops;
    }

    /**
     * @param gridFilterLoops the gridFilterLoops to set
     */
    public void setGridFilterLoops(int gridFilterLoops) {
        this.gridFilterLoops = gridFilterLoops;
    }
}
