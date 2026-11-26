package simpaths.model.macro;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import microsim.annotation.GUIparameter;
import simpaths.data.Parameters;
import simpaths.model.Person;
import simpaths.model.enums.Labour;
import simpaths.model.enums.UpratingCase;

/**
 * Facade class managing all DSGE macro model integration with SimPaths.
 *
 * <p>This class encapsulates all DSGE-related state and logic, keeping
 * SimPathsModel clean and focused on microsimulation orchestration.</p>
 *
 * <h3>Layers</h3>
 * <ul>
 *   <li><b>Ramsey trend</b> ({@link RamseyTrendModel}): the planner's secular wage path.
 *       SimPaths' realized employment and hours reach it only through the end-of-year
 *       re-solve ({@link #applyRamseyRecursiveFeedback}).</li>
 *   <li><b>DSGE cycle</b> ({@link DSGEModel}, optional): the state-space model
 *       (s_t = A×s_{t-1} + B×ε), stepped once per year on the scenario's shocks. It takes
 *       no input from SimPaths.</li>
 * </ul>
 * <p>One pass per year: the labour market runs once, and nothing in the macro layer
 * re-runs it within the year.</p>
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Loading and initializing the macro layers from the bundle files</li>
 *   <li>Running the yearly macro update and the planner's end-of-year feedback</li>
 *   <li>Storing current wage/inflation deviations for individual wage adjustments</li>
 *   <li>Logging and diagnostics for debugging feedback chain</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * MacroModelManager dsge = new MacroModelManager();
 * dsge.setEnabled(true);
 *
 * // In yearly schedule
 * dsge.updateEquilibrium(year, startYear, country, persons);
 *
 * // Get wage deviation for individual wage equations
 * double wageMultiplier = 1.0 + dsge.getWageDeviation() / 100.0;
 * }</pre>
 *
 * @author DSGE-SimPaths Integration
 */
public class MacroModelManager {

