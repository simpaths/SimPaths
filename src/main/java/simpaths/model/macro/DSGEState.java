package simpaths.model.macro;

import java.util.Map;

/**
 * Container for DSGE state vector and derived quantities.
 *
 * The DSGE model separates variables into:
 * - State variables (predetermined, depend on lagged values)
 * - Jump variables (forward-looking, determined by expectations)
 *
 * Dimensions and variable indices are loaded from model_info.json at runtime
 * via the {@link VarIndices} class. A fallback schema matching the current
 * exported SimPaths DSGE layout is kept for default construction and tests.
 *
 * All values are in % deviations from steady state (log-deviations × 100).
 * For example, wageDeviation = 2.5 means wages are 2.5% above steady state.
 *
 * Key for SimPaths coupling:
 * - Wage (w) is a STATE variable → extract from state vector
 * - Inflation (π): a state when the model has price indexation; side resolved via VarIndices.piIsState
 *
 * @author DSGE-SimPaths Integration
 */
public class DSGEState {

    // ========== Variable Index Mapping ==========

    /**
    * Maps variable names to their indices within state and jump vectors.
    *
    * Loaded from model_info.json at runtime; {@link #DEFAULT} mirrors the
    * current exported SimPaths DSGE schema for fallback construction and tests.
     */
    public static class VarIndices {

        // State variable indices (within s vector, 0-indexed)
        public final int sR;
        public final int sW;
        public final int sV;
        public final int sN;
        public final int sH;
        public final int sX;
        public final int sL;
        public final int sSepShock;
        /** Demand shock state (`d_shock`) in the exported state vector. */
        public final int sDShock;

        // Jump variable indices (within y vector, 0-indexed)
        public final int yY;
        public final int yD;
        public final int yGap;
        public final int yWStar;
        public final int yU;
        public final int yURate;
        public final int yM;
        public final int yMC;
        public final int yTheta;

        /** True if pi is a state variable (exports with iota_p indexation), false if a jump */
        public final boolean piIsState;
        /** Index of pi within the state vector (piIsState) or jump vector (!piIsState) */
        public final int piIdx;

        /** Minimum required state vector length (max state index + 1) */
        public final int minStateSize;

        /** Minimum required jump vector length (max jump index + 1) */
        public final int minJumpSize;

        /** Default indices matching the current exported SimPaths DSGE structure */
        public static final VarIndices DEFAULT = createDefault();

        /**
         * Create VarIndices from index maps (typically loaded from model_info.json).
         *
         * @param stateIdx Map of state variable name → index in s vector
         * @param jumpIdx  Map of jump variable name → index in y vector
         */
        public VarIndices(Map<String, Integer> stateIdx, Map<String, Integer> jumpIdx) {
            this.sR = stateIdx.getOrDefault("R", 1);
            this.sW = stateIdx.getOrDefault("w", 3);
            this.sV = stateIdx.getOrDefault("v", 4);
            this.sN = stateIdx.getOrDefault("n", 5);
            this.sH = stateIdx.getOrDefault("h", 6);
            this.sX = stateIdx.getOrDefault("x", 7);
            this.sL = stateIdx.getOrDefault("L", 8);
            this.sSepShock = stateIdx.getOrDefault("sep_shock", 11);
            this.sDShock = stateIdx.getOrDefault("d_shock", 12);

            this.yY = jumpIdx.getOrDefault("y", 0);
            this.yD = jumpIdx.getOrDefault("d", 1);
            this.yGap = jumpIdx.getOrDefault("gap", 2);
            this.yWStar = jumpIdx.getOrDefault("w_star", 3);
            this.yU = jumpIdx.getOrDefault("u", 4);
            this.yURate = jumpIdx.getOrDefault("u_rate", 5);
            this.yM = jumpIdx.getOrDefault("m", 6);
            this.yMC = jumpIdx.getOrDefault("mc", 7);
            this.yTheta = jumpIdx.getOrDefault("theta", 8);

            // pi is a STATE when iota_p indexation puts pi(-1) in the NKPC, and a jump
            // in indexation-free exports. Resolve from whichever map declares it, with
            // no silent default: a fallback index can point at another variable (the
            // output gap) and report it as inflation.
            Integer piState = stateIdx.get("pi");
            Integer piJump = jumpIdx.get("pi");
            if (piState != null) {
                this.piIsState = true;
                this.piIdx = piState;
            } else if (piJump != null) {
                this.piIsState = false;
                this.piIdx = piJump;
            } else {
                throw new IllegalArgumentException(
                    "Variable index maps declare no 'pi' on either the state or jump side; "
                    + "cannot resolve the inflation slot (check model_info.json "
                    + "state_idx_in_s / jump_idx_in_y).");
            }

            int maxState = max(sR, sW, sV, sN, sH, sX, sL, sSepShock, sDShock);
            int maxJump = max(yY, yD, yGap, yWStar, yU, yURate, yM, yMC, yTheta);
            if (piIsState) {
                maxState = Math.max(maxState, piIdx);
            } else {
                maxJump = Math.max(maxJump, piIdx);
            }
            this.minStateSize = 1 + maxState;
            this.minJumpSize = 1 + maxJump;
        }