    private static final Logger log = LogManager.getLogger(MacroModelManager.class);
    private static final String MACRO_PATHS_FILENAME = "MacroPaths.csv";
    private static final Pattern HANDOFF_TIME_PATTERN = Pattern.compile("\"handoff_time\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PROJECTION_QUARTERS_PATTERN =
            Pattern.compile("\"projection_quarters\"\\s*:\\s*(\\d+)");
    private static final Pattern QUARTER_LABEL_PATTERN =
            Pattern.compile("(\\d{4})q([1-4])");

    /** Forward horizon assumed when growth_terminal_state.json declares none (80 years). */
    private static final int DEFAULT_PROJECTION_QUARTERS = 320;

    /** Forward horizon of the Ramsey handoff, taken from growth_terminal_state.json. */
    private int ramseyProjectionQuarters = DEFAULT_PROJECTION_QUARTERS;

    /**
     * Handoff quarter declared by growth_terminal_state.json, e.g. "2023q1".
     *
     * <p>Every macro artifact is aligned to this quarter, and they are merged by DATE
     * rather than by row position. The reference CSV and the population projection both
     * carry a time column; consumed by index, a start-year change that regenerates only
     * some artifacts would leave the splice off by however many quarters, with every
     * check still passing.</p>
     */
    private String ramseyHandoffQuarter;
    private static final int DEMOGRAPHIC_TAIL_HALF_LIFE_QUARTERS = 20;

    // ========== Configuration ==========

    @GUIparameter(description = "Enable DSGE macro model for wage/inflation feedback")
    private boolean enabled = false;

    @GUIparameter(description = "Enable DSGE cycle layer alongside the macro trend layer")
    private boolean useDsge = false;

    @GUIparameter(description = "Log macro state each year to console")
    private boolean logMacroState = false;

    @GUIparameter(description = "Enable verbose macro diagnostics (iteration internals and sample micro logs)")
    private boolean verboseMacroLogging = false;

    @GUIparameter(description = "Path to exogenous shock scenario CSV (relative to the bundle folder, empty = baseline with no exogenous shocks)")
    private String dsgeShockScenario = "";

    /** Maximum state deviation before stability check fires (log-deviation units, default 10.0) */
    private double maxBounds = 10.0;

    @GUIparameter(description = "Use Ramsey growth model for secular wage trend")
    private boolean useRamseyTrend = true;

    /**
     * Standalone recorder mode: record labor supply paths (MacroPaths.csv)
     * without any macro model feedback. When true AND enabled==false, only
     * the LaborSupplyAggregator and MacroPathRecorder are active; no DSGE,
     * no Ramsey, no wage/inflation feedback. All DSGE/trend columns are zero;
     * only l_level, h_level, participation_rate, and workers_over_atRiskPool
     * are populated from SimPaths behavioral labor supply.
     */
    private boolean recorderOnlyMode = false;

    /**
     * Whether path recording (MacroPaths.csv) is enabled. This is orthogonal to
     * macro model usage: the macro model can run without recording, and recording
     * can run without a macro model (recorder-only mode).
     */
    private boolean pathRecordingEnabled = true;

    @GUIparameter(description = "CSV file name for the Ramsey scenario in the macro bundle (empty = baseline)")
    private String ramseyScenario = "";

    @GUIparameter(description = "Macro bundle folder; empty = input/<country>/MacroModel")
    private String macroBundleDir = "";

    /**
     * The planner's belief about how much of a realized labour-supply surprise is permanent,
     * for the employment gap (SimPaths' workers with positive hours against the planner's
     * employment path), the hours gap and the population gap alike: 1 = permanent, 0 = current
     * year only. The feedback itself is always on with a Ramsey layer, for all three margins:
     * the labour input is N*h and the planner's consumers are P, so feeding one margin alone
     * gets the capital-dilution response wrong whenever SimPaths moves them in different
     * directions.
     */
    private double ramseyFeedbackPersistence = 1.0;
    /**
     * {@code mm_ramseyFeedbackReferenceYear}: the year in which the feedback takes its level
     * factors and its hours base ({@link #feedbackReferenceYear()}); 0 stands for the year after
     * the start year, the first year with a labour market. Every later year's gap is measured
     * against this year, and the years before it leave the planner on its projection. A later
     * year suits a run whose first simulated years carry a transient of SimPaths' own, such as
     * a dip of labour supply in the first labour-market year: taken as the reference, that year
     * would turn the recovery from it into a lasting surprise of workers.
     */
    private int ramseyFeedbackReferenceYear = 0;
    /**
     * The last year whose end-of-year feedback ran ({@link #applyRamseyRecursiveFeedback}).
     * {@link #updateEquilibrium} refuses to advance the planner's trend into a year whose
     * previous feedback is missing.
     */
    private int lastFeedbackYear = Integer.MIN_VALUE;
    /** SimPaths -> Ramsey level factor, latched in the reference year ({@link #feedbackReferenceYear()}). */
    private double ramseyFeedbackLambda = Double.NaN;
    /** SimPaths -> Ramsey level factor for the population, latched in the same year as {@link #ramseyFeedbackLambda}. */
    private double ramseyFeedbackLambdaP = Double.NaN;
    /**
     * SimPaths' own hours-per-worker level in the latch year, the reference the realized hours
     * gap is measured against. Deliberately NOT a ratio to the planner's hours path, unlike
     * {@link #ramseyFeedbackLambda}: see {@link #applyRamseyFeedbackWithRealized(int, double, double)}.
     */
    private double ramseyFeedbackHoursBase = Double.NaN;
    /** Calendar year of quarter 0 of the currently solved Ramsey path (startYear, then each re-solve year). */
    private int ramseyQuarterZeroYear;
    /**
     * Original projection slices passed to the startup solve; index 0 = startYear q1. The worker
     * and population paths carry a Ramsey scenario's {@code N} and {@code P} factors
     * ({@link #scaleByScenarioPath}).
     */
    private double[] ramseyProjN;
    /** The planner's population path P on the same quarters as {@link #ramseyProjN}. */
    private double[] ramseyProjP;
    private double[] ramseyProjH;
    private double[] ramseyProjGY;
    private double[] ramseyProjNXY;
    private int ramseyProjLen = 0;

    // ========== Internal State ==========

    /** DSGE cycle model (state-space with A, Bs, C, D matrices); null without the DSGE layer */
    private DSGEModel dynamicDsgeModel;

    /** Recorder used when DSGE is disabled and only Ramsey trends are exported */
    private MacroPathRecorder trendOnlyPathRecorder;

    /** Exogenous shock scenario for counterfactual simulations (DSGE layer only) */
    private DSGEShockScenario shockScenario;

    /** Labor supply aggregator (every macro mode and recorder-only mode) */
    private LaborSupplyAggregator laborSupplyAggregator;

    /** Ramsey scenario for long-run counterfactual analysis */
    private RamseyScenario ramseyScenarioObj;

    /** Simulation start year (stored on first updateEquilibrium call for Ramsey scenario mapping) */
    private int simulationStartYear;

    /** Flag indicating DSGE model is initialized */
    private boolean initialized = false;

    /** Current wage deviation from DSGE (in %, e.g., 2.5 means wages are 2.5% above steady state) */
    private double wageDeviation = 0.0;

    /** Current inflation deviation from DSGE (in percentage points) */
    private double inflationDeviation = 0.0;

    /** Secular wage trend adjustment (in %, typically from Ramsey trend) */
    private double capitalDeepeningWageAdjustment = 0.0;

    /** Ramsey growth model providing structural trend paths (wages, output, r*) */
    private RamseyTrendModel ramseyTrend;

    /**
     * Quarter-0 Ramsey levels, against which every trend column is expressed as a
     * percentage. Null until the trend is initialised, and reset to null whenever
     * the Ramsey layer falls back.
     *
     * <p>Holding the whole TrendState, rather than one latched scalar per variable,
     * keeps every trend column in step by construction: with separate scalars, a
     * variable added to the trend layer needs four synchronised edits (declare,
     * latch, reset, divide), and missing one yields a silent 0.0 column rather than
     * an error.</p>
     */
    private RamseyTrendModel.TrendState ramseyBaseline;

    /** Current year's Ramsey trend state (set by advanceRamseyTrend, read by logging) */
    private RamseyTrendModel.TrendState ramseyCurrentYearState;

    /**
     * Current Ramsey trend percentages for all key variables.
     * Keys: "w", "y", "c", "inv", "k". Values: % deviation from first-year baseline.
     * Passed to path recorders each year for CSV export.
     */
    private Map<String, Double> currentRamseyTrends = new LinkedHashMap<>();

    /**
     * Start-year anchor for the realized-wage diagnostic. Latched on the first
     * call to {@link #populateRealizedWageIndex(Set)} once a non-empty at-risk
     * pool wage sample is available; remains 0.0 until then. The exported
     * {@code wage_realized} column is {@code (meanWage / anchor - 1) * 100}, so
     * the series is 0 in the start year — matching the {@code w_trend} /
     * {@code w_total} "% from first year" convention.
     *
     * <p><b>The anchor is NOT comparable across run modes.</b> It is latched at
     * whichever schedule point first reaches this method with a usable pool, and
     * that point differs by mode: a macro-on run reaches it after the start-year
     * wage regression has been redrawn, so the anchor is a regression wage; a
     * recorder-only run reaches it before any redraw, so the anchor is the
     * database wage carried in from the input population. The two anchors differ
     * by the redraw, which means the {@code wage_realized} <i>level</i> in the
     * first few years partly reflects anchor construction rather than any
     * macro effect.</p>
     *
     * <p>Consequence for analysis: compare {@code wage_realized} across runs of
     * the <b>same</b> mode, or compare growth rates rather than levels. Do not
     * read an early-year gap between a macro-on and a recorder-only run as a
     * treatment effect. The series is a diagnostic; nothing in the model
     * consumes it.</p>
     */
    private double startYearAtRiskWage = 0.0;

    // ========== Diagnostic Summary Tracking ==========

    private int diagnosticsCount = 0;
    private double wageDevMin = Double.POSITIVE_INFINITY;
    private double wageDevMax = Double.NEGATIVE_INFINITY;
    private double inflDevMin = Double.POSITIVE_INFINITY;
    private double inflDevMax = Double.NEGATIVE_INFINITY;
    private double outputDevMin = Double.POSITIVE_INFINITY;
    private double outputDevMax = Double.NEGATIVE_INFINITY;
    private double employmentDevMin = Double.POSITIVE_INFINITY;
    private double employmentDevMax = Double.NEGATIVE_INFINITY;
    private double wageDevSum = 0.0;
    private double inflDevSum = 0.0;
    private double outputDevSum = 0.0;
    private double employmentDevSum = 0.0;

    // ========== Tracking for Diagnostics ==========

    private int wageAdjustmentCount;
    private double wageAdjustmentSum;

    // ========== Constructor ==========

    /**
     * Create a new DSGE model manager.
     *
     * <p>The manager is created in disabled state. Call {@link #setEnabled(boolean)}
     * to enable DSGE integration.</p>
     */
    public MacroModelManager() {
        // Default configuration - DSGE disabled
    }

    // ========== Main Update Method ==========

    /**
     * Update DSGE equilibrium for the current year.
     *
     * <p>This is the main entry point called from SimPathsModel. It handles:</p>
     * <ol>
     *   <li>Initialization (on first call)</li>
     *   <li>Single-pass dynamic equilibrium computation</li>
     *   <li>Storing wage/inflation deviations for use in individual wage equations</li>
     * </ol>
     *
     * <p>The macro layer never re-runs the labour market within a tick, so there is
     * no callback and no within-period iteration.</p>
     *
     * @param year            Current simulation year
     * @param startYear       First year of simulation (for baseline detection)
     * @param countryCode     Country code (e.g., "PL") for locating model files
     * @param persons         Set of all persons in simulation
     */
    public void updateEquilibrium(
            int year,
            int startYear,
            String countryCode,
            Set<Person> persons
    ) {
        if (!enabled && !recorderOnlyMode) {
            return;  // Neither macro model nor standalone recorder enabled
        }

        // Standalone recorder mode: lightweight path (no DSGE, no Ramsey, no wage feedback)
        if (recorderOnlyMode && !enabled) {
            if (!initialized) {
                this.simulationStartYear = startYear;
                initializeRecorderOnly(countryCode);
                if (logMacroState) {
                    log.info(String.format(
                        "Macro year %d: recorder-only mode initialized (no macro feedback, recording L/h paths).",
                        year));
                }
            }
            if (laborSupplyAggregator != null) {
                // Eagerly latch labour-market-invariant demographic anchors so
                // the demographic indices are emitted from year 1 (the L/h
                // baseline is still latched post-labour-market this tick).
                // runRecorderOnlyPass handles the year-1 (no L/h baseline) case.
                laborSupplyAggregator.latchDemographicAnchors(persons);
                runRecorderOnlyPass(year, persons);
            }
            return;
        }

        // Initialize on first call (year 1 = startYear)
        boolean firstCall = !initialized;
        if (firstCall) {
            this.simulationStartYear = startYear;
            initialize(countryCode, persons);
        }

        // The planner advances its trend for `year` on a path revised by the end-of-year
        // feedback of `year - 1`; without it the trend would silently ignore a year of
        // SimPaths' labour supply. The feedback of the start year is a no-op (no labour market).
        boolean plannerSolved = useRamseyTrend && ramseyTrend != null && ramseyTrend.isSolved();
        if (plannerSolved && year - 1 > simulationStartYear && lastFeedbackYear != year - 1) {
            throw new IllegalStateException(String.format(
                    "Macro year %d: the planner's end-of-year feedback for %d did not run "
                    + "(MacroEndYearCapture must follow DSGEMacroUpdate every year). "
                    + "Refusing to advance the trend on a path that ignores %d.",
                    year, year - 1, year - 1));
        }

        resetCounters();
        if (!useDsge) {
            runRamseyOnlyPass(year, persons);
            if (firstCall && logMacroState) {
                log.info(String.format(
                        "Macro year %d: mm_macroModel runs no DSGE layer; Ramsey trend only.",
                        year));
            }
            return;
        }

        // The DSGE takes no input from SimPaths, so it runs the same annual step on the
        // scenario's shocks in every year, the start year included. The trend is the
        // planner's path, which is deterministic within the year.
        runDsgePass(year, persons);
    }

    /**
     * Called after UpdatePotentialHourlyEarnings to log wage adjustment summary.
     */
    public void logWageAdjustmentSummary() {
        int count = wageAdjustmentCount;
        if (logMacroState && count > 0) {
            double avgAdjustment = wageAdjustmentSum / count;
            System.out.println(String.format(
                "Macro wage adjustment: %d persons adjusted, average change = %+.4f GBP/hour",
                count, avgAdjustment));
        }
    }

    /**
     * Latch the start-year anchors of the level diagnostics from the post-labour-market
     * population of the first year with a labour market.
     *
     * <p>Called from SimPathsModel right after every labour-market update; only the first
     * call latches, so L/h are anchored on model-assigned hours rather than the input
     * database's. The latch also opens the gate of {@code participation_rate},
     * {@code workers_over_atRiskPool} and the category diagnostics.</p>
     *
     * @param year Current simulation year (for logging only)
     * @param persons Current person set after labor market update
     * @return true if the anchors were latched by this call, false otherwise
     */
    public boolean latchStartYearAnchorsPostLabourMarket(int year, Set<Person> persons) {
        if ((!enabled && !recorderOnlyMode) || !initialized || laborSupplyAggregator == null) {
            return false;
        }
        if (laborSupplyAggregator.isStartYearLatched()) {
            return false;
        }

        laborSupplyAggregator.latchStartYearAnchors(persons);
        if (logMacroState) {
            log.info(String.format(
                "Start-year anchors latched post-labour-market (year %d): L=%.0f workers with positive hours, h=%.1f hours/week",
                year, laborSupplyAggregator.getStartYearL(),
                laborSupplyAggregator.getStartYearH()));
        }
        return true;
    }

    /**
     * Whether the start-year anchors have been latched post-labour-market.
     * Intended for diagnostics and integration tests.
     */
    public boolean isStartYearLatched() {
        return laborSupplyAggregator != null && laborSupplyAggregator.isStartYearLatched();
    }

    /**
     * Re-emit SimPaths-side macro level aggregates against the post-alignment
     * population and overwrite the corresponding columns on the current year's
     * row in the active path recorder.
     *
     * <p>The default capture point for level columns is the DSGEMacroUpdate
     * event, which fires before {@code ConsiderMortality} and
     * {@code PopulationAlignment}. As a result the recorded {@code Pop},
     * {@code WAP}, {@code atRisk} and {@code L} levels for year t reflect the
     * pre-alignment snapshot, and any work the alignment does in year t shows
     * up in the year t+1 row. This call closes that gap so the recorded
     * demographic levels are the year's end-of-year, post-alignment values —
     * the same population the alignment contract aligns to the external
     * projection.</p>
     *
     * <p>Read-only: uses {@link LaborSupplyAggregator#computeRawAggregates}, so
     * the DSGE state and the Ramsey trend columns are unaffected. Only the
     * SimPaths-aggregated level columns are overwritten.</p>
     *
     * <p>To keep the row internally consistent, all SimPaths-aggregated
     * columns that depend on the population snapshot are re-emitted together:
     * the demographic level indices ({@code l_level}, {@code h_level},
     * {@code participation_rate}, {@code workers_over_atRiskPool},
     * {@code total_population}, {@code wap_level}, {@code atrisk_level}),
     * the labour-category metrics ({@code cat_*_share},
     * {@code cat_*_meanHours}), and the realised-wage index
     * ({@code wage_realized}).</p>
     *
     * @param year    Current simulation year (row to amend)
     * @param persons Post-alignment person set
     */
    public void updateLevelAggregatesPostAlignment(int year, Set<Person> persons) {
        if ((!enabled && !recorderOnlyMode) || !initialized
                || laborSupplyAggregator == null || persons == null) {
            return;
        }
        MacroPathRecorder recorder = getActivePathRecorder();
        if (recorder == null || !recorder.isEnabled()) {
            return;
        }

        LaborSupplyAggregator.LaborSupplyAggregates aggs =
                laborSupplyAggregator.computeRawAggregates(persons);

        Map<String, Double> updates = buildPostAlignmentLevelUpdates(aggs, persons);
        for (Map.Entry<String, Double> entry : updates.entrySet()) {
            recorder.amendTrendForYear(year, entry.getKey(), entry.getValue());
        }

        if (logMacroState) {
            log.info(String.format(
                    "Macro end-of-year capture (year %d): Pop=%.0f, WAP=%.0f, atRisk=%.0f, "
                    + "L=%.0f, h=%.2f (post-alignment).",
                    year, aggs.totalPopulation(), aggs.workingAgePopulation(),
                    aggs.atRiskPool(), aggs.laborForce(), aggs.avgHoursPerWorker()));
        }
    }

    /**
     * Return the path recorder that is currently receiving rows, or null if
     * none is active. Rows go to {@link #trendOnlyPathRecorder} whenever the
     * DSGE model is absent — that covers both recorder-only mode
     * (macro off) and the macro-on/DSGE-off Ramsey-only configuration, both of
     * which are emitted by {@link #runRamseyOnlyPass} / {@link #runRecorderOnlyPass}
     * into {@link #trendOnlyPathRecorder}. Only when the DSGE model
     * is loaded do rows go to its embedded recorder.
     */
    private MacroPathRecorder getActivePathRecorder() {
        if (dynamicDsgeModel == null) {
            return trendOnlyPathRecorder;
        }
        return dynamicDsgeModel.getPathRecorder();
    }

    /**
     * Build the map of (column → value) updates that re-express the
     * SimPaths-aggregated level columns against the supplied post-alignment
     * aggregates. Does not touch any field on this manager — purely builds the
     * map from inputs.
     */
    private Map<String, Double> buildPostAlignmentLevelUpdates(
            LaborSupplyAggregator.LaborSupplyAggregates aggs, Set<Person> persons) {
        // Exactly the formulas the in-year pass uses, applied to post-alignment
        // aggregates. Shared rather than mirrored: a divergence here would
        // corrupt only the end-of-year level columns, which no single-pass
        // test compares against the in-year ones.
        Map<String, Double> m = new LinkedHashMap<>(demographicIndexUpdates(aggs));
        m.putAll(labourCategoryUpdates(aggs));

        // Realised-wage index: mean of full-time hourly earnings potential over
        // the post-alignment at-risk pool, expressed as % from the start-year
        // anchor. Per-person wages do not move between the macro update and
        // alignment; only the pool composition changes, so a mid-run re-anchor
        // is impossible by construction here (startYearAtRiskWage was latched
        // at the first call from runDsgePass / runRamseyOnlyPass / runRecorderOnlyPass).
        Double wageRealized = computeWageRealizedPostAlignment(persons);
        if (wageRealized != null) {
            m.put("wage_realized", wageRealized);
        }

        return m;
    }

    /**
     * Compute the realised-wage index against the supplied (post-alignment)
     * person set without mutating {@link #startYearAtRiskWage} or
     * {@link #currentRamseyTrends}. Returns null if the start-year anchor has
     * not been latched yet or the at-risk pool has no wage observations.
     */
    private Double computeWageRealizedPostAlignment(Set<Person> persons) {
        if (persons == null || persons.isEmpty() || startYearAtRiskWage == 0.0) {
            return null;
        }
        double sum = 0.0;
        int count = 0;
        for (Person p : persons) {
            if (!laborSupplyAggregator.isInAtRiskPool(p)) {
                continue;
            }
            double w = p.getLabWageFullTimeHrly();
            if (w > 0.0) {
                sum += w;
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        double mean = sum / count;
        return (mean / startYearAtRiskWage - 1.0) * 100.0;
    }

    // ========== Internal Methods ==========

    /**
     * Initialize the macro layers from the bundle folder ({@link #resolveBundleDir}:
     * {@code mm_macroBundleDir}, else {@code input/<CC>/MacroModel}): the labour-supply aggregator, the Ramsey trend when configured and, with the DSGE layer on,
     * the state-space DSGE model and its shock scenario.
     */
    private void initialize(String countryCode, Set<Person> persons) {
        // Catch an invalid flag combination before loading any macro artefacts.
        validateRamseyFeedbackConfig();

        String macroModelPath = resolveBundleDir(countryCode);

        // Validate directory exists
        File macroDir = macroModelPath == null ? null : new File(macroModelPath);
        if (macroDir == null || !macroDir.exists() || !macroDir.isDirectory()) {
            String msg = String.format(
                "DSGE model directory not found: %s%n" +
                "Expected dynamic model files (policy_A.csv, policy_Bs.csv, etc.).%n" +
                "Run Dynare export scripts to generate these files.",
                macroModelPath);
            if (logMacroState) {
                log.error(msg);
            }
            throw new RuntimeException(msg);
        }
        requireVerifiedBundle(macroDir.toPath());

        try {
            // Create shared labor supply aggregator
            laborSupplyAggregator = new LaborSupplyAggregator();
            laborSupplyAggregator.setLogEnabled(logMacroState);
            // Eagerly latch labour-market-invariant demographic anchors (TotalPop,
            // WAP, AtRisk) from the year-1 population so the demographic indices
            // are emitted from the first simulated year. The L/h anchors stay
            // post-labour-market (latchStartYearAnchorsPostLabourMarket), because
            // the input database's hours differ from model-assigned hours.
            laborSupplyAggregator.latchDemographicAnchors(persons);

            if (!useDsge) {
                trendOnlyPathRecorder = pathRecordingEnabled ? new MacroPathRecorder() : null;
                applyRecorderSchema(macroModelPath, trendOnlyPathRecorder);

                if (useRamseyTrend) {
                    initializeRamseyTrend(macroModelPath);
                    // Re-check after loading and require a solved planner, so a Ramsey layer
                    // that failed to solve is fatal instead of quietly running without its
                    // trend and the feedback channel.
                    validateRamseyFeedbackConfig();
                    requireRamseyTrendSolved();
                }

                dynamicDsgeModel = null;
                shockScenario = null;
                wageDeviation = 0.0;
                inflationDeviation = 0.0;
                resetDiagnostics();
                initialized = true;

                if (logMacroState) {
                    log.info("DSGE cycle is disabled; initialized Ramsey-only macro trend mode.");
                }
                return;
            }

            // Load dynamic DSGE model (state-space)
            dynamicDsgeModel = new DSGEModel();
            dynamicDsgeModel.setLogEnabled(logMacroState);
            dynamicDsgeModel.setMaxStateDeviation(maxBounds);
            dynamicDsgeModel.loadFromDirectory(macroModelPath);
            dynamicDsgeModel.resetToSteadyState();
            dynamicDsgeModel.setPathRecordingEnabled(pathRecordingEnabled);

            if (logMacroState) {
                log.info("Dynamic DSGE model loaded from: " + macroModelPath);
            }

            // Load exogenous shock scenario if configured
            if (dsgeShockScenario != null) dsgeShockScenario = dsgeShockScenario.trim();
            if (dsgeShockScenario != null && !dsgeShockScenario.isEmpty()) {
                String scenarioPath = macroModelPath + File.separator + dsgeShockScenario;
                shockScenario = new DSGEShockScenario(
                        dynamicDsgeModel.getShockNames(),
                        dynamicDsgeModel.getNumShocks());
                shockScenario.loadFromFile(java.nio.file.Path.of(scenarioPath));
                if (logMacroState) {
                    log.info("Loaded DSGE shock scenario from: " + scenarioPath);
                    log.info(shockScenario.getSummary());
                }
            } else {
                shockScenario = null;
            }

            // Load Ramsey growth model for structural wage trend (if enabled)
            if (useRamseyTrend) {
                initializeRamseyTrend(macroModelPath);
                // Re-check after loading and require a solved planner, as in the
                // Ramsey-only branch above.
                validateRamseyFeedbackConfig();
                requireRamseyTrendSolved();
            } else {
                // No planner, but the start year must still be the bundle's handoff quarter.
                validateHandoffForDsgeOnly(macroModelPath);
            }

            wageDeviation = 0.0;
            inflationDeviation = 0.0;
            resetDiagnostics();
            initialized = true;
            trendOnlyPathRecorder = null;

        } catch (IOException e) {
            String msg = String.format(
                "Failed to read DSGE model files from %s: %s",
                macroModelPath, e.getMessage());
            if (logMacroState) {
                log.error(msg, e);
            }
            throw new RuntimeException(msg, e);
        } catch (IllegalArgumentException e) {
            String msg = String.format(
                "Invalid DSGE model files in %s: %s",
                macroModelPath, e.getMessage());
            if (logMacroState) {
                log.error(msg, e);
            }
            throw new RuntimeException(msg, e);
        }
    }

    /** Written into the bundle by the MATLAB exporter (input_processing/macromodel/internal/mark_bundle_unverified.m). */
    static final String BUNDLE_NOT_VERIFIED_MARKER = "BUNDLE_NOT_VERIFIED.txt";

    /**
     * Refuse a bundle the MATLAB side marked as unverified (contract #36). The exporters
     * write the marker before their first file and remove it only when
     * {@code verify_export_bundle} passes, and the calibration steps write it when they
     * rewrite the bundle's growth-params JSON, so its presence means the folder is
     * incomplete or mixes vintages even though every file in it looks valid.
     */
    static void requireVerifiedBundle(Path bundleDir) {
        Path marker = bundleDir.resolve(BUNDLE_NOT_VERIFIED_MARKER);
        if (Files.exists(marker)) {
            throw new IllegalStateException(String.format(
                    "%s found in %s: the macro bundle was refused by the MATLAB exporter (or a "
                    + "calibration step changed it after the last export) and must be regenerated "
                    + "from input_processing/macromodel/ before SimPaths can run on it. The marker file lists the reason.",
                    BUNDLE_NOT_VERIFIED_MARKER, bundleDir));
        }
    }

    // ========== Capital Deepening / Ramsey Trend ==========

    /**
     * Initialize the Ramsey growth model for forward trend projection.
     *
     * <p>Loads growth parameters, terminal state (K, A at end of estimation sample),
     * and reference CSV (for N/h trend extrapolation). Solves the Ramsey model once
     * for the full forward horizon (~80 years).</p>
     *
     * <p>The Ramsey scenario {@code mm_ramseyScenario}, when set, is read from the input scenario
     * folder if that folder holds it, otherwise from the bundle folder
     * (relative to the bundle folder). Its parameter columns go to {@link RamseyTrendModel}.
     * Its {@code N} and {@code P} columns scale the planner's worker and population paths
     * ({@link #scaleByScenarioPath}) after both are complete, that is after the population
     * projection, the terminal closure of {@code N} and the frozen dependency ratio of {@code P}:
     * quarter {@code j} is multiplied by {@code 1 + N} (or {@code 1 + P}) of its calendar year,
     * interpolated geometrically between q1 of one year and q1 of the next, the file's last row
     * persisting. The planner knows the scaled paths from the first solve on. The stored
     * {@link #ramseyProjN} and {@link #ramseyProjP} are the scaled paths, so {@code lambda} and
     * {@code lambda_P} latch against them and every feedback gap measures SimPaths' workers and
     * population against them. Deviations must be zero from the start year through the year
     * after the feedback's reference year, {@code startYear + 2} by default
     * ({@link #requireNoPathDeviationBeforeFirstAllowedYear}): the start
     * year's workers and population are the bundle's observed handoff, measured together with the
     * capital stock {@code K0}, and the feedback latches its level factors on the quarters of
     * the reference year, into which the factor of the following year enters through the
     * interpolation. A deviation in those years would be absorbed into the latch, and the gaps
     * could not show a mismatch between the scenario and SimPaths. A file whose {@code N} and
     * {@code P} are zero throughout leaves every planner quantity bit-identical.</p>
     *
     * <p>Called only when {@code mm_macroModel} selects a Ramsey mode, so missing artefacts
     * are fatal: continuing with a zero secular trend would run a different model from the
     * configured one while {@code saveRunParameters} still records the configured mode.</p>
     *
     * @param macroModelPath Directory containing growth_params_*.json and related files
     */
    void initializeRamseyTrend(String macroModelPath) throws IOException {
        File macroDir = new File(macroModelPath);

        // Find growth_params_*.json (country-specific)
        File[] growthParamFiles = macroDir.listFiles(
                (dir, name) -> name.startsWith("growth_params_") && name.endsWith(".json"));

        if (growthParamFiles == null || growthParamFiles.length == 0) {
            throw new IllegalStateException("Ramsey trend: no growth_params_*.json found in " + macroModelPath
                    + "; mm_macroModel requested the Ramsey trend layer, so this is fatal.");
        }

        Arrays.sort(growthParamFiles, Comparator.comparing(File::getName));

        Path paramsPath = growthParamFiles[0].toPath();
        String fileName = growthParamFiles[0].getName();
        String countryTag = fileName.substring("growth_params_".length(),
                fileName.length() - ".json".length());

        Path terminalPath = paramsPath.resolveSibling("growth_terminal_state.json");
        Path refPath = paramsPath.resolveSibling(
                "growth_model_reference_" + countryTag.toLowerCase() + ".csv");

        if (!terminalPath.toFile().exists()) {
            throw new IllegalStateException("Ramsey trend: growth_terminal_state.json not found in " + macroModelPath
                    + "; mm_macroModel requested the Ramsey trend layer, so this is fatal.");
        }
        validateRamseyHandoffTime(terminalPath);
        ramseyProjectionQuarters = readProjectionQuarters(terminalPath);
        if (!refPath.toFile().exists()) {
            throw new IllegalStateException("Ramsey trend: " + refPath.getFileName() + " not found in " + macroModelPath
                    + "; mm_macroModel requested the Ramsey trend layer, so this is fatal.");
        }

        ramseyTrend = new RamseyTrendModel();
        ramseyTrend.setLogEnabled(logMacroState);
        ramseyTrend.loadParams(paramsPath);
        ramseyTrend.loadTerminalState(terminalPath);

        // Load Ramsey scenario if configured
        if (ramseyScenario != null) ramseyScenario = ramseyScenario.trim();
        Path scenarioPath = null;
        if (ramseyScenario != null && !ramseyScenario.isEmpty()) {
            scenarioPath = Path.of(macroModelPath, ramseyScenario);
            ramseyScenarioObj = new RamseyScenario();
            ramseyScenarioObj.setLogEnabled(logMacroState);
            ramseyScenarioObj.loadFromFile(scenarioPath);
            ramseyTrend.setScenario(ramseyScenarioObj, simulationStartYear);
            log.info("Loaded Ramsey scenario from: " + scenarioPath);
            if (logMacroState) {
                log.info(ramseyScenarioObj.getSummary());
            }
        } else {
            ramseyScenarioObj = null;
        }

        // Load reference CSV and extract N, h, gY, nxY for growth-rate extrapolation
        var reference = RamseyTrendModel.loadReferenceCSV(refPath);
        int refSize = reference.size();

        // The whole forward splice hangs off the assumption that the LAST reference row
        // is the handoff quarter (startIdx = refSize - 1 below). The row carries its own
        // date, so verify rather than assume: a reference CSV regenerated for a different
        // start year would otherwise shift every downstream index silently.
        String refTerminalTime = refSize > 0 ? reference.get(refSize - 1).time() : null;
        if (ramseyHandoffQuarter != null && refTerminalTime != null
                && quarterIndex(refTerminalTime) != quarterIndex(ramseyHandoffQuarter)) {
            throw new IllegalStateException(String.format(
                    "Macro artifacts are not aligned: growth_terminal_state.json declares "
                    + "handoff %s, but %s ends at %s. The forward projection is anchored on "
                    + "the last reference row, so these must be the same quarter. Re-run the "
                    + "MATLAB workflow for the requested start year.",
                    ramseyHandoffQuarter, refPath.getFileName(), refTerminalTime));
        }
        double[] refN = new double[refSize];
        double[] refH = new double[refSize];
        double[] refGY = new double[refSize];
        double[] refNXY = new double[refSize];
        double[] refP = new double[refSize];
        for (int i = 0; i < refSize; i++) {
            refP[i] = reference.get(i).P();
            refN[i] = reference.get(i).N();
            refH[i] = reference.get(i).h();
            refGY[i] = reference.get(i).gY();
            refNXY[i] = reference.get(i).nxY();
        }

        // Extend demographic/h paths forward from end of estimation sample. The horizon
        // comes from the bundle (ramsey_trend.m's projection_quarters) rather than a
        // constant duplicated here: MATLAB solves the handoff contract over exactly this
        // many quarters, and a silent mismatch changes the terminal closure on one side
        // only.
        int totalExtended = refSize + ramseyProjectionQuarters;
        double[] extDemographic = RamseyTrendModel.extendExogenous(refN, totalExtended,
                DEMOGRAPHIC_EXTENSION_WINDOW);
        // Hours are NOT extended here. The bundle carries the planner's hours path as an
        // artefact (see the read below), because re-deriving it on this side would make the
        // trailing-growth window an unenforced convention across the MATLAB/Java boundary.
        // refH still serves the in-sample portion of the reference CSV.
        double[] extGY = RamseyTrendModel.extendShare(refGY, totalExtended);
        double[] extNXY = RamseyTrendModel.extendShare(refNXY, totalExtended);

        // Override demographic path with population projection if available.
        // This ensures the Ramsey trend uses demographic projections consistent
        // with SimPaths (e.g., Polish WAP decline) rather than extrapolating the
        // estimation country's historical growth rate.
        int startIdx = refSize - 1;
        // The projection MUST carry this bundle's country tag. There is deliberately no
        // fallback to a country-agnostic population_projection.csv: in a per-country layout
        // such a file can only ever feed one country's demographics into another country's
        // trend. A wrong demographic path invalidates the whole trend layer silently, so a
        // missing or unreadable projection fails loudly instead.
        Path popProjPath = refPath.resolveSibling("population_projection_" + countryTag + ".csv");

        if (!popProjPath.toFile().exists()) {
            throw new IllegalStateException(String.format(
                    "Ramsey trend for %s requires %s, which is missing from %s. "
                    + "Without it the trend extrapolates the estimation country's historical "
                    + "demographic growth instead of the SimPaths-consistent projection. "
                    + "Re-export the macro files (run_full_workflow without "
                    + "skip_population_projection) or choose an mm_macroModel without the Ramsey layer.",
                    countryTag, popProjPath.getFileName(), popProjPath.getParent()));
        }

        PopulationProjectionData popProjection;
        try {
            popProjection = loadPopulationProjection(popProjPath);
        } catch (IOException e) {
            throw new IllegalStateException(String.format(
                    "Ramsey trend for %s could not read %s: %s. Re-export the macro files.",
                    countryTag, popProjPath.getFileName(), e.getMessage()), e);
        }

        if (popProjection.n().length == 0) {
            throw new IllegalStateException(String.format(
                    "Ramsey trend for %s found %s but it contains no projection rows. "
                    + "Re-export the macro files.",
                    countryTag, popProjPath.getFileName()));
        }

        double[] popDemographic = popProjection.n();

        // Align the two series by DATE, not by row position. extDemographic[startIdx]
        // is the handoff quarter (the last reference row), so the projection must be
        // copied from ITS handoff row - which is not necessarily row 0. Assuming row 0
        // is the handoff would let a start-year change shift the whole demographic path
        // silently while every other check still passes.
        int popStartIdx = indexOfHandoffQuarter(popProjection.times(), popProjPath, refTerminalTime);

        int maxOverride = Math.min(popDemographic.length - popStartIdx, totalExtended - startIdx);
        System.arraycopy(popDemographic, popStartIdx, extDemographic, startIdx, maxOverride);

        // The planner's population: the reference history, then the projection's P column.
        double[] extDemographicP = new double[totalExtended];
        System.arraycopy(refP, 0, extDemographicP, 0, refSize);
        System.arraycopy(popProjection.p(), popStartIdx, extDemographicP, startIdx, maxOverride);

        // Beyond the finite population projection horizon, taper the trailing
        // demographic growth rate smoothly to zero. The tail is a terminal
        // closure for the perfect-foresight solve, not an additional forecast.
        // maxOverride counts the handoff row; MATLAB's n_avail counts quarters beyond
        // it. With no quarters beyond the handoff MATLAB never calls the closure and
        // the window-20 extrapolation stands — mirror that exactly.
        int overrideQuartersAfterHandoff = maxOverride - 1;
        if (overrideQuartersAfterHandoff >= 1 && startIdx + maxOverride < totalExtended) {
            applySmoothTerminalDemographicClosure(
                    extDemographic, startIdx + maxOverride - 1, totalExtended,
                    demographicTaperWindow(overrideQuartersAfterHandoff));
        }
        // Beyond the projection P follows N (already tapered) at the last projected ratio.
        if (startIdx + maxOverride < totalExtended) {
            applyFrozenDependencyRatio(extDemographicP, extDemographic,
                    startIdx + maxOverride - 1, totalExtended);
        }

        if (logMacroState) {
            log.info(String.format(
                    "Population projection loaded: %d quarters from %s "
                    + "(N[0]=%.1f → N[%d]=%.1f, P[0]=%.1f → P[%d]=%.1f, P/N %.4f → %.4f, "
                    + "override applied from index %d)",
                    popDemographic.length, popProjPath.getFileName(),
                    popDemographic[0],
                    popDemographic.length - 1, popDemographic[popDemographic.length - 1],
                    popProjection.p()[0],
                    popDemographic.length - 1, popProjection.p()[popDemographic.length - 1],
                    popProjection.p()[0] / popDemographic[0],
                    popProjection.p()[popDemographic.length - 1]
                            / popDemographic[popDemographic.length - 1],
                    startIdx));
        }

        // Forward projection starts at terminal date (last reference observation)
        int projLen = totalExtended - startIdx;
        double[] projDemographic = Arrays.copyOfRange(extDemographic, startIdx, totalExtended);
        double[] projP = Arrays.copyOfRange(extDemographicP, startIdx, totalExtended);
        double[] projH = loadProjectedHours(refPath, countryTag, refTerminalTime, projLen);
        double[] projGY = Arrays.copyOfRange(extGY, startIdx, totalExtended);
        double[] projNXY = Arrays.copyOfRange(extNXY, startIdx, totalExtended);

        // A Ramsey scenario's N and P columns scale the finished worker and population paths
        // (after the terminal closure and the frozen dependency ratio). The initial solve, the
        // stored slices below, and so every feedback latch, gap and re-solve, use the scaled paths.
        if (ramseyScenarioObj != null && ramseyScenarioObj.hasPathDeviations()) {
            requireNoPathDeviationBeforeFirstAllowedYear(ramseyScenarioObj, scenarioPath, simulationStartYear,
                    feedbackReferenceYear());
            projDemographic = scaleByScenarioPath(projDemographic, simulationStartYear, ramseyScenarioObj, "N");
            projP = scaleByScenarioPath(projP, simulationStartYear, ramseyScenarioObj, "P");
        }

        double K_terminal = ramseyTrend.getK();
        double A_terminal = ramseyTrend.getA();
        ramseyTrend.solveForPath(K_terminal, A_terminal, projDemographic, projH, projP, projGY, projNXY, projLen);

        // Retain the original projection slices: a later recursive-feedback re-solve builds
        // each year's inputs from these, never from the (already-consumed) local arrays above.
        this.ramseyProjN = projDemographic.clone();
        this.ramseyProjP = projP.clone();
        this.ramseyProjH = projH.clone();
        this.ramseyProjGY = projGY.clone();
        this.ramseyProjNXY = projNXY.clone();
        this.ramseyProjLen = projLen;
        this.ramseyQuarterZeroYear = simulationStartYear;

        // Set Ramsey baseline from the starting position (quarter 0) immediately,
        // so that the first advanceRamseyTrend() call produces non-zero trends.
        // Without this, the first year of DSGE operation always reports trends = 0%
        // because advanceRamseyTrend() uses the first stepYear() output as baseline.
        var q0 = ramseyTrend.getTrendAtQuarter(0);
        ramseyBaseline = q0;

        if (logMacroState) {
            log.info(String.format(
                    "Ramsey trend initialized: country=%s, K0=%.0f, A0=%.6f, "
                    + "w0=%.6f, r*_q=%.4f%%, projection=%d quarters",
                    countryTag, K_terminal, A_terminal,
                    q0.wage(), q0.rQuarterly() * 100.0, projLen));
            log.info(String.format(
                    "Ramsey baseline set at quarter 0: w=%.6f, Y=%.1f, C=%.1f, I=%.1f, K=%.0f, N=%.1f",
                    ramseyBaseline.wage(), ramseyBaseline.output(), ramseyBaseline.consumption(),
                    ramseyBaseline.investment(), ramseyBaseline.capitalStock(), ramseyBaseline.labor()));
        }
    }

    /**
     * The planner's worker path ({@code column} {@code "N"}) or population path ({@code "P"})
     * scaled by a Ramsey scenario's relative deviations.
     *
     * <p>Quarter {@code j} of {@code path} lies in calendar year {@code y = startYear + j / 4},
     * quarter {@code k = j % 4} (0 = q1). With {@code f(y) = 1 + scenario.getPathDeviation(y, column)}
     * it is multiplied by {@code f(y)^(1 - k/4) * f(y+1)^(k/4)}: the annual factor holds at q1 and
     * the quarters between are interpolated geometrically, which is the quarterly path
     * {@code build_population_projection.m} builds from annual values. The scenario's last row
     * persists, so beyond it the path is scaled by a constant and keeps its growth rates, and a
     * frozen dependency ratio {@code P/N} stays frozen at the scaled ratio. A quarter whose two
     * factors are both one is left untouched, bit for bit.</p>
     *
     * <p>{@link #initializeRamseyTrend} applies it to the finished paths and requires both
     * deviations to be zero from the start year through the year after the feedback's reference
     * year ({@link #requireNoPathDeviationBeforeFirstAllowedYear}), so the handoff quarter and the
     * quarters the feedback latches on keep the bundle's values and the feedback gaps measure
     * SimPaths against the scaled paths.</p>
     *
     * @return a new array; {@code path.clone()} when {@code scenario} is null
     */
    static double[] scaleByScenarioPath(double[] path, int startYear, RamseyScenario scenario, String column) {
        double[] out = path.clone();
        if (scenario == null) {
            return out;
        }
        for (int j = 0; j < out.length; j++) {
            int y = startYear + j / 4;
            int k = j % 4;
            double fYear = 1.0 + scenario.getPathDeviation(y, column);
            double fNext = 1.0 + scenario.getPathDeviation(y + 1, column);
            if (fYear == 1.0 && fNext == 1.0) {
                continue;
            }
            out[j] = path[j] * Math.pow(fYear, 1.0 - k / 4.0) * Math.pow(fNext, k / 4.0);
        }
        return out;
    }

    /**
     * Offset from the feedback's reference year of the first year in which a Ramsey scenario's
     * {@code N} or {@code P} may be non-zero (see {@link #requireNoPathDeviationBeforeFirstAllowedYear}).
     */
    static final int FIRST_PATH_DEVIATION_YEAR_OFFSET = 2;

    /**
     * Refuse a non-zero {@code N} or {@code P} deviation from {@code startYear} through the year
     * after the feedback's reference year ({@link #feedbackReferenceYear()}; {@code startYear + 2}
     * with the default reference year). The start year's workers and population are the bundle's
     * observed handoff, measured together with {@code K0}. The feedback latches {@code lambda} and
     * {@code lambda_P} on the planner's paths in the reference year, whose quarters 2 to 4 take
     * in the factor of the following year through the within-year interpolation of
     * {@link #scaleByScenarioPath}; a deviation in any of these years would be absorbed into that
     * calibration, and the feedback gaps could not show a mismatch between the scenario and
     * SimPaths. The first year a deviation may be non-zero is the reference year plus
     * {@value #FIRST_PATH_DEVIATION_YEAR_OFFSET}.
     */
    static void requireNoPathDeviationBeforeFirstAllowedYear(RamseyScenario scenario, Path scenarioPath,
                                                             int startYear, int referenceYear) {
        int firstAllowedYear = referenceYear + FIRST_PATH_DEVIATION_YEAR_OFFSET;
        for (int year = startYear; year < firstAllowedYear; year++) {
            for (String column : RamseyScenario.PATH_NAMES) {
                double deviation = scenario.getPathDeviation(year, column);
                if (deviation != 0.0) {
                    throw new IllegalStateException(String.format(
                            "Ramsey scenario %s: column %s is %s in %d, but N and P must be zero from the "
                            + "start year %d through %d; the first year a deviation may be non-zero is %d. "
                            + "The start year's workers and population are the bundle's observed handoff, and "
                            + "the planner calibrates SimPaths' headcount against the scenario paths in the "
                            + "feedback's reference year %d (mm_ramseyFeedbackReferenceYear; by default the year "
                            + "after the start year), whose quarters also take in the factor of %d through "
                            + "the within-year interpolation, so an earlier deviation would be absorbed into that "
                            + "calibration and the feedback gaps could not show a mismatch.",
                            scenarioPath, column, deviation, year, startYear, firstAllowedYear - 1,
                            firstAllowedYear, referenceYear, referenceYear + 1));
                }
            }
        }
    }

    /**
     * Rejects an out-of-range feedback persistence or a reference year that does not lie after
     * the start year when the Ramsey layer runs; without it the feedback has no path to re-solve
     * and neither is read. Called both before {@link #initializeRamseyTrend(String)} (catches a
     * bad value before loading any files) and after it.
     */
    void validateRamseyFeedbackConfig() {
        if (!useRamseyTrend) return;
        if (!(ramseyFeedbackPersistence >= 0.0 && ramseyFeedbackPersistence <= 1.0))
            throw new IllegalStateException(
                "mm_ramseyFeedbackPersistence must be in [0,1], got " + ramseyFeedbackPersistence);
        if (simulationStartYear > 0)
            requireFeedbackReferenceYearInsideRun(ramseyFeedbackReferenceYear, simulationStartYear,
                    Integer.MAX_VALUE);
    }

    /**
     * The year in which the feedback takes its level factors and its hours base:
     * {@code mm_ramseyFeedbackReferenceYear}, or the year after the start year when that is 0.
     */
    int feedbackReferenceYear() {
        return ramseyFeedbackReferenceYear == 0 ? simulationStartYear + 1 : ramseyFeedbackReferenceYear;
    }

    /**
     * {@code mm_ramseyFeedbackReferenceYear} is 0 (the year after the start year) or a year
     * after the start year and before the end year. The labour market does not run in the
     * start year, so that year has no workers to take a reference from; a reference in the
     * end year or later would leave no year to measure a gap in, which is a run without
     * feedback under another name.
     */
    public static void requireFeedbackReferenceYearInsideRun(int referenceYear, int startYear, int endYear) {
        if (referenceYear == 0) return;
        if (referenceYear <= startYear || referenceYear >= endYear) {
            String upper = endYear == Integer.MAX_VALUE ? "" : " and before the end year " + endYear;
            throw new IllegalStateException(String.format(
                    "mm_ramseyFeedbackReferenceYear must be 0 (the year after the start year) or a year "
                    + "after the start year %d%s, got %d", startYear, upper, referenceYear));
        }
    }

    /**
     * A requested Ramsey trend that is not solved after {@link #initializeRamseyTrend(String)}
     * is fatal: continuing with a zero secular trend would mean running a different model from
     * the configured one while {@code saveRunParameters} still records the configured mode.
     */
    private void requireRamseyTrendSolved() {
        if (ramseyTrend == null || !ramseyTrend.isSolved()) {
            throw new IllegalStateException("mm_macroModel requested the Ramsey trend layer, but it failed"
                    + " to initialise (missing MacroModel artefacts?); refusing to run a different model"
                    + " from the configured one");
        }
    }

    /**
     * Forward horizon of the Ramsey handoff contract, as declared by the export bundle.
     *
     * <p>A bundle that does not declare it falls back to 320 quarters (80 years) with a
     * warning, so it still runs but not silently.</p>
     */
    private int readProjectionQuarters(Path terminalPath) throws IOException {
        String terminalJson = Files.readString(terminalPath);
        Matcher matcher = PROJECTION_QUARTERS_PATTERN.matcher(terminalJson);
        if (!matcher.find()) {
            log.warn(String.format(
                    "%s does not declare projection_quarters; assuming %d. "
                    + "Re-export the macro files so the forward horizon is pinned by the "
                    + "bundle rather than by a constant on each side.",
                    terminalPath.getFileName(), DEFAULT_PROJECTION_QUARTERS));
            return DEFAULT_PROJECTION_QUARTERS;
        }

        int quarters = Integer.parseInt(matcher.group(1));
        if (quarters <= 0) {
            throw new IllegalStateException(String.format(
                    "%s declares projection_quarters=%d, which must be positive.",
                    terminalPath.getFileName(), quarters));
        }
        if (logMacroState) {
            log.info("Ramsey projection horizon: " + quarters + " quarters (from bundle)");
        }
        return quarters;
    }

    /** Forward horizon actually in use, after reading the bundle. */
    public int getRamseyProjectionQuarters() {
        return ramseyProjectionQuarters;
    }

    private void validateRamseyHandoffTime(Path terminalPath) throws IOException {
        if (simulationStartYear <= 0) {
            return;
        }

        String expectedHandoff = simulationStartYear + "q1";
        String terminalJson = Files.readString(terminalPath);
        Matcher matcher = HANDOFF_TIME_PATTERN.matcher(terminalJson);
        if (!matcher.find()) {
            throw new IllegalStateException(String.format(
                    "Macro model enabled for simulation start year %d, but %s is missing handoff_time. "
                    + "Expected handoff_time=%s. Re-export the macro files or set mm_macroModel: off.",
                    simulationStartYear, terminalPath.getFileName(), expectedHandoff));
        }

        String actualHandoff = matcher.group(1).trim().toLowerCase();
        if (!actualHandoff.equals(expectedHandoff)) {
            throw new IllegalStateException(String.format(
                    "Macro model handoff mismatch: simulation start year %d implies %s, "
                    + "but %s declares handoff_time=%s. Re-export the macro files for the requested start year "
                    + "or set mm_macroModel: off.",
                    simulationStartYear, expectedHandoff, terminalPath.getFileName(), actualHandoff));
        }
        // Retained so the CSV merges can align on this quarter by DATE rather than
        // trusting row positions. Validating the scalar alone is not enough: it says
        // nothing about whether the reference CSV and the population projection were
        // regenerated for the same quarter.
        ramseyHandoffQuarter = actualHandoff;
    }

    /**
     * Convert a "YYYYqQ" label into a comparable quarter index.
     *
     * @return year*4 + (quarter-1), or {@link Integer#MIN_VALUE} if unparseable
     */
    static int quarterIndex(String label) {
        if (label == null) {
            return Integer.MIN_VALUE;
        }
        Matcher m = QUARTER_LABEL_PATTERN.matcher(label.trim().toLowerCase());
        if (!m.matches()) {
            return Integer.MIN_VALUE;
        }
        int year = Integer.parseInt(m.group(1));
        int quarter = Integer.parseInt(m.group(2));
        if (quarter < 1 || quarter > 4) {
            return Integer.MIN_VALUE;
        }
        return year * 4 + (quarter - 1);
    }

    /**
     * Original projection tail from quarter fromQ, with the realized gap delta applied under
     * a geometric per-year persistence belief: quarter j is scaled by 1 + (delta-1)*rho^floor(j/4).
     * rho=1: the gap is permanent (random walk); rho=0: only the realized year (j<4) is replaced
     * (Math.pow(0,0)==1) and the tail stays at the assumed path.
     */
    static double[] buildRevisedLaborSlice(double[] projN, int fromQ, double delta, double persistence) {
        double[] out = Arrays.copyOfRange(projN, fromQ, projN.length);
        for (int j = 0; j < out.length; j++) {
            double weight = Math.pow(persistence, j / 4);
            out[j] *= 1.0 + (delta - 1.0) * weight;
        }
        return out;
    }

    /**
     * Trailing-growth window for extending the demographic path past the estimation sample.
     * MUST match the window of {@code extend_level_path(N_trend(...), ..., 20)} and
     * {@code extend_level_path(handoff_labor_proj, ..., 20)} in
     * {@code input_processing/macromodel/ramsey_trend.m}, which builds the same projection on
     * the MATLAB side.
     *
     * <p>Nothing enforces this across the MATLAB/Java boundary; the population projection file
     * overrides the extrapolated path over the years it covers, but this window still governs
     * the stretch beyond it. Hours have no window here: the bundle carries
     * {@code hours_projection_*.csv}, so the hours path has one owner. The terminal-tail
     * windows are duplicated on both sides and are pinned in {@link RamseyTrendModel}.</p>
     */
    static final int DEMOGRAPHIC_EXTENSION_WINDOW = 20;

    /**
     * Taper seed window, mirroring {@code tail_window = min(20, n_avail - 1)} in
     * ramsey_trend.m: the trailing growth rate must be measured inside the projection
     * override, never across the splice into pre-override extrapolated data.
     */
    static int demographicTaperWindow(int overrideQuartersAfterHandoff) {
        return Math.min(20, overrideQuartersAfterHandoff - 1);
    }

    static final String TREND_KEY_REALIZED_GAP = "n_realized_gap";
    static final String TREND_KEY_REALIZED_HOURS_GAP = "h_realized_gap";
    static final String TREND_KEY_REALIZED_POP_GAP = "p_realized_gap";
    static final String TREND_KEY_WAGE_REVISION = "w_trend_revision";

    /**
     * Extended-path feedback step for one completed simulation year: map the realized headcount
     * (SimPaths' workers with positive hours) into Ramsey units, rebuild the remaining-horizon labor path under the persistence belief,
     * and re-solve the trend from the predetermined capital state. The wage SimPaths used for
     * {@code year} is NOT revised (one-period-stale convention); the next {@link #advanceRamseyTrend()}
     * call steps the updated path for {@code year + 1}.
     */
    void applyRamseyFeedbackWithRealized(int year, double realizedLaborForce) {
        applyRamseyFeedbackWithRealized(year, realizedLaborForce, Double.NaN);
    }

    /**
     * As {@link #applyRamseyFeedbackWithRealized(int, double)}, additionally feeding realized
     * hours per worker into the planner's hours path, with the same persistence
     * {@link #ramseyFeedbackPersistence} as the headcount gap. The Ramsey labor input is N*h, so
     * an x% hours gap dilutes capital exactly as an x% headcount gap does. Pass a non-finite or
     * non-positive {@code realizedHours} to leave hours inert.
     *
     * <p><b>The two gaps use different references, deliberately.</b> The headcount gap is
     * measured against the planner's projected N, which is legitimate because that projection
     * is overwritten at startup with the same population projection SimPaths aligns to, so
     * the realized headcount and the projected employment are comparable and delta_n isolates genuine
     * labour-market surprises. This holds unless an input scenario replaces SimPaths' population
     * workbook: then the gap carries the scenario's departure from the bundle's path, or the
     * unforeseen part when the Ramsey scenario's {@code N} describes it. No such reconciliation exists for hours: the planner's hours
     * path is a log-linear trend fitted to observed hours ({@code hours_trend_method} in the
     * growth params, about -0.2%/yr for PL) and SimPaths models no hours trend at all, so
     * measuring delta_h against the projection would convert that modelling disagreement into
     * a permanent labour-supply shift worth several percent of the trend wage. The gap is
     * therefore measured against SimPaths' OWN latch-year hours: the planner keeps its fitted
     * trend as a maintained assumption and SimPaths feeds back only its own hours movements on
     * top of it. Flat SimPaths hours give delta_h = 1 and no revision, which is correct,
     * because SimPaths carries no information about the hours trend.
     *
     * <p>Revisit this if SimPaths ever gains an exogenous hours projection of its own (for
     * example an hours alignment): the reference would then become that projection, exactly as
     * the population projection is the reference for employment.</p>
     */
    void applyRamseyFeedbackWithRealized(int year, double realizedLaborForce, double realizedHours) {
        applyRamseyFeedbackWithRealized(year, realizedLaborForce, realizedHours, Double.NaN);
    }

    /**
     * As the three-argument overload, additionally feeding SimPaths' own population (all ages,
     * weighted, after alignment) into the planner's population path P. The population gap
     * latches its own level factor {@link #ramseyFeedbackLambdaP} in the same year as
     * {@link #ramseyFeedbackLambda}, is measured against the planner's P, and is carried
     * forward under the same persistence {@link #ramseyFeedbackPersistence}.
     *
     * <p>The level of P is a pure scale: scaling the whole P path leaves K, Y, C and the wage
     * unchanged. A population gap therefore moves the trend wage only through the time shape
     * of the planner's P path, so at rho = 1, where the gap scales the whole remaining path, it
     * leaves the wage unchanged whatever its size. Workers move the wage through capital per
     * worker whatever the population does, so at rho = 1 a joint surprise to workers and
     * population revises the wage like a workers-only one.</p>
     *
     * <p>Pass a non-finite or non-positive {@code realizedPopulation} to leave the population on
     * the bundle's path. Such a population is logged as a warning: in the latch year
     * {@link #ramseyFeedbackLambdaP} is then never latched and the population margin stays on
     * the bundle's path for the whole run; in a later year that year's population is left on
     * the projection.</p>
     */
    void applyRamseyFeedbackWithRealized(int year, double realizedLaborForce, double realizedHours,
                                         double realizedPopulation) {
        if (ramseyTrend == null || !ramseyTrend.isSolved() || ramseyProjN == null) return;
        if (year <= simulationStartYear) return; // no labour market in the start year
        if (year < feedbackReferenceYear()) return; // the planner stays on its projection until the reference year

        if (!Double.isFinite(realizedLaborForce) || realizedLaborForce <= 0.0) {
            log.warn(String.format(
                    "Ramsey feedback %d: invalid realized labour force %.4f; skipping.",
                    year, realizedLaborForce));
            return;
        }
        int calQ = 4 * (year - simulationStartYear);   // quarter offset in the ORIGINAL projection arrays
        if (calQ + 8 > ramseyProjLen) {                 // need the realized year plus at least one more
            log.warn(String.format(
                    "Ramsey feedback %d: projection horizon exhausted; skipping re-solve.", year));
            return;
        }
        double assumedMeanN = (ramseyProjN[calQ] + ramseyProjN[calQ + 1]
                + ramseyProjN[calQ + 2] + ramseyProjN[calQ + 3]) / 4.0;
        double assumedMeanP = (ramseyProjP[calQ] + ramseyProjP[calQ + 1]
                + ramseyProjP[calQ + 2] + ramseyProjP[calQ + 3]) / 4.0;
        boolean hoursUsable = Double.isFinite(realizedHours) && realizedHours > 0.0;
        boolean popUsable = Double.isFinite(realizedPopulation) && realizedPopulation > 0.0;

        if (Double.isNaN(ramseyFeedbackLambda)) {       // the reference year
            ramseyFeedbackLambda = assumedMeanN / realizedLaborForce;
            if (hoursUsable) ramseyFeedbackHoursBase = realizedHours;
            if (popUsable) {
                ramseyFeedbackLambdaP = assumedMeanP / realizedPopulation;
            } else {
                log.warn(String.format(
                        "Ramsey feedback %d: invalid realized population %.4f in the latch year; lambda_P "
                        + "is not latched, so the population margin stays on the bundle's path for the "
                        + "whole run.", year, realizedPopulation));
            }
            amendFeedbackDiagnostics(year, 0.0, hoursUsable ? 0.0 : Double.NaN,
                    popUsable ? 0.0 : Double.NaN, Double.NaN);
            if (logMacroState) {
                log.info(String.format(
                        "Ramsey feedback %d: latched lambda=%.6f (assumed N=%.1f, realized L=%.1f).",
                        year, ramseyFeedbackLambda, assumedMeanN, realizedLaborForce));
                if (popUsable) {
                    log.info(String.format(
                            "Ramsey feedback %d: latched lambda_P=%.6f (assumed P=%.1f, realized population=%.1f).",
                            year, ramseyFeedbackLambdaP, assumedMeanP, realizedPopulation));
                }
                if (hoursUsable) {
                    log.info(String.format(
                            "Ramsey feedback %d: latched hours reference h=%.4f (SimPaths' own base).",
                            year, ramseyFeedbackHoursBase));
                }
            }
            return;                                     // delta == 1 by construction
        }

        boolean hoursMeasurable = hoursUsable && !Double.isNaN(ramseyFeedbackHoursBase)
                && ramseyFeedbackHoursBase > 0.0;
        double delta = ramseyFeedbackLambda * realizedLaborForce / assumedMeanN;
        double deltaH = hoursMeasurable ? realizedHours / ramseyFeedbackHoursBase : 1.0;
        boolean popMeasurable = popUsable && !Double.isNaN(ramseyFeedbackLambdaP);
        if (!popUsable && !Double.isNaN(ramseyFeedbackLambdaP)) {
            log.warn(String.format(
                    "Ramsey feedback %d: invalid realized population %.4f; the population is left on "
                    + "its projection this year.", year, realizedPopulation));
        }
        double deltaP = popMeasurable ? ramseyFeedbackLambdaP * realizedPopulation / assumedMeanP : 1.0;
        int qYearCur = 4 * (year - ramseyQuarterZeroYear);  // offset of `year` in the CURRENT solved model
        double wOldNext = meanWageAtQuarterOrNaN(qYearCur + 4);

        // The three margins (workers, hours, population), one persistence.
        double[] nSlice = buildRevisedLaborSlice(ramseyProjN, calQ, delta, ramseyFeedbackPersistence);
        double[] hSlice = hoursMeasurable
                ? buildRevisedLaborSlice(ramseyProjH, calQ, deltaH, ramseyFeedbackPersistence)
                : Arrays.copyOfRange(ramseyProjH, calQ, ramseyProjLen);
        double[] gYSlice = Arrays.copyOfRange(ramseyProjGY, calQ, ramseyProjLen);
        double[] nxSlice = Arrays.copyOfRange(ramseyProjNXY, calQ, ramseyProjLen);

        double k0 = ramseyTrend.getKAtQuarter(qYearCur);    // predetermined at the start of `year`
        double a0 = ramseyTrend.getAAtQuarter(qYearCur);

        if (ramseyScenarioObj != null) {
            ramseyTrend.setScenario(ramseyScenarioObj, year);   // deviations are keyed to quarter-0's calendar year
        }
        ramseyTrend.setTfpPhaseOffsetQuarters(calQ);             // TFP convergence continues, does not restart
        double[] pSlice = popMeasurable
                ? buildRevisedLaborSlice(ramseyProjP, calQ, deltaP, ramseyFeedbackPersistence)
                : Arrays.copyOfRange(ramseyProjP, calQ, ramseyProjLen);
        ramseyTrend.solveForPath(k0, a0, nSlice, hSlice, pSlice, gYSlice, nxSlice, ramseyProjLen - calQ);
        ramseyQuarterZeroYear = year;
        ramseyTrend.stepYear();                                  // consume the realized year; cursor now at year+1

        double wNewNext = meanWageAtQuarterOrNaN(4);
        double revisionPct = (!Double.isFinite(wOldNext) || wOldNext == 0.0) ? Double.NaN
                : (wNewNext / wOldNext - 1.0) * 100.0;
        amendFeedbackDiagnostics(year, (delta - 1.0) * 100.0,
                hoursMeasurable ? (deltaH - 1.0) * 100.0 : Double.NaN,
                popMeasurable ? (deltaP - 1.0) * 100.0 : Double.NaN, revisionPct);
        if (logMacroState) {
            log.info(String.format(
                    "Ramsey feedback %d (rho=%.3f): delta=%.4f%%, delta_h=%.4f%%, delta_p=%.4f%%, next-year trend wage revised by %.4f%%.",
                    year, ramseyFeedbackPersistence,
                    (delta - 1.0) * 100.0, (deltaH - 1.0) * 100.0, (deltaP - 1.0) * 100.0, revisionPct));
        }
    }

    private double meanWageAtQuarterOrNaN(int firstQuarter) {
        if (firstQuarter < 0 || firstQuarter + 4 > ramseyTrend.getSimQuarters()) return Double.NaN;
        double sum = 0.0;
        for (int j = 0; j < 4; j++) sum += ramseyTrend.getTrendAtQuarter(firstQuarter + j).wage();
        return sum / 4.0;
    }

    private void amendFeedbackDiagnostics(int year, double gapPct, double hoursGapPct, double popGapPct,
                                          double revisionPct) {
        MacroPathRecorder recorder = getActivePathRecorder();
        if (recorder == null) return;
        recorder.amendTrendForYear(year, TREND_KEY_REALIZED_GAP, gapPct);
        if (Double.isFinite(hoursGapPct)) recorder.amendTrendForYear(year, TREND_KEY_REALIZED_HOURS_GAP, hoursGapPct);
        if (Double.isFinite(popGapPct)) recorder.amendTrendForYear(year, TREND_KEY_REALIZED_POP_GAP, popGapPct);
        if (Double.isFinite(revisionPct)) recorder.amendTrendForYear(year, TREND_KEY_WAGE_REVISION, revisionPct);
    }

    /**
     * {@code mm_macroModel: dsge}: no planner is loaded, but the simulation start year must
     * still be the bundle's handoff quarter, which {@code growth_terminal_state.json} declares.
     * Fails when that file is missing or declares another quarter.
     */
    private void validateHandoffForDsgeOnly(String macroModelPath) throws IOException {
        Path terminalPath = Path.of(macroModelPath, "growth_terminal_state.json");
        if (!Files.exists(terminalPath)) {
            throw new IllegalStateException(String.format(
                    "DSGE-only mode: %s not found in %s; it declares the handoff quarter the simulation "
                    + "start year must match. Re-export the macro files.",
                    terminalPath.getFileName(), macroModelPath));
        }
        validateRamseyHandoffTime(terminalPath);
    }

    /**
     * MacroEndYearCapture hook: the realized post-alignment workers, hours and population, from
     * one {@code computeRawAggregates} call -> extended-path re-solve. Runs whenever the manager is enabled and the planner is solved, and records the
     * year for the sequencing check in {@link #updateEquilibrium}.
     */
    public void applyRamseyRecursiveFeedback(int year, Set<Person> persons) {
        if (!enabled || laborSupplyAggregator == null) return;
        if (!(useRamseyTrend && ramseyTrend != null && ramseyTrend.isSolved())) return;
        lastFeedbackYear = year;
        var aggregates = laborSupplyAggregator.computeRawAggregates(persons);
        applyRamseyFeedbackWithRealized(year, aggregates.laborForce(), aggregates.avgHoursPerWorker(),
                aggregates.totalPopulation());
    }

    /**
     * Advance the Ramsey trend model by one year (4 quarters).
     *
     * <p>Called once per simulation year in {@link #updateEquilibrium}, before the
     * DSGE cycle computation. Sets {@link #capitalDeepeningWageAdjustment} to the
     * percentage wage trend relative to the first simulation year.</p>
     *
     * <p>Baselines for all key variables are set during {@link #initializeRamseyTrend}
     * from the quarter-0 position. So the first call here already produces non-zero
     * trends (measuring change from quarter 0 to the end of the first stepped year).</p>
     *
    * <p>Populates {@link #currentRamseyTrends} with keys: w, y, c, inv, k, g, nx,
    * n_ramsey, p_ramsey and the levels y_ramsey_level, c_ramsey_level, inv_ramsey_level,
    * k_ramsey_level, n_ramsey_level, p_ramsey_level.</p>
     */
    void advanceRamseyTrend() {
        var ts = ramseyTrend.stepYear();
        ramseyCurrentYearState = ts;

        // Baselines are set during initializeRamseyTrend() from quarter 0.
        // Every call here computes trends relative to that baseline.
        capitalDeepeningWageAdjustment = (ts.wage() / ramseyBaseline.wage() - 1.0) * 100.0;
        currentRamseyTrends.clear();
        currentRamseyTrends.put("w", capitalDeepeningWageAdjustment);
        currentRamseyTrends.put("y", (ts.output() / ramseyBaseline.output() - 1.0) * 100.0);
        currentRamseyTrends.put("c", (ts.consumption() / ramseyBaseline.consumption() - 1.0) * 100.0);
        currentRamseyTrends.put("inv", (ts.investment() / ramseyBaseline.investment() - 1.0) * 100.0);
        currentRamseyTrends.put("k", (ts.capitalStock() / ramseyBaseline.capitalStock() - 1.0) * 100.0);
        // G and NX: use absolute difference since baseline could be near zero (especially NX)
        currentRamseyTrends.put("g", ramseyBaseline.government() != 0.0
                ? (ts.government() / ramseyBaseline.government() - 1.0) * 100.0 : 0.0);
        currentRamseyTrends.put("nx", ramseyBaseline.netExports() != 0.0
                ? (ts.netExports() / ramseyBaseline.netExports() - 1.0) * 100.0 : 0.0);
        // Ramsey model's labor input (workers N) — for comparison with SimPaths population
        currentRamseyTrends.put("n_ramsey", ramseyBaseline.labor() > 0.0
                ? (ts.labor() / ramseyBaseline.labor() - 1.0) * 100.0 : 0.0);
        currentRamseyTrends.put("p_ramsey", ramseyBaseline.population() > 0.0
                ? (ts.population() / ramseyBaseline.population() - 1.0) * 100.0 : 0.0);
        // Levels of the annual state, in the bundle's units: comparable across runs, unlike the
        // trends above, which are indexed to each run's own quarter 0.
        currentRamseyTrends.put("y_ramsey_level", ts.output());
        currentRamseyTrends.put("c_ramsey_level", ts.consumption());
        currentRamseyTrends.put("inv_ramsey_level", ts.investment());
        currentRamseyTrends.put("k_ramsey_level", ts.capitalStock());
        currentRamseyTrends.put("n_ramsey_level", ts.labor());
        currentRamseyTrends.put("p_ramsey_level", ts.population());

        if (logMacroState) {
            System.out.println(String.format(
                "  Ramsey trend: w=%.6f (base=%.6f), Y=%.1f, r*_q=%.4f%% → wage_trend=%+.4f%%",
                ts.wage(), ramseyBaseline.wage(), ts.output(),
                ts.rQuarterly() * 100.0, capitalDeepeningWageAdjustment));
            log.info(String.format("Ramsey trend: w=%.6f → wage_trend=%+.4f%%",
                ts.wage(), capitalDeepeningWageAdjustment));
        }
    }

    /**
     * Compute the secular wage-trend adjustment.
     *
     * <p>Ramsey trend is the supported mechanism. If Ramsey trend is unavailable,
     * the secular adjustment falls back to zero.</p>
     */
    private void computeCapitalDeepeningTrend() {
        // Ramsey trend: already computed by advanceRamseyTrend()
        if (useRamseyTrend && ramseyTrend != null && ramseyTrend.isSolved()) {
            return;
        }

        // Without a solved planner (mode dsge) the secular trend is zero.
        currentRamseyTrends.clear();
        capitalDeepeningWageAdjustment = 0.0;
        currentRamseyTrends.put("w", capitalDeepeningWageAdjustment);

        if (logMacroState) {
            System.out.println("  Ramsey trend unavailable; secular wage trend fallback = 0.0000%.");
        }

        if (logMacroState) {
            log.info("Ramsey trend unavailable; secular wage trend fallback = 0.0000%.");
        }
    }

    /**
     * Parsed population projection data: quarter labels, the N path and the P path. Both
     * paths come from one workbook (contract 41).
     *
     * @param times quarter labels ("2023q1", ...), one per row. Carried so the splice
     *              can align on DATE; merged by row position, this file would let a
     *              start-year change corrupt a run silently.
     * @param n     employment (workers) per quarter
     * @param p     the planner's population (consumers) per quarter
     */
    private record PopulationProjectionData(String[] times, double[] n, double[] p) {}

    private record HoursProjectionData(String[] times, double[] h) {}

    /**
     * Read the planner's hours path for the forward horizon from the bundle.
     *
     * <p>Mandatory, with no fallback to extrapolating the reference CSV here. Such a fallback
     * would be a second derivation of the hours path with its own trailing-growth window
     * (MATLAB uses {@code extend_level_path(handoff_h_hist, ..., 1)}). Any divergence would
     * stay invisible while {@code hours_trend_method} is {@code log_linear}, which makes the
     * exported {@code h} column a pure exponential so every window recovers the same rate.
     * A missing file therefore fails loudly — the same rule the population projection
     * follows.</p>
     *
     * @param refPath           the reference CSV, whose sibling the projection is
     * @param countryTag        bundle country tag, e.g. {@code Poland}
     * @param refTerminalTime   last reference quarter, used to align by date
     * @param projLen           quarters the solve needs, starting at the handoff
     */
    private double[] loadProjectedHours(Path refPath, String countryTag,
                                        String refTerminalTime, int projLen) {
        Path hoursPath = refPath.resolveSibling("hours_projection_" + countryTag + ".csv");

        if (!hoursPath.toFile().exists()) {
            throw new IllegalStateException(String.format(
                    "Ramsey trend for %s requires %s, which is missing from %s. The bundle "
                    + "carries the planner's hours path so that both sides cannot project "
                    + "different hours from the same handoff. Re-export the macro files "
                    + "(export_ramsey_for_simpaths refreshes the Ramsey half without Dynare).",
                    countryTag, hoursPath.getFileName(), hoursPath.getParent()));
        }

        HoursProjectionData hours;
        try {
            hours = loadHoursProjection(hoursPath);
        } catch (IOException e) {
            throw new IllegalStateException(String.format(
                    "Ramsey trend for %s could not read %s: %s. Re-export the macro files.",
                    countryTag, hoursPath.getFileName(), e.getMessage()), e);
        }

        int hoursStartIdx = indexOfHandoffQuarter(hours.times(), hoursPath, refTerminalTime);
        int available = hours.h().length - hoursStartIdx;
        if (available < projLen) {
            throw new IllegalStateException(String.format(
                    "%s covers %d quarters from the handoff, but the Ramsey forward horizon "
                    + "is %d. The hours projection and projection_quarters come from the same "
                    + "MATLAB workspace, so a shortfall means the bundle is a mixed vintage. "
                    + "Re-export the macro files.",
                    hoursPath.getFileName(), available, projLen));
        }

        double[] projH = Arrays.copyOfRange(hours.h(), hoursStartIdx, hoursStartIdx + projLen);
        for (int i = 0; i < projH.length; i++) {
            if (!Double.isFinite(projH[i]) || projH[i] <= 0.0) {
                throw new IllegalStateException(String.format(
                        "%s contains a non-positive or non-finite hours value (%.6f) at "
                        + "quarter %d. Re-export the macro files.",
                        hoursPath.getFileName(), projH[i], hoursStartIdx + i));
            }
        }

        if (logMacroState) {
            log.info(String.format(
                    "Ramsey hours path read from %s: %d quarters from %s (h=%.4f to %.4f).",
                    hoursPath.getFileName(), projLen, hours.times()[hoursStartIdx],
                    projH[0], projH[projH.length - 1]));
        }
        return projH;
    }

    /**
     * Load the planner's hours-per-worker path produced by MATLAB's
     * {@code internal/write_hours_projection.m}. Format: {@code time,h}, one row per
     * quarter, first row at the handoff quarter.
     *
     * @throws IOException if the file cannot be read or a row cannot be parsed
     */
    private HoursProjectionData loadHoursProjection(Path csvPath) throws IOException {
        List<String> lines = Files.readAllLines(csvPath);
        var times = new ArrayList<String>();
        var hValues = new ArrayList<Double>();

        for (int i = 1; i < lines.size(); i++) {   // row 0 is the header
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length < 2) {
                throw new IOException(String.format(
                        "%s line %d: expected 'time,h' but found '%s'",
                        csvPath.getFileName(), i + 1, line));
            }
            try {
                hValues.add(Double.parseDouble(parts[1].trim()));
            } catch (NumberFormatException e) {
                throw new IOException(String.format(
                        "%s line %d: cannot parse hours value '%s'",
                        csvPath.getFileName(), i + 1, parts[1].trim()), e);
            }
            times.add(parts[0].trim());
        }

        double[] h = new double[hValues.size()];
        for (int i = 0; i < h.length; i++) {
            h[i] = hValues.get(i);
        }
        return new HoursProjectionData(times.toArray(new String[0]), h);
    }

    /**
     * Locate the handoff quarter within the population projection.
     *
     * <p>Both this file and the reference CSV are aligned to the handoff quarter, but
     * neither is guaranteed to START there — a projection may legitimately begin
     * earlier. Finding the row by date makes that case work instead of silently
     * shifting the projected path by the offset.</p>
     *
     * <p>Shared by every bundle projection (population and hours) so the date alignment
     * has one implementation. A second copy is exactly what drifts.</p>
     *
     * @param times             the projection's quarter labels
     * @param referenceTerminal the last reference-CSV quarter, i.e. the handoff
     * @throws IllegalStateException if the handoff quarter is not present at all
     */
    private int indexOfHandoffQuarter(String[] times, Path csvPath,
                                      String referenceTerminal) {
        String handoff = ramseyHandoffQuarter != null ? ramseyHandoffQuarter : referenceTerminal;
        if (handoff == null) {
            // No declared handoff to align on (no simulation start year set, e.g. in a
            // bare unit test). Fall back to assuming row 0 is the handoff rather than
            // failing, but say so.
            log.warn(String.format(
                    "No handoff quarter available to align %s; assuming its first row is "
                    + "the handoff. Set the simulation start year so the alignment can be "
                    + "verified.", csvPath.getFileName()));
            return 0;
        }

        int target = quarterIndex(handoff);
        for (int i = 0; i < times.length; i++) {
            if (quarterIndex(times[i]) == target) {
                if (i != 0 && logMacroState) {
                    log.info(String.format(
                            "Population projection %s starts at %s; aligning on handoff %s "
                            + "at row %d.", csvPath.getFileName(), times[0], handoff, i));
                }
                return i;
            }
        }

        String span = times.length == 0 ? "(empty)"
                : times[0] + " to " + times[times.length - 1];
        throw new IllegalStateException(String.format(
                "Macro artifacts are not aligned: the Ramsey handoff is %s, but %s covers "
                + "%s and does not contain that quarter. That happens when only some "
                + "artifacts were regenerated after a start-year change. Re-run "
                + "the MATLAB workflow for the requested start year, or set "
                + "mm_macroModel: off.",
                handoff, csvPath.getFileName(), span));
    }

    /**
     * Load population projection CSV produced by MATLAB's {@code build_population_projection.m}.
     *
     * <p>Format: {@code time,N,P}; further columns are ignored.</p>
     *
     * @param csvPath Path to population_projection_[COUNTRY].csv
     * @return Parsed time labels, N path and P path
     * @throws IOException If the file cannot be read or parsed, or lacks N, P or time
     */
    private PopulationProjectionData loadPopulationProjection(Path csvPath) throws IOException {
        List<String> lines = Files.readAllLines(csvPath);
        var times = new ArrayList<String>();
        var nValues = new ArrayList<Double>();
        var pValues = new ArrayList<Double>();

        if (lines.isEmpty()) {
            return new PopulationProjectionData(new String[0], new double[0], new double[0]);
        }

        String[] header = MacroCsv.splitRow(lines.get(0));
        int timeCol = -1;
        int nCol = -1;
        int pCol = -1;
        for (int i = 0; i < header.length; i++) {
            String name = header[i].trim();
            if (name.equalsIgnoreCase("time")) timeCol = i;
            if (name.equalsIgnoreCase("N")) nCol = i;
            if (name.equalsIgnoreCase("P")) pCol = i;
        }

        if (nCol < 0) {
            throw new IOException("Population projection CSV missing mandatory N column: " + csvPath);
        }
        if (pCol < 0) {
            throw new IOException("Population projection CSV missing mandatory P column: " + csvPath
                    + ". The planner's consumers are the resident population; re-export the macro files.");
        }
        // Mandatory: without it the splice can only guess that row 0 is the handoff
        // quarter, which is the failure the date alignment exists to prevent.
        if (timeCol < 0) {
            throw new IOException("Population projection CSV missing mandatory time column: "
                    + csvPath + ". The splice aligns on the handoff quarter by date; "
                    + "re-export the macro files.");
        }

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] parts = MacroCsv.splitRow(line);
            if (parts.length <= nCol || parts.length <= timeCol || parts.length <= pCol) continue;

            times.add(parts[timeCol].trim().toLowerCase());
            nValues.add(Double.parseDouble(parts[nCol].trim()));
            pValues.add(Double.parseDouble(parts[pCol].trim()));
        }

        double[] n = nValues.stream().mapToDouble(Double::doubleValue).toArray();
        double[] p = pValues.stream().mapToDouble(Double::doubleValue).toArray();
        return new PopulationProjectionData(times.toArray(new String[0]), n, p);
    }

    /**
     * Hold the planner's dependency ratio P/N at its last projected value: for every index
     * after {@code lastProjectionIdx}, {@code pPath[t] = nPath[t] * pPath[last] / nPath[last]}.
     * P has no tail rule of its own; it follows employment, which carries the tapered tail.
     * Nothing changes when the projection covers the whole horizon.
     *
     * @param pPath             population path, overwritten after {@code lastProjectionIdx}
     * @param nPath             final employment path over the same quarters
     * @param lastProjectionIdx index of the last quarter taken from the projection
     * @param totalLength       number of quarters of both paths
     */
    static void applyFrozenDependencyRatio(double[] pPath, double[] nPath,
                                           int lastProjectionIdx, int totalLength) {
        double ratio = pPath[lastProjectionIdx] / nPath[lastProjectionIdx];
        for (int t = lastProjectionIdx + 1; t < totalLength; t++) {
            pPath[t] = nPath[t] * ratio;
        }
    }

    /**
     * Taper the post-projection demographic log-growth rate geometrically to zero.
     *
     * <p>The finite population projection remains authoritative through
     * {@code lastProjectionIdx}. Beyond that point, the Ramsey tail only closes
     * the terminal condition, so the recent growth rate is allowed to fade out
     * instead of being extrapolated forever or stopped with a hard kink.</p>
     */
    // Package-private rather than private so MacroModelManagerTest can assert this
    // directly against the taper-window bound. Visibility only - no behavioural difference.
    static void applySmoothTerminalDemographicClosure(
            double[] path, int lastProjectionIdx, int totalLength, int window) {
        if (path == null || lastProjectionIdx < 0 || lastProjectionIdx >= totalLength - 1) {
            return;
        }

        window = Math.min(window, lastProjectionIdx);
        if (window < 1) {
            Arrays.fill(path, lastProjectionIdx + 1, totalLength, path[lastProjectionIdx]);
            return;
        }

        double last = path[lastProjectionIdx];
        double base = path[lastProjectionIdx - window];
        if (last <= 0 || base <= 0 || !Double.isFinite(last) || !Double.isFinite(base)) {
            Arrays.fill(path, lastProjectionIdx + 1, totalLength, last);
            return;
        }

        double logGrowth = Math.log(last / base) / window;
        if (!Double.isFinite(logGrowth)) {
            Arrays.fill(path, lastProjectionIdx + 1, totalLength, last);
            return;
        }

        double rho = Math.pow(0.5, 1.0 / DEMOGRAPHIC_TAIL_HALF_LIFE_QUARTERS);
        for (int t = lastProjectionIdx + 1; t < totalLength; t++) {
            logGrowth *= rho;
            path[t] = path[t - 1] * Math.exp(logGrowth);
        }
    }