        private static int max(int... values) {
            int m = values[0];
            for (int i = 1; i < values.length; i++) {
                if (values[i] > m) m = values[i];
            }
            return m;
        }

        private static VarIndices createDefault() {
            Map<String, Integer> defaultState = Map.ofEntries(
                Map.entry("c", 0),
                Map.entry("R", 1),
                Map.entry("pi", 2),
                Map.entry("w", 3),
                Map.entry("v", 4),
                Map.entry("n", 5),
                Map.entry("h", 6),
                Map.entry("x", 7),
                Map.entry("L", 8),
                Map.entry("k", 9),
                Map.entry("inv", 10),
                Map.entry("sep_shock", 11),
                Map.entry("d_shock", 12)
            );
            Map<String, Integer> defaultJump = Map.of(
                "y", 0, "d", 1, "gap", 2, "w_star", 3,
                "u", 4, "u_rate", 5, "m", 6, "mc", 7, "theta", 8
            );
            return new VarIndices(defaultState, defaultJump);
        }
    }

    // ========== Default Dimensions (for the constructors without an index map) ==========

    /** Default number of state variables (current exported SimPaths DSGE schema) */
    static final int DEFAULT_N_STATE = 13;

    /** Default number of jump variables (current exported SimPaths DSGE schema) */
    static final int DEFAULT_N_JUMP = 9;

    // ========== Vectors ==========

    /** State vector */
    private final double[] stateVector;

    /** Jump vector */
    private final double[] jumpVector;

    /** Variable indices used to extract named deviations */
    private final VarIndices indices;

    // ========== Key Deviations from Steady State (in %) ==========

    /** Output deviation from steady state (%) - JUMP */
    public final double outputDeviation;

    /** Wage deviation from steady state (%) - STATE */
    public final double wageDeviation;

    /** Inflation deviation from steady state (%) — state or jump, side resolved via piIsState */
    public final double inflationDeviation;

    /** Employment deviation from steady state (%) - STATE */
    public final double employmentDeviation;

    /** Unemployment rate deviation from steady state (percentage points) - JUMP */
    public final double unemploymentRateDeviation;

    /** Hours deviation from steady state (%) - STATE */
    public final double hoursDeviation;

    /** Labor force deviation from steady state (%) - STATE */
    public final double laborForceDeviation;

    /** Interest rate deviation from steady state (%) - STATE */
    public final double interestRateDeviation;

    // ========== Constructors ==========

    /**
     * Create a DSGEState from separate state and jump vectors with explicit index mapping.
     * This is the primary constructor used by DSGEModel.stepQuarterly().
     *
     * @param stateVars Array of state variable values (% deviations)
     * @param jumpVars  Array of jump variable values (% deviations)
     * @param indices   Variable index mapping (loaded from model_info.json)
     */
    public DSGEState(double[] stateVars, double[] jumpVars, VarIndices indices) {
        if (stateVars == null || stateVars.length < indices.minStateSize) {
            throw new IllegalArgumentException(
                "State vector must have at least " + indices.minStateSize + " elements, got " +
                (stateVars == null ? "null" : stateVars.length));
        }
        if (jumpVars == null || jumpVars.length < indices.minJumpSize) {
            throw new IllegalArgumentException(
                "Jump vector must have at least " + indices.minJumpSize + " elements, got " +
                (jumpVars == null ? "null" : jumpVars.length));
        }

        this.stateVector = stateVars.clone();
        this.jumpVector = jumpVars.clone();
        this.indices = indices;

        // Extract key deviations using loaded indices
        this.interestRateDeviation = stateVars[indices.sR];
        this.wageDeviation = stateVars[indices.sW];
        this.employmentDeviation = stateVars[indices.sN];
        this.hoursDeviation = stateVars[indices.sH];
        this.laborForceDeviation = stateVars[indices.sL];

        this.outputDeviation = jumpVars[indices.yY];
        this.inflationDeviation = indices.piIsState
            ? stateVars[indices.piIdx]
            : jumpVars[indices.piIdx];
        this.unemploymentRateDeviation = jumpVars[indices.yURate];
    }