    /**
     * The DSGE pass of one year (modes {@code dsge} and {@code ramsey_dsge}), identical in every
     * year from the start year on.
     *
     * <p>The DSGE takes no input from SimPaths: its annual step receives the scenario's shocks
     * for {@code year} only (all zero without a scenario), so without a scenario the cycle wage is
     * zero and a Ramsey+DSGE run has the Ramsey-only wage. SimPaths' labour supply reaches the
     * macro side through the planner's end-of-year feedback alone
     * ({@link #applyRamseyRecursiveFeedback}).</p>
     *
     * <p>In order: the trend (the planner's path advanced one year, or zero without a planner);
     * the level diagnostics for MacroPaths.csv; the DSGE step; the wage, the trend's level times
     * the factor of the DSGE's hourly wage cycle {@code w - h} ({@link TrendCycle#totalPct},
     * {@link DSGEState#getHourlyWageDeviation}); the wage update before the labour market; the
     * realised-wage index; logging and diagnostics.</p>
     */
    private void runDsgePass(int year, Set<Person> persons) {
        if (useRamseyTrend && ramseyTrend != null && ramseyTrend.isSolved()) {
            advanceRamseyTrend();
        } else {
            computeCapitalDeepeningTrend();
        }

        // Level diagnostics for MacroPaths.csv only; nothing here enters the DSGE.
        LaborSupplyAggregator.LaborSupplyAggregates aggregates =
                laborSupplyAggregator.computeRawAggregates(persons);
        populateDemographicIndices(aggregates);
        populateLabourCategoryMetrics(aggregates);
        logLabourCategoryDistribution(year, aggregates);

        // Year and trends on the recorder before the step, so all four quarterly records carry them.
        dynamicDsgeModel.setCurrentYear(year);
        MacroPathRecorder recorder = dynamicDsgeModel.getPathRecorder();
        if (recorder != null) {
            recorder.setCurrentTrends(currentRamseyTrends);
        }

        DSGEState annualState = dynamicDsgeModel.stepAnnual(shockScenario, year);

        wageDeviation = TrendCycle.totalPct(capitalDeepeningWageAdjustment, annualState.getHourlyWageDeviation());
        inflationDeviation = annualState.getInflationDeviation();

        // Update individual wages with the new deviation before the labour market runs.
        applyMacroWages(year, persons);

        // The realised-wage index on the post-overlay wages, amended onto the quarterly
        // records just written (they were recorded before this year's index existed).
        double wageRealizedPct = populateRealizedWageIndex(persons);
        if (recorder != null && !Double.isNaN(wageRealizedPct)) {
            recorder.amendTrendForYear(year, "wage_realized", wageRealizedPct);
        }

        logDynamicEquilibrium(year, annualState, aggregates);
        updateDiagnostics(annualState);
    }

    /**
     * Verbose-only diagnostic: log the labour-supply category distribution and
     * mean hours per category for the year. Gated on
     * {@code logMacroState && verboseMacroLogging} (the same convention as the
     * other verbose macro diagnostics) so it never appears in standard runs.
     * This is the lens for the wage-feedback intensive-margin question (whether
     * a mean-hours decline is driven by mass shifting into the wide PL_1 bin).
     */
    private void logLabourCategoryDistribution(int year, LaborSupplyAggregator.LaborSupplyAggregates aggregates) {
        if (!logMacroState || !verboseMacroLogging || laborSupplyAggregator == null
                || aggregates == null || !laborSupplyAggregator.isStartYearLatched()) {
            return;
        }
        log.info(String.format("Macro Year %d — %s", year,
                laborSupplyAggregator.formatLabourCategoryDistribution(aggregates)));
    }

    /**
     * Run one simulation year with DSGE bypassed (Ramsey-only).
     *
     * <p>All DSGE cycle variables and shocks are exported as zeros, while trend
     * and total columns are exported from Ramsey trend values. This guarantees
     * x_total == x_trend when DSGE is disabled.</p>
     */
    private void runRamseyOnlyPass(int year, Set<Person> persons) {
        if (useRamseyTrend && ramseyTrend != null && ramseyTrend.isSolved()) {
            advanceRamseyTrend();
        } else {
            computeCapitalDeepeningTrend();
        }

        if (laborSupplyAggregator != null) {
            // The demographic indices emit from the start year against the eagerly
            // latched anchors; l_level/h_level and the workers-derived columns stay
            // gated on the post-labour-market latch inside populateDemographicIndices.
            LaborSupplyAggregator.LaborSupplyAggregates aggs =
                    laborSupplyAggregator.computeRawAggregates(persons);
            populateDemographicIndices(aggs);
            populateLabourCategoryMetrics(aggs);
            logLabourCategoryDistribution(year, aggs);
        }

        wageDeviation = capitalDeepeningWageAdjustment;
        inflationDeviation = 0.0;
        applyMacroWages(year, persons);

        populateRealizedWageIndex(persons);

        if (trendOnlyPathRecorder != null) {
            trendOnlyPathRecorder.setCurrentTrends(currentRamseyTrends);
            trendOnlyPathRecorder.recordRamseyOnlyYear(year);
        }

        if (logMacroState) {
            log.info(String.format(
                    "Macro Year %d (Ramsey-only): wage_total=%+.3f%% (cycle=+0.000%% + trend=%+.3f%%)",
                    year, wageDeviation, capitalDeepeningWageAdjustment));
        }
    }