    /**
    * Create a DSGEState using default variable indices from the current exported schema.
     * Convenience constructor for tests and simple usage.
     *
     * @param stateVars Array of state variable values (% deviations)
     * @param jumpVars  Array of jump variable values (% deviations)
     */
    public DSGEState(double[] stateVars, double[] jumpVars) {
        this(stateVars, jumpVars, VarIndices.DEFAULT);
    }

    /**
     * Create a DSGEState at steady state (all zeros) using default dimensions.
     */
    public DSGEState() {
        this(new double[DEFAULT_N_STATE], new double[DEFAULT_N_JUMP], VarIndices.DEFAULT);
    }

    // ========== Getters ==========

    /**
     * Get the state vector for use as lagged state in next period.
     * @return Copy of state vector
     */
    public double[] getStateVector() {
        return stateVector.clone();
    }

    /**
     * Get the jump vector.
     * @return Copy of jump vector
     */
    public double[] getJumpVector() {
        return jumpVector.clone();
    }

    /**
     * Get a specific state variable by index.
     * @param idx State variable index
     * @return Value in % deviation from steady state
     */
    public double getStateVar(int idx) {
        if (idx < 0 || idx >= stateVector.length) {
            throw new IndexOutOfBoundsException(
                "State index must be 0-" + (stateVector.length - 1) + ", got " + idx);
        }
        return stateVector[idx];
    }

    /**
     * Get a specific jump variable by index.
     * @param idx Jump variable index
     * @return Value in % deviation from steady state
     */
    public double getJumpVar(int idx) {
        if (idx < 0 || idx >= jumpVector.length) {
            throw new IndexOutOfBoundsException(
                "Jump index must be 0-" + (jumpVector.length - 1) + ", got " + idx);
        }
        return jumpVector[idx];
    }

    /**
     * Get the variable indices used by this state.
     * @return VarIndices instance
     */
    public VarIndices getIndices() {
        return indices;
    }

    // ========== Convenience Getters ==========

    /** Wage (w) - STATE variable: the bargained wage per worker */
    public double getWageDeviation() {
        return wageDeviation;
    }

    /**
     * The wage per hour, {@code w - h}: the DSGE's wage per worker net of hours per worker
     * (both 100&times;log deviations). This is the cycle SimPaths applies, since its macro wage
     * multiplies hourly wages and the planner's trend wage is a wage per hour.
     */
    public double getHourlyWageDeviation() {
        return wageDeviation - hoursDeviation;
    }

    /** Inflation (π) — side resolved via piIsState */
    public double getInflationDeviation() {
        return inflationDeviation;
    }

    /** Output (y) - JUMP variable */
    public double getOutputDeviation() {
        return outputDeviation;
    }

    /** Employment (n) - STATE variable */
    public double getEmploymentDeviation() {
        return employmentDeviation;
    }

    /** Unemployment rate (u_rate) - JUMP variable */
    public double getUnemploymentRateDeviation() {
        return unemploymentRateDeviation;
    }

    /** Hours (h) - STATE variable */
    public double getHoursDeviation() {
        return hoursDeviation;
    }

    /** Labor force (L) - STATE variable */
    public double getLaborForceDeviation() {
        return laborForceDeviation;
    }

    /** Interest rate (R) - STATE variable */
    public double getInterestRateDeviation() {
        return interestRateDeviation;
    }

    /** Vacancies (v) - STATE variable */
    public double getVacanciesDeviation() {
        return stateVector[indices.sV];
    }

    /** Labor market tightness (θ) - JUMP variable */
    public double getThetaDeviation() {
        return jumpVector[indices.yTheta];
    }

    /** Marginal cost (mc) - JUMP variable */
    public double getMarginalCostDeviation() {
        return jumpVector[indices.yMC];
    }

    // ========== Utility Methods ==========

    @Override
    public String toString() {
        return String.format(
            "DSGEState{y=%.3f%%, w=%.3f%%, pi=%.3f%%, n=%.3f%%, u_rate=%.3f pp, h=%.3f%%, L=%.3f%%}",
            outputDeviation, wageDeviation, inflationDeviation,
            employmentDeviation, unemploymentRateDeviation, hoursDeviation, laborForceDeviation
        );
    }

    /**
     * Create a CSV-formatted string for logging.
     * @return Comma-separated values of key variables
     */
    public String toCSV() {
        return String.format("%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
            outputDeviation, wageDeviation, inflationDeviation,
            employmentDeviation, unemploymentRateDeviation, hoursDeviation, laborForceDeviation);
    }

    /**
     * Get CSV header for logging.
     * @return Header row matching toCSV() output
     */
    public static String getCSVHeader() {
        return "y_dev,w_dev,pi_dev,n_dev,u_rate_dev,h_dev,L_dev";
    }
}