    /**
     * Initialize standalone recorder mode (no DSGE, no Ramsey, no file loading).
     *
     * <p>Creates only a {@link LaborSupplyAggregator} and a {@link MacroPathRecorder}.
     * No model files are loaded from disk; the MacroModel directory is not required.
     * All DSGE/Ramsey state remains null/zero.</p>
     *
     * <p>Deliberately no {@link #requireVerifiedBundle} check: this mode reads only the
     * variable names in {@code model_info.json} to label the {@code MacroPaths.csv} columns,
     * and no number from the bundle enters the run. The default configuration
     * ({@code mm_macroModel: off}, recorder on) runs in this mode, so the check would stop
     * every macro-off run after a failed MATLAB export.</p>
     */
    private void initializeRecorderOnly(String countryCode) {
        laborSupplyAggregator = new LaborSupplyAggregator();
        laborSupplyAggregator.setLogEnabled(logMacroState);

        trendOnlyPathRecorder = pathRecordingEnabled ? new MacroPathRecorder() : null;
        String macroModelPath = resolveBundleDir(countryCode);
        if (macroModelPath != null) {
            applyRecorderSchema(macroModelPath, trendOnlyPathRecorder);
        }

        dynamicDsgeModel = null;
        shockScenario = null;
        ramseyTrend = null;
        wageDeviation = 0.0;
        inflationDeviation = 0.0;
        capitalDeepeningWageAdjustment = 0.0;
        initialized = true;

        if (logMacroState) {
            log.info("Recorder-only mode initialized: LaborSupplyAggregator + MacroPathRecorder active, "
                + "no DSGE/Ramsey model loaded, no wage feedback.");
        }
    }

    /**
     * Configure a non-DSGE recorder from the active MacroModel schema when model_info.json exists.
     * Falls back to the recorder's built-in defaults when metadata is unavailable.
     */
    private void applyRecorderSchema(String macroModelPath, MacroPathRecorder recorder) {
        if (recorder == null || macroModelPath == null || macroModelPath.isBlank()) {
            return;
        }

        Path modelInfoPath = Path.of(macroModelPath, "model_info.json");
        if (!Files.exists(modelInfoPath)) {
            if (logMacroState) {
                log.info("Macro recorder schema fallback: model_info.json not found at " + modelInfoPath);
            }
            return;
        }

        try {
            String content = Files.readString(modelInfoPath);
            String[] stateNames = extractJsonStringArray(content, "state_names");
            String[] jumpNames = extractJsonStringArray(content, "jump_names");
            String[] shockNames = extractJsonStringArray(content, "exo_names");
            recorder.setVariableNames(stateNames, jumpNames, shockNames);

            if (logMacroState) {
                log.info(String.format(
                        "Macro recorder schema loaded from %s: %d states, %d jumps, %d shocks",
                        modelInfoPath, stateNames.length, jumpNames.length, shockNames.length));
            }
        } catch (IOException e) {
            if (logMacroState) {
                log.warn("Failed to load recorder schema from " + modelInfoPath + ": " + e.getMessage());
            }
        }
    }

    /** Delegate to {@link MacroJson}, the reader shared with DSGEModel and
     *  RamseyTrendModel. Do not re-inline a local parser. */
    private static String[] extractJsonStringArray(String json, String key) {
        return MacroJson.stringArray(json, key);
    }

    /**
     * Run one simulation year in recorder-only mode.
     *
     * <p>Aggregates SimPaths behavioral labor supply into l_level, h_level,
     * participation_rate, and workers_over_atRiskPool. All DSGE state/jump/shock
     * columns remain zero. No wage or inflation feedback is applied.</p>
     *
     * @param year    Current simulation year
     * @param persons Current population
     */
    private void runRecorderOnlyPass(int year, Set<Person> persons) {
        // The demographic indices emit from the start year against the eagerly
        // latched anchors; the workers-derived columns wait for the post-market latch.
        LaborSupplyAggregator.LaborSupplyAggregates aggs =
                laborSupplyAggregator.computeRawAggregates(persons);

        // Populate the trends map with SimPaths labor supply diagnostics only
        currentRamseyTrends.clear();

        populateDemographicIndices(aggs);
        populateLabourCategoryMetrics(aggs);
        populateRealizedWageIndex(persons);
        // Emit the exogenous SimPaths real-wage trend as w_trend (= w_total in
        // this mode, since the DSGE cycle is zero). Mirrors the Person wage
        // regression's RealWageGrowth lookup so the exported series matches
        // what the wage equation actually consumes.
        populateExogenousWageTrend(year);

        logLabourCategoryDistribution(year, aggs);

        // Record to path recorder (zero DSGE state/jumps/shocks)
        if (trendOnlyPathRecorder != null) {
            trendOnlyPathRecorder.setCurrentTrends(currentRamseyTrends);
            trendOnlyPathRecorder.recordRamseyOnlyYear(year);
        }

        if (logMacroState) {
            log.info(String.format(
                    "Macro Year %d (recorder-only): L=%.0f, h=%.2f, participation=%.1f%%, workers/atRisk=%.1f%%",
                    year, aggs.laborForce(),
                    aggs.avgHoursPerWorker(),
                    aggs.participationRate() * 100.0,
                    aggs.atRiskPool() > 0
                        ? 100.0 * aggs.laborForce() / aggs.atRiskPool()
                        : 0.0));
        }
    }

    /**
     * Populate demographic level indices in the trends map.
     *
     * <p>Writes l_level, h_level, participation_rate, workers_over_atRiskPool,
     * total_population, wap_level, and atrisk_level into {@code currentRamseyTrends}.
     * All level indices are expressed as % change from the start year.</p>
     *
     * @param aggregates Current labor supply aggregates
     */
    private void populateDemographicIndices(LaborSupplyAggregator.LaborSupplyAggregates aggregates) {
        currentRamseyTrends.putAll(demographicIndexUpdates(aggregates));
    }

    /**
     * The SimPaths-aggregated level columns for one set of aggregates.
     *
     * <p>Pure: reads only the aggregator's latched start-year anchors and the
     * supplied aggregates. Both writers use it — the in-year pass through
     * {@link #populateDemographicIndices} and the end-of-year re-expression in
     * {@link #buildPostAlignmentLevelUpdates} — which is the point: with two
     * copies of these formulas, a divergence would silently corrupt the
     * post-alignment level columns of MacroPaths.csv while the in-year ones stay
     * right.</p>
     *
     * <p>participation_rate and workers_over_atRiskPool are workers/L-derived:
     * they are only meaningful once L comes from the SimPaths labour-supply model,
     * i.e. post-labour-market (isStartYearLatched() is true only then). Emitting them
     * in the eager year-1 pre-labour-market pass would expose input-DB hours and,
     * because compare_scenarios.do anchors pr0/ar0 on the first non-zero value,
     * corrupt its WAP/atRisk reconstruction. Only the labour-market-invariant
     * head-count anchors are safe to emit from year 1.</p>
     */
    private Map<String, Double> demographicIndexUpdates(
            LaborSupplyAggregator.LaborSupplyAggregates aggregates) {
        Map<String, Double> m = new LinkedHashMap<>();

        double startL = laborSupplyAggregator.getStartYearL();
        double startH = laborSupplyAggregator.getStartYearH();
        double startTotalPop = laborSupplyAggregator.getStartYearTotalPop();
        double startWAP = laborSupplyAggregator.getStartYearWAP();
        double startAtRisk = laborSupplyAggregator.getStartYearAtRisk();

        if (startL > 0.0) {
            m.put("l_level", (aggregates.laborForce() / startL - 1.0) * 100.0);
        }
        if (startH > 0.0) {
            m.put("h_level", (aggregates.avgHoursPerWorker() / startH - 1.0) * 100.0);
        }
        if (laborSupplyAggregator.isStartYearLatched()) {
            m.put("participation_rate", aggregates.participationRate() * 100.0);
            m.put("workers_over_atRiskPool",
                    aggregates.atRiskPool() > 0
                        ? 100.0 * aggregates.laborForce() / aggregates.atRiskPool()
                        : 0.0);
        }
        if (startTotalPop > 0.0) {
            m.put("total_population",
                    ((double) aggregates.totalPopulation() / startTotalPop - 1.0) * 100.0);
        }
        if (startWAP > 0.0) {
            m.put("wap_level",
                    ((double) aggregates.workingAgePopulation() / startWAP - 1.0) * 100.0);
        }
        if (startAtRisk > 0.0) {
            m.put("atrisk_level",
                    ((double) aggregates.atRiskPool() / startAtRisk - 1.0) * 100.0);
        }
        return m;
    }

    /**
     * Compute the mean {@code labWageHrly} over the at-risk pool — i.e. the
     * exact population that enters the SimPaths discrete-choice labour supply
     * model ({@link LaborSupplyAggregator#isInAtRiskPool}) — latch the start-
     * year anchor on first call, and write the resulting % change from the
     * start year ({@code (mean / anchor - 1) * 100}) into
     * {@link #currentRamseyTrends} under the key {@code wage_realized}.
     *
     * <p>The series is intentionally always constructed — including in
     * recorder-only and Ramsey-only modes — so the wage faced by labour-supply
     * decisions remains visible alongside (or in the absence of) the macro
     * multiplier. Averaging over the at-risk
     * pool aligns the wage diagnostic with the participation / hours / labour-
     * category panels in the same MacroPaths.csv, which all use the same gate.
     * Units (% from first year) match {@code w_trend} / {@code w_total} so the
     * realized wage and the exogenous / Ramsey wage trends can be plotted on a
     * common axis.</p>
     *
     * @param persons SimPaths person set
     * @return the computed % change value, or {@link Double#NaN} when no at-
     *         risk person has a valid wage (in which case the trends map is
     *         left untouched)
     */
    private double populateRealizedWageIndex(Set<Person> persons) {
        currentRamseyTrends.remove("wage_realized");
        if (persons == null || persons.isEmpty() || laborSupplyAggregator == null) {
            return Double.NaN;
        }
        double sum = 0.0;
        int count = 0;
        for (Person p : persons) {
            if (!laborSupplyAggregator.isInAtRiskPool(p)) {
                continue;
            }
            double w = p.getLabWageFullTimeHrly();
            if (w > 0.0) {
                sum += w;
                count++;
            }
        }
        if (count == 0) {
            return Double.NaN;
        }
        double mean = sum / count;
        if (startYearAtRiskWage == 0.0) {
            startYearAtRiskWage = mean;
        }
        double pctFromFirstYear = (mean / startYearAtRiskWage - 1.0) * 100.0;
        currentRamseyTrends.put("wage_realized", pctFromFirstYear);
        return pctFromFirstYear;
    }

    /**
     * Recorder-only path: emit the exogenous SimPaths real-wage trend as
     * {@code currentRamseyTrends["w"]} so the recorder writes it to the
     * {@code w_trend} (and, since the DSGE cycle is zero in this mode,
     * identically to the {@code w_total}) column. The series is the
     * {@code wage_growth} index from {@code economic_time_series.xlsx} (or its
     * geometric out-of-sample extrapolation, transparently handled by
     * {@link Parameters#getTimeSeriesIndex}) expressed as
     * {@code (idx_t / idx_startYear - 1) * 100} so the units match the macro-
     * on Ramsey {@code w_trend}.
     */
    private void populateExogenousWageTrend(int year) {
        if (simulationStartYear <= 0) {
            return;
        }
        Double idxNow = Parameters.getTimeSeriesIndex(year, UpratingCase.Earnings);
        Double idxStart = Parameters.getTimeSeriesIndex(simulationStartYear, UpratingCase.Earnings);
        if (idxNow == null || idxStart == null || idxStart == 0.0) {
            return;
        }
        double wTrendPct = (idxNow / idxStart - 1.0) * 100.0;
        currentRamseyTrends.put("w", wTrendPct);
    }

    private void populateLabourCategoryMetrics(LaborSupplyAggregator.LaborSupplyAggregates aggregates) {
        currentRamseyTrends.putAll(labourCategoryUpdates(aggregates));
    }

    /**
     * The four labour-category share/mean-hours columns for one set of
     * aggregates, padded with zeros when fewer than four categories are present.
     * Shared with {@link #buildPostAlignmentLevelUpdates} for the same reason as
     * {@link #demographicIndexUpdates}.
     */
    private Map<String, Double> labourCategoryUpdates(
            LaborSupplyAggregator.LaborSupplyAggregates aggregates) {
        Map<String, Double> m = new LinkedHashMap<>();
        int index = 0;
        for (LaborSupplyAggregator.LabourCategorySummary summary
                : aggregates.categoryDistribution().values()) {
            if (index >= 4) {
                break;
            }
            m.put("cat_" + index + "_share", summary.share());
            m.put("cat_" + index + "_meanHours", summary.meanHours());
            index++;
        }
        while (index < 4) {
            m.put("cat_" + index + "_share", 0.0);
            m.put("cat_" + index + "_meanHours", 0.0);
            index++;
        }
        return m;
    }

    /**
     * Log the year's DSGE state and wage decomposition (DSGE layer on).
     */
    private void logDynamicEquilibrium(
            int year,
            DSGEState state,
            LaborSupplyAggregator.LaborSupplyAggregates aggregates
    ) {
        boolean scenarioApplied = shockScenario != null && shockScenario.hasShocksForYear(year);
        if (logMacroState) {
            System.out.println("\n========== DSGE MACRO MODEL UPDATE ==========");
            System.out.println(String.format("Year: %d", year));
            System.out.println("--- Labor Supply Aggregation (diagnostic; not a DSGE input) ---");
            System.out.println(String.format("  Participants         %.0f (extensive margin, hours > 0)", aggregates.laborForce()));
            System.out.println(String.format("  At-risk pool:        %.0f (non-student/retired 15-64)", aggregates.atRiskPool()));
            System.out.println(String.format("  Avg hours/week:      %.1f", aggregates.avgHoursPerWorker()));
            System.out.println(String.format("  Participation rate:  %.1f%%", aggregates.participationRate() * 100));
            System.out.println("--- DSGE Shocks ---");
            System.out.println(String.format("  Scenario shocks:     %s", scenarioApplied ? "APPLIED" : "none"));
            System.out.println("--- DSGE State Evolution (Annual Average) ---");
            System.out.println(String.format("  Wage per worker:     %+.4f%%", state.getWageDeviation()));
            System.out.println(String.format("  Inflation deviation: %+.4f pp", state.getInflationDeviation()));
            System.out.println(String.format("  Output deviation:    %+.4f%%", state.getOutputDeviation()));
            System.out.println(String.format("  Employment dev:      %+.4f%%", state.getEmploymentDeviation()));
            System.out.println(String.format("  Labor force dev:     %+.4f%%", state.getLaborForceDeviation()));
            System.out.println(String.format("  Hours deviation:     %+.4f%%", state.getHoursDeviation()));
            System.out.println("--- Wage Decomposition (total = trend level x cycle factor) ---");
            System.out.println(String.format("  DSGE cycle per hour: %+.4f%% (w - h, 100 x log)", state.getHourlyWageDeviation()));
            String trendLabel = (useRamseyTrend && ramseyTrend != null) ? "Ramsey trend" : "No trend layer";
            System.out.println(String.format("  %s:   %+.4f%%", trendLabel, capitalDeepeningWageAdjustment));
            System.out.println(String.format("  Total wage dev:      %+.4f%%", wageDeviation));
            System.out.println("--- Macro Wage Multiplier ---");
            System.out.println(String.format("  Multiplier:          %.6f (= 1 + %.4f/100)",
                1.0 + wageDeviation / 100.0, wageDeviation));
            System.out.println("=======================================================\n");
        }

        if (logMacroState) {
            log.info(String.format("DSGE Year %d: wage_dev=%.3f%% (trend=%.3f%% times hourly cycle=%.3f%%), inflation_dev=%.3f%%, scenario %s",
                year, wageDeviation, capitalDeepeningWageAdjustment, state.getHourlyWageDeviation(),
                inflationDeviation, scenarioApplied ? "APPLIED" : "none"));
        }
    }

    /**
     * Reset counters at start of each year.
     */
    private void resetCounters() {
        wageAdjustmentCount = 0;
        wageAdjustmentSum = 0.0;
    }

    private void resetDiagnostics() {
        diagnosticsCount = 0;
        wageDevMin = Double.POSITIVE_INFINITY;
        wageDevMax = Double.NEGATIVE_INFINITY;
        inflDevMin = Double.POSITIVE_INFINITY;
        inflDevMax = Double.NEGATIVE_INFINITY;
        outputDevMin = Double.POSITIVE_INFINITY;
        outputDevMax = Double.NEGATIVE_INFINITY;
        employmentDevMin = Double.POSITIVE_INFINITY;
        employmentDevMax = Double.NEGATIVE_INFINITY;
        wageDevSum = 0.0;
        inflDevSum = 0.0;
        outputDevSum = 0.0;
        employmentDevSum = 0.0;
    }

    private void updateDiagnostics(DSGEState state) {
        if (!logMacroState || state == null) {
            return;
        }
        updateDiagnosticsValues(state.getWageDeviation(), state.getInflationDeviation(),
                state.getOutputDeviation(), state.getEmploymentDeviation());
    }

    private void updateDiagnosticsValues(double wageDev, double inflDev, double outputDev, double employmentDev) {
        diagnosticsCount++;

        wageDevMin = Math.min(wageDevMin, wageDev);
        wageDevMax = Math.max(wageDevMax, wageDev);
        inflDevMin = Math.min(inflDevMin, inflDev);
        inflDevMax = Math.max(inflDevMax, inflDev);
        outputDevMin = Math.min(outputDevMin, outputDev);
        outputDevMax = Math.max(outputDevMax, outputDev);
        employmentDevMin = Math.min(employmentDevMin, employmentDev);
        employmentDevMax = Math.max(employmentDevMax, employmentDev);

        wageDevSum += wageDev;
        inflDevSum += inflDev;
        outputDevSum += outputDev;
        employmentDevSum += employmentDev;
    }

    public void logDiagnosticsSummary() {
        if (!logMacroState || diagnosticsCount == 0) {
            return;
        }

        log.info(String.format(
                "DSGE Diagnostics Summary: n=%d | wage[min=%.4f,max=%.4f,mean=%.4f] " +
                "pi[min=%.4f,max=%.4f,mean=%.4f] | y[min=%.4f,max=%.4f,mean=%.4f] | n[min=%.4f,max=%.4f,mean=%.4f]",
                diagnosticsCount,
                wageDevMin, wageDevMax, wageDevSum / diagnosticsCount,
                inflDevMin, inflDevMax, inflDevSum / diagnosticsCount,
                outputDevMin, outputDevMax, outputDevSum / diagnosticsCount,
                employmentDevMin, employmentDevMax, employmentDevSum / diagnosticsCount
        ));
    }

    private void applyMacroWages(int year, Set<Person> persons) {
        // The UK first-year schedule uses the input population's wages and hours.
        if (year == simulationStartYear || wageDeviation == 0.0) {
            return;
        }
        for (Person person : persons) {
            Double originalWage = person.applyMacroHourlyWage(wageDeviation);
            if (originalWage != null) {
                trackWageAdjustment(person, originalWage, person.getLabWageFullTimeHrly(), wageDeviation);
            }
        }
    }

    // ========== Tracking Methods ==========

    /**
     * Track a wage adjustment for logging purposes.
     *
     * <p>Called when a macro wage adjustment is applied.</p>
     *
     * @param person        the person whose wage is being adjusted
     * @param originalWage  wage before DSGE adjustment
     * @param adjustedWage  wage after DSGE adjustment
     * @param deviation     the DSGE wage deviation in percent
     */
    private void trackWageAdjustment(Person person, double originalWage, double adjustedWage, double deviation) {
        int count = ++wageAdjustmentCount;
        wageAdjustmentSum += adjustedWage - originalWage;

        // Log sample individuals (first 3 each iteration)
        if (logMacroState && verboseMacroLogging && count <= 3) {
            System.out.println(String.format(
                "  [Wage Adj] Person %d (%s, age %d): %.2f → %.2f GBP/hr (%.3f%% deviation)",
                person.getKey().getId(), person.getDemMaleFlag(), person.getDemAge(),
                originalWage, adjustedWage, deviation));
        }
    }

    // ========== Getters/Setters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Whether recording infrastructure is active (either full macro model or standalone recorder).
     * Use this as the gate for operations that should work in both modes:
     * the start-year anchor latch, labor supply diagnostics, path export.
     */
    public boolean isRecorderActive() {
        return enabled || recorderOnlyMode;
    }

    public boolean isRecorderOnlyMode() {
        return recorderOnlyMode;
    }

    public void setRecorderOnlyMode(boolean recorderOnlyMode) {
        this.recorderOnlyMode = recorderOnlyMode;
    }

    public boolean isPathRecordingEnabled() {
        return pathRecordingEnabled;
    }

    public void setPathRecordingEnabled(boolean pathRecordingEnabled) {
        this.pathRecordingEnabled = pathRecordingEnabled;
    }

    public boolean isUseDsge() {
        return useDsge;
    }

    public void setUseDsge(boolean useDsge) {
        this.useDsge = useDsge;
    }

    public boolean isLogMacroState() {
        return logMacroState;
    }

    public void setLogMacroState(boolean logMacroState) {
        this.logMacroState = logMacroState;
        if (laborSupplyAggregator != null) {
            laborSupplyAggregator.setLogEnabled(logMacroState);
        }
        if (dynamicDsgeModel != null) {
            dynamicDsgeModel.setLogEnabled(logMacroState);
        }
    }

    public boolean isVerboseMacroLogging() {
        return verboseMacroLogging;
    }

    public void setVerboseMacroLogging(boolean verboseMacroLogging) {
        this.verboseMacroLogging = verboseMacroLogging;
    }

    public String getDsgeShockScenario() {
        return dsgeShockScenario;
    }

    public void setDsgeShockScenario(String dsgeShockScenario) {
        this.dsgeShockScenario = dsgeShockScenario;
    }

    public String getRamseyScenario() {
        return ramseyScenario;
    }

    public void setRamseyScenario(String ramseyScenario) {
        this.ramseyScenario = ramseyScenario;
    }

    public void setMacroBundleDir(String macroBundleDir) {
        this.macroBundleDir = macroBundleDir;
    }

    /**
     * The bundle folder this run reads: {@code mm_macroBundleDir} when set, otherwise
     * {@code input/{countryCode}/MacroModel}. A second bundle, such as one handed off at an
     * earlier quarter for a backtest, needs its own folder because {@code BundleProvenanceTest}
     * (contract 18) hashes the country default.
     *
     * @return the folder, or {@code null} when neither a folder nor a country is given
     */
    String resolveBundleDir(String countryCode) {
        if (macroBundleDir != null && !macroBundleDir.isBlank()) {
            return macroBundleDir.trim();
        }
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }
        return Path.of(Parameters.getInputDirectory(), countryCode, "MacroModel").toString();
    }

    public double getMaxBounds() {
        return maxBounds;
    }

    public void setMaxBounds(double maxBounds) {
        this.maxBounds = maxBounds;
        if (dynamicDsgeModel != null) {
            dynamicDsgeModel.setMaxStateDeviation(maxBounds);
        }
    }

    public boolean isUseRamseyTrend() {
        return useRamseyTrend;
    }

    public void setUseRamseyTrend(boolean useRamseyTrend) {
        this.useRamseyTrend = useRamseyTrend;
    }

    public void setRamseyFeedbackPersistence(double value) { this.ramseyFeedbackPersistence = value; }

    public void setRamseyFeedbackReferenceYear(int value) { this.ramseyFeedbackReferenceYear = value; }

    /**
     * Get the Ramsey trend model (for testing/diagnostics).
     * @return RamseyTrendModel or null if not initialized
     */
    public RamseyTrendModel getRamseyTrend() {
        return ramseyTrend;
    }

    /**
     * Get the current Ramsey trend values (for testing/diagnostics).
     *
    * <p>Keys include: w, y, c, inv, k, g, nx, n_ramsey.
     * Values are percentage changes from the first simulation year baseline.</p>
     *
     * @return Unmodifiable map of trend names to percentage changes, or empty if unavailable
     */
    public Map<String, Double> getCurrentRamseyTrends() {
        return Collections.unmodifiableMap(currentRamseyTrends);
    }

    /**
     * Get the current capital-deepening wage trend adjustment (in %).
     *
     * <p>This is the secular trend component added on top of the DSGE's
     * business-cycle wage deviation. Positive means wages are above what
     * the DSGE alone would predict, due to rising K/L ratio.</p>
     *
     * @return capital-deepening wage adjustment in percent
     */
    public double getCapitalDeepeningWageAdjustment() {
        return capitalDeepeningWageAdjustment;
    }

    /**
     * Get current DSGE wage deviation (% from steady state).
     *
     * <p>Positive means wages are above steady state.</p>
     *
     * @return wage deviation in percent (e.g., 2.5 means 2.5% above steady state)
     */
    public double getWageDeviation() {
        return wageDeviation;
    }

    /**
     * Get current DSGE inflation deviation (percentage points from steady state).
     *
     * @return inflation deviation in percentage points
     */
    public double getInflationDeviation() {
        return inflationDeviation;
    }

    /**
     * Check if DSGE model has been initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Export the recorded macro paths to {@code csv/MacroPaths.csv}.
     *
     * @param outputFolder The experiment's output folder (where csv/ subdirectory will be created)
     */
    public void exportPaths(String outputFolder) {
        if ((!enabled && !recorderOnlyMode) || !initialized || !pathRecordingEnabled) {
            return;  // Nothing to export
        }

        try {
            if (recorderOnlyMode || !useDsge) {
                if (trendOnlyPathRecorder == null || !trendOnlyPathRecorder.hasData()) {
                    if (logMacroState) {
                        String mode = recorderOnlyMode ? "recorder-only" : "Ramsey-only";
                        log.warn("Macro path export skipped: recorder has no " + mode + " data");
                    }
                    return;
                }
                trendOnlyPathRecorder.exportToSimPathsOutput(outputFolder, MACRO_PATHS_FILENAME);
                if (logMacroState) {
                    String mode = recorderOnlyMode ? "recorder-only" : "Ramsey-only";
                    log.info("Macro paths exported (" + mode + "): " + trendOnlyPathRecorder.getSummary());
                }
                return;
            }

            if (dynamicDsgeModel == null) {
                if (logMacroState) {
                    log.warn("DSGE path export skipped: dynamic model not initialized");
                }
                return;
            }
            dynamicDsgeModel.exportPaths(outputFolder, MACRO_PATHS_FILENAME);
            MacroPathRecorder recorder = dynamicDsgeModel.getPathRecorder();
            if (recorder != null && recorder.hasData() && logMacroState) {
                log.info("DSGE paths exported: " + recorder.getSummary());
            }
        } catch (IOException e) {
            if (logMacroState) {
                log.error("Failed to export DSGE paths: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Get path recording status summary.
     * @return Summary string or null if not available
     */
    public String getPathRecorderSummary() {
        if (!enabled && !recorderOnlyMode) {
            return null;
        }
        if (recorderOnlyMode || !useDsge) {
            return (trendOnlyPathRecorder != null) ? trendOnlyPathRecorder.getSummary() : null;
        }
        if (dynamicDsgeModel == null) return null;
        MacroPathRecorder recorder = dynamicDsgeModel.getPathRecorder();
        return (recorder != null) ? recorder.getSummary() : null;
    }
}
