package simpaths.model.macro;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Ramsey optimal growth model providing secular trend paths for wages, output,
 * capital, and the natural interest rate.
 *
 * <p>Solves the deterministic Ramsey optimal savings problem:</p>
 * <pre>
 *   max  Σ β^t P_t u(c_t),   u(c) = (c^{1-σ} - 1)/(1-σ)
 *   s.t. K_{t+1} = (1-δ)K_t + Y_t - P_t·c_t - (g_Y(t) + nx_Y(t))·Y_t
 *        Y_t = A_t · K_t^α · (N_t·h_t)^{1-α}
 *        A_{t+1} = (1 + g_A) · A_t
 *
 *   C_t = P_t·c_t,   I_t = Y_t - C_t - G_t - NX_t,
 *   w_t = (1-α)·Y_t/(N_t·h_t),   r_t = α·Y_t/K_t - δ
 * </pre>
 *
 * <p>where P_t is the resident population of all ages (the consumers, one head one
 * weight), c_t consumption per head, N_t the number of persons in employment, h_t
 * hours per person employed, and g_Y(t) and nx_Y(t) the government spending and net
 * exports shares of GDP. The exogenous paths are A, N, h, P, g_Y and nx_Y. The
 * population weight cancels from the first-order conditions, so the Euler equation
 * is c_{t+1}/c_t = [β(1+r_{t+1})]^{1/σ}, unaffected by P, G and NX, and the
 * terminal BGP K/Y ratio is pinned down by the growth-corrected modified golden
 * rule and the implied terminal MPK. The level of P is a pure scale: scaling the
 * whole path scales c by its inverse and leaves K, Y, C, w and r unchanged, so P moves
 * the allocation only through the time shape of its path; labour input N·h moves it
 * through capital per worker whatever P does. Beyond the last quarter passed to
 * {@link #solveForPath} the dependency ratio P/N is held at its value in that
 * quarter, so N/P is constant wherever the terminal closure acts.</p>
 *
 * <p>Solved via a stacked-time Newton (LBJ) perfect-foresight solve. The
 * transversality condition is approximated by requiring K/Y to converge to
 * the balanced growth path ratio at the terminal date (extended well beyond
 * the simulation horizon).</p>
 *
 * <h2>Two usage modes</h2>
 * <ol>
 *   <li><b>MATLAB (estimation pipeline)</b>: Detrend historical observables.
 *       Implemented in {@code ramsey_trend.m} with identical algorithm.</li>
 *   <li><b>Java (SimPaths runtime)</b>: Compute forward trend paths from
 *       the terminal state of the estimation sample, using the projected
 *       employment, hours and population as exogenous inputs.</li>
 * </ol>
 *
 * <p>Both implementations read the same {@code growth_params_{COUNTRY}.json}
 * and must produce identical results (verified by {@code RamseyTrendModelTest}
 * at a relative tolerance of 1e-9). Labour input is exogenous on both sides.</p>
 *
 * <h2>SimPaths integration</h2>
 * <p>In forward projection mode, the model initializes from the terminal
 * capital stock and TFP level ({@code growth_terminal_state.json}) and
 * solves the full path at initialization. It then steps through the
 * solved path year by year, returning trend state (wage, output, r*) at
 * each step. At the end of every simulated year {@link MacroModelManager}
 * re-solves the remaining path from the predetermined capital stock, on a
 * labour path revised by SimPaths' realized employment and hours.</p>
 *
 * @author WELLSIM / DSGE-SimPaths Integration
 * @version 1.0
 * @see "input_processing/macromodel/docs/Technical_Documentation_Macromodel.md"
 */
public class RamseyTrendModel {

    private static final Logger log = LogManager.getLogger(RamseyTrendModel.class);

    // ========== Structural Parameters (from JSON) ==========

    /** Capital share in Cobb-Douglas production function */
    private double alphaK;

    /** Quarterly depreciation rate (derived from annual via 1-(1-δ_a)^(1/4)) */
    private double deltaQ;

    /** Quarterly discount factor (derived from annual via β_a^(1/4)) */
    private double betaQ;

    /** CRRA coefficient (inverse of intertemporal elasticity of substitution) */
    private double sigma;

    /** Quarterly TFP growth rate (derived from annual via (1+g_A_a)^(1/4) - 1) */
    private double gAQ;

    // ========== Annual Parameter Values (stored for scenario deviation conversion) ==========

    /** Annual depreciation rate (baseline, from JSON) */
    private double deltaAnnual;

    /** Annual discount factor (baseline, from JSON) */
    private double betaAnnual;

    /** Annual TFP growth rate (baseline, from JSON) */
    private double gAAnnual;

    /** Whether the baseline JSON specifies terminal TFP-growth convergence */
    private boolean terminalGrowthEnabled;

    /** Terminal annual TFP growth rate used for long-run BGP targeting */
    private double gATerminalAnnual;

    /** Terminal quarterly TFP growth rate used for long-run BGP targeting */
    private double gATerminalQ;

    /** Quarterly AR(1) persistence governing convergence toward terminal TFP growth */
    private double terminalGrowthRhoQ;

    // ========== Solver Settings ==========

    /** Number of tail quarters beyond the main horizon used for terminal closure */
    private int terminalExtensionQuarters;

    /**
     * Phase offset (in quarters) applied to the terminal-growth TFP convergence when the
     * solve starts mid-horizon (recursive-feedback re-solve). The convergence
     * g(q) = g_terminal + (g0 - g_terminal) * rho^q must continue from the original
     * handoff quarter, not restart at the re-solve's quarter 0. Applies to the next
     * solveForPath call. Default 0 preserves existing behavior exactly.
     */
    private int tfpPhaseOffsetQuarters = 0;

    /** Newton convergence tolerance */
    private double tolerance;

    /** Maximum Newton iterations */
    private int maxIterations;

    // ========== State ==========

    /** Current capital stock (evolves quarterly) */
    private double K;

    /** Current TFP level (evolves quarterly) */
    private double A;

    /** Whether parameters have been loaded */
    private boolean paramsLoaded = false;

    /** Whether the model has been solved */
    private boolean solved = false;

    /** Whether logging is enabled */
    private boolean logEnabled = false;

    // ========== Scenario ==========

    /** Optional long-run counterfactual scenario (time-varying parameter deviations) */
    private RamseyScenario scenario;

    /** Calendar year corresponding to quarter index 0 of the solved path */
    private int scenarioStartYear;

    // ========== Solved Path (quarterly resolution) ==========

    /** Per-quarter alpha_K (constant if no scenario, time-varying with scenario) */
    private double[] solvedAlpha;

    /** Per-quarter delta_Q (constant if no scenario, time-varying with scenario) */
    private double[] solvedDelta;

    /** Solved per-capita consumption path */
    private double[] solvedC;

    /** Solved capital path */
    private double[] solvedK;

    /** Solved output path */
    private double[] solvedY;

    /** Exogenous employment path (persons in employment), extended over the terminal tail */
    private double[] pathN;

    /** Exogenous hours per person employed path, extended over the terminal tail */
    private double[] pathH;

    /**
     * Exogenous resident population path, all ages (the planner's consumers), extended
     * over the terminal tail at the dependency ratio of the last quarter passed in
     */
    private double[] pathP;

    /** Exogenous TFP path */
    private double[] pathA;

    /** Exogenous government spending share of GDP (g_Y) path */
    private double[] pathGY;

    /** Exogenous net exports share of GDP (nx_Y) path */
    private double[] pathNXY;

    /** Total quarters in simulation (excluding extension) */
    private int simQuarters;

    /** Current quarterly step index (0-based, advances 4 per year) */
    private int currentQuarterIdx;

    // ========== BGP Reference ==========

    /** Balanced growth path capital-output ratio (quarterly) */
    private double kyBgp;

    // ========== Constructor ==========

    /**
     * Create a new Ramsey trend model (uninitialized).
     * Call {@link #loadParams(Path)} and then {@link #solveForPath} to initialize.
     */
    public RamseyTrendModel() {
        // Default state — not yet loaded
    }

    // ========== Parameter Loading ==========

    /**
     * Load structural parameters from a growth_params JSON file.
     *
     * @param jsonPath Path to growth_params_{COUNTRY}.json
     * @throws IOException If the file cannot be read
     * @throws IllegalArgumentException If required parameters are missing
     */
    public void loadParams(Path jsonPath) throws IOException {
        String content = Files.readString(jsonPath);

        // Structural parameters
        alphaK = requireJsonDouble(content, "alpha_K");
        double deltaAnnualVal = requireJsonDouble(content, "delta_annual");
        double betaAnnualVal = requireJsonDouble(content, "beta_annual");
        sigma = requireJsonDouble(content, "sigma");
        double gAAnnualVal = requireJsonDouble(content, "tfp_growth_annual");

        // Store annual values for scenario deviation conversion
        deltaAnnual = deltaAnnualVal;
        betaAnnual = betaAnnualVal;
        gAAnnual = gAAnnualVal;

        terminalGrowthEnabled = false;
        gATerminalAnnual = gAAnnualVal;
        terminalGrowthRhoQ = Double.NaN;

        // Convert to quarterly
        deltaQ = 1.0 - Math.pow(1.0 - deltaAnnualVal, 0.25);
        betaQ = Math.pow(betaAnnualVal, 0.25);
        gAQ = Math.pow(1.0 + gAAnnualVal, 0.25) - 1.0;
        gATerminalQ = gAQ;

        String terminalGrowthJson = extractJsonObject(content, "terminal_growth");
        if (terminalGrowthJson != null && extractJsonBoolean(terminalGrowthJson, "enabled", false)) {
            terminalGrowthEnabled = true;
            gATerminalAnnual = requireJsonDouble(terminalGrowthJson, "g_A_terminal_annual");
            if (gATerminalAnnual <= 0) {
                throw new IllegalArgumentException("g_A_terminal_annual must be positive when terminal growth is enabled");
            }
            terminalGrowthRhoQ = extractJsonDouble(terminalGrowthJson, "rho_quarterly");
            if (terminalGrowthRhoQ == 0.0) {
                terminalGrowthRhoQ = 0.99;
            }
            if (!Double.isFinite(terminalGrowthRhoQ) || terminalGrowthRhoQ <= 0 || terminalGrowthRhoQ >= 1) {
                throw new IllegalArgumentException("terminal_growth.rho_quarterly must lie in (0,1), got: " + terminalGrowthRhoQ);
            }
            gATerminalQ = annualToQuarterlyGrowth(gATerminalAnnual);
        }

        // Solver settings
        terminalExtensionQuarters = extractJsonIntEither(content,
            "terminal_tail_quarters", "terminal_extension_quarters", 300);
        tolerance = extractJsonDoubleEither(content,
            "newton_tolerance", "tolerance", 1e-10);
        maxIterations = extractJsonIntEither(content,
            "max_newton_iterations", "max_iterations", 200);

        if (terminalExtensionQuarters <= 0) terminalExtensionQuarters = 300;
        if (tolerance <= 0) tolerance = 1e-10;
        if (maxIterations <= 0) maxIterations = 200;

        // BGP capital-output ratio uses terminal-period growth, not the zero-growth shortcut.
        kyBgp = calculateBgpCapitalOutputRatio(alphaK, deltaQ, betaQ, sigma, gATerminalQ);

        // Labour input is exogenous. A bundle that declares labor_supply=endogenous was
        // exported against a different model (a participation FOC on phi/psi): its N path
        // is l*WAP and its reference CSV carries columns this solver does not read, so
        // refuse it instead of solving it as if it were exogenous. An absent key or
        // "exogenous" is the normal case.
        String laborSupplyMode = extractJsonString(content, "labor_supply");
        if ("endogenous".equalsIgnoreCase(laborSupplyMode)) {
            throw new IllegalArgumentException(
                    "growth_params declares labor_supply=endogenous, but the planner takes labour "
                    + "input as exogenous and has no participation choice. Re-export the bundle: "
                    + jsonPath);
        }

        validateParams();
        paramsLoaded = true;

        if (logEnabled) {
            log.info(String.format("Ramsey params loaded: alpha=%.3f, delta_q=%.6f, beta_q=%.6f, sigma=%.3f, g_A_q=%.6f",
                    alphaK, deltaQ, betaQ, sigma, gAQ));
            log.info(String.format("  K/Y (BGP) = %.4f", kyBgp));
            if (terminalGrowthEnabled) {
                log.info(String.format("  Terminal TFP growth convergence: g_A_a %.4f -> %.4f, rho_q=%.4f",
                        gAAnnual, gATerminalAnnual, terminalGrowthRhoQ));
            }
            log.info("  Labor supply: exogenous (N taken as given)");
        }
    }

    /**
     * Load the terminal state (K, A) from growth_terminal_state.json.
     * Used for forward projection from the end of the estimation sample.
     *
     * @param jsonPath Path to growth_terminal_state.json
     * @throws IOException If the file cannot be read
     */
    public void loadTerminalState(Path jsonPath) throws IOException {
        String content = Files.readString(jsonPath);
        K = extractJsonDouble(content, "K_terminal");
        A = extractJsonDouble(content, "A_terminal");

        if (K <= 0 || A <= 0) {
            throw new IllegalArgumentException(String.format(
                    "Invalid terminal state: K=%.2f, A=%.6f (must be positive)", K, A));
        }

        if (logEnabled) {
            log.info(String.format("Terminal state loaded: K=%.2f, A=%.8f", K, A));
        }
    }

    // ========== Scenario Support ==========

    /**
     * Set a Ramsey scenario for long-run counterfactual analysis.
     *
     * <p>When a scenario is set, {@link #solveForPath} will use time-varying parameters
     * (baseline + deviation) instead of constant baseline values. The scenario deviations
     * are keyed by calendar year, so the start year mapping must also be provided.</p>
     *
     * <p>Call this <b>before</b> {@link #solveForPath}. If called after solving, the model
     * must be re-solved to reflect the scenario.</p>
     *
     * @param scenario  the Ramsey scenario (may be null to clear)
     * @param startYear calendar year corresponding to quarter index 0 of the solved path
     */
    public void setScenario(RamseyScenario scenario, int startYear) {
        this.scenario = scenario;
        this.scenarioStartYear = startYear;
        this.solved = false;  // force re-solve with new scenario
        if (logEnabled && scenario != null && scenario.isLoaded()) {
            log.info("Ramsey scenario set: " + scenario.getSummary()
                    + " (start year mapping: " + startYear + ")");
        }
    }

    /**
     * Check whether a scenario is active.
     *
     * @return true if a loaded scenario is set
     */
    public boolean hasScenario() {
        return scenario != null && scenario.isLoaded() && scenario.hasAnyDeviations();
    }

    // ========== Solving ==========

    /**
     * Solve the Ramsey model for the optimal consumption and capital paths.
     *
     * <p>Given exogenous paths for employment (N, persons in employment), hours per person
     * employed (h), the resident population (P), the government spending share (gY), the net
     * exports share (nxY), initial capital K_0 and initial TFP A_0, this method solves the full
     * stacked perfect-foresight system subject to the Euler equation and the terminal
     * transversality condition. Beyond {@code quarters} the population follows the extended
     * employment path at the dependency ratio P/N of quarter {@code quarters - 1}.</p>
     *
     * @param K0      Initial capital stock
     * @param A0      Initial TFP level
     * @param N       Exogenous employment path (persons in employment, quarterly, length = quarters)
     * @param h       Exogenous hours per person employed path (quarterly, length = quarters)
     * @param P       Exogenous resident population path, all ages (quarterly, length = quarters)
     * @param gY      Exogenous government spending share path (quarterly, length = quarters)
     * @param nxY     Exogenous net exports share path (quarterly, length = quarters)
     * @param quarters Number of simulation quarters (the "in-sample" or forward horizon)
     * @throws IllegalStateException If parameters not loaded
     */
    public void solveForPath(double K0, double A0, double[] N, double[] h, double[] P,
                             double[] gY, double[] nxY, int quarters) {
        if (!paramsLoaded) {
            throw new IllegalStateException("Parameters not loaded. Call loadParams() first.");
        }
        if (quarters <= 0) {
            throw new IllegalArgumentException("quarters must be positive, got: " + quarters);
        }
        if (!Double.isFinite(K0) || K0 <= 0) {
            throw new IllegalArgumentException("K0 must be positive and finite, got: " + K0);
        }
        if (!Double.isFinite(A0) || A0 <= 0) {
            throw new IllegalArgumentException("A0 must be positive and finite, got: " + A0);
        }
        if (N.length != quarters || h.length != quarters) {
            throw new IllegalArgumentException(String.format(
                    "N length (%d) and h length (%d) must match quarters (%d)",
                    N.length, h.length, quarters));
        }
        if (P.length != quarters) {
            throw new IllegalArgumentException(String.format(
                    "P length (%d) must match quarters (%d)", P.length, quarters));
        }
        if (gY.length != quarters || nxY.length != quarters) {
            throw new IllegalArgumentException(String.format(
                    "gY length (%d) and nxY length (%d) must match quarters (%d)",
                    gY.length, nxY.length, quarters));
        }
        validateExogenousInput(N, "N");
        validateExogenousInput(h, "h");
        validateExogenousInput(P, "P");
        validateShareInput(gY, "gY");
        validateShareInput(nxY, "nxY");

        this.K = K0;
        this.A = A0;
        this.simQuarters = quarters;
        this.currentQuarterIdx = 0;

        int totalQ = quarters + terminalExtensionQuarters;

        // Build time-varying parameter arrays (constant when no scenario)
        double[] alphaArr = new double[totalQ];
        double[] deltaArr = new double[totalQ];
        double[] betaArr = new double[totalQ];
        double[] sigmaArr = new double[totalQ];
        double[] gAQArr = new double[totalQ];
        buildParameterArrays(totalQ, alphaArr, deltaArr, betaArr, sigmaArr, gAQArr);

        // Store for use in getTrendAtQuarter
        this.solvedAlpha = alphaArr;
        this.solvedDelta = deltaArr;

        // Build extended exogenous paths
        double[] nExt = extendExogenous(N, totalQ, TAIL_DEMOGRAPHIC_WINDOW);
        double[] hExt = extendWithTaperedGrowth(h, totalQ, TAIL_HOURS_WINDOW);
        double[] pExt = extendWithFrozenRatio(P, nExt, totalQ);
        double[] aExt = buildTfpPathVarying(A0, totalQ, gAQArr);
        double[] gYExt = extendShare(gY, totalQ);
        double[] nxYExt = extendShare(nxY, totalQ);

        // Apply scenario deviations to g_Y and nx_Y share paths
        if (hasScenario()) {
            for (int t = 0; t < totalQ; t++) {
                int calendarYear = scenarioStartYear + t / 4;
                double[] devs = scenario.getDeviations(calendarYear);
                gYExt[t] += devs[RamseyScenario.IDX_G_Y];
                nxYExt[t] += devs[RamseyScenario.IDX_NX_Y];
                validatePrivateShare(gYExt[t], nxYExt[t], calendarYear, t);
            }
        } else {
            for (int t = 0; t < totalQ; t++) {
                int calendarYear = scenarioStartYear + t / 4;
                validatePrivateShare(gYExt[t], nxYExt[t], calendarYear, t);
            }
        }

        this.pathN = new double[totalQ];
        this.pathH = new double[totalQ];
        this.pathP = pExt;
        this.pathA = aExt;
        this.pathGY = gYExt;
        this.pathNXY = nxYExt;
        System.arraycopy(hExt, 0, pathH, 0, totalQ);
        System.arraycopy(nExt, 0, pathN, 0, totalQ);

        // Terminal BGP uses terminal-period parameters
        double termAlpha = alphaArr[totalQ - 1];
        double termDelta = deltaArr[totalQ - 1];
        double termBeta = betaArr[totalQ - 1];
        double termGAQ = gAQArr[totalQ - 1];
        double termKyBgp = calculateBgpCapitalOutputRatio(termAlpha, termDelta, termBeta, sigmaArr[totalQ - 1], termGAQ);
        kyBgp = termKyBgp;

        // Terminal gross growth implied by the exogenous drivers — used only to
        // build a feasible BGP-like initial guess for the Newton iteration.
        double gTerminal = terminalGrossGrowth(aExt, nExt, alphaArr, totalQ);

        if (logEnabled) {
            log.info(String.format("Solving Ramsey (stacked-time Newton): %d quarters (+%d extension), K0=%.1f%s",
                    quarters, terminalExtensionQuarters, K0,
                    hasScenario() ? " [SCENARIO: " + scenario.getScenarioName() + "]" : ""));
            if (hasScenario()) {
                log.info(String.format("  Terminal BGP K/Y = %.4f, G_terminal=%.6f (alpha=%.3f, delta_q=%.6f, beta_q=%.6f)",
                        termKyBgp, gTerminal, termAlpha, termDelta, termBeta));
            }
        }

        // Stacked-time (LBJ) perfect-foresight solve: the Euler + resource
        // constraints for ALL quarters are solved simultaneously by a damped
        // Newton iteration on a block-banded system, with K_0 fixed and the
        // terminal transversality closure K_{T-1}/Y_{T-1} = kyBgp. Because the
        // linear solve is global, saddle-path conditioning is irrelevant — this
        // handles anticipated/delayed permanent shocks (e.g. a TFP step that
        // only arrives years into the simulation), which a one-dimensional
        // forward shooting solve does not.
        solvedK = new double[totalQ];
        solvedC = new double[totalQ];
        solvedY = new double[totalQ];

        int iter = stackedNewtonSolve(K0, aExt, nExt, hExt, pExt, gYExt, nxYExt, totalQ,
                alphaArr, deltaArr, betaArr, sigmaArr, gTerminal,
                solvedK, solvedC, solvedY);

        solved = true;

        if (logEnabled) {
            log.info(String.format("Ramsey stacked-time Newton converged in %d iterations (c_0=%.6f)",
                    iter, solvedC[0]));
            log.info(String.format("  K_0 = %.4f (calibrated K0 = %.4f)", solvedK[0], K0));
            log.info(String.format("  Terminal K/Y = %.4f (BGP target = %.4f)",
                    solvedK[totalQ - 1] / solvedY[totalQ - 1], kyBgp));
        }
    }

    /**
     * Build time-varying parameter arrays from baseline values and optional scenario deviations.
     *
     * <p>When no scenario is active, all arrays are filled with the constant baseline values.
     * With a scenario, deviations are added per year (4 quarters each), with the annual-to-quarterly
     * conversion applied after adding the deviation.</p>
     */
    private void buildParameterArrays(int totalQ, double[] alphaArr, double[] deltaArr, double[] betaArr, double[] sigmaArr, double[] gAQArr) {
        double[] baselineGAAnnualArr = buildBaselineAnnualGrowthPath(totalQ);

        if (!hasScenario()) {
            // No scenario: constant baseline values
            Arrays.fill(alphaArr, alphaK);
            Arrays.fill(deltaArr, deltaQ);
            Arrays.fill(betaArr, betaQ);
            Arrays.fill(sigmaArr, sigma);
            for (int t = 0; t < totalQ; t++) {
                gAQArr[t] = annualToQuarterlyGrowth(baselineGAAnnualArr[t]);
            }
            return;
        }

        for (int t = 0; t < totalQ; t++) {
            int calendarYear = scenarioStartYear + t / 4;
            double[] devs = scenario.getDeviations(calendarYear);

            // alpha_K: direct (no quarterly conversion needed)
            alphaArr[t] = alphaK + devs[RamseyScenario.IDX_ALPHA_K];

            // delta: annual deviation → quarterly conversion
            double deltaADev = deltaAnnual + devs[RamseyScenario.IDX_DELTA];
            deltaArr[t] = 1.0 - Math.pow(1.0 - deltaADev, 0.25);

            // beta: annual deviation → quarterly conversion
            double betaADev = betaAnnual + devs[RamseyScenario.IDX_BETA];
            betaArr[t] = Math.pow(betaADev, 0.25);

            // sigma: direct (no quarterly conversion needed)
            sigmaArr[t] = sigma + devs[RamseyScenario.IDX_SIGMA];

            // g_A: annual deviation → quarterly conversion
            double gAADev = baselineGAAnnualArr[t] + devs[RamseyScenario.IDX_G_A];
            gAQArr[t] = Math.pow(1.0 + gAADev, 0.25) - 1.0;

                validateQuarterlyParameters(alphaArr[t], deltaArr[t], betaArr[t], sigmaArr[t], gAQArr[t],
                    calendarYear, t);
        }

        if (logEnabled) {
            // Log first and last year parameter values for diagnostics
            int firstYear = scenarioStartYear;
            int lastYear = scenarioStartYear + (totalQ - 1) / 4;
            log.info(String.format("  Scenario params at year %d: alpha=%.4f, delta_q=%.6f, beta_q=%.6f, sigma=%.3f, g_A_q=%.6f",
                    firstYear, alphaArr[0], deltaArr[0], betaArr[0], sigmaArr[0], gAQArr[0]));
            log.info(String.format("  Scenario params at year %d: alpha=%.4f, delta_q=%.6f, beta_q=%.6f, sigma=%.3f, g_A_q=%.6f",
                    lastYear, alphaArr[totalQ - 1], deltaArr[totalQ - 1], betaArr[totalQ - 1],
                    sigmaArr[totalQ - 1], gAQArr[totalQ - 1]));
        }
    }

    // ========== Stepping Interface (for SimPaths) ==========

    /**
     * Get the trend state at a specific quarter index (0-based, within sim horizon).
     *
     * <p>This is the primary interface for retrieving trend values for a given
     * quarter. It computes wages and interest rates from the solved K, Y paths.</p>
     *
     * @param quarterIdx 0-based quarter index within the simulation horizon
     * @return TrendState with wage, output, r*, K values
     * @throws IllegalStateException If model not solved
     * @throws IndexOutOfBoundsException If quarter out of range
     */
    public TrendState getTrendAtQuarter(int quarterIdx) {
        if (!solved) {
            throw new IllegalStateException("Model not solved. Call solveForPath() first.");
        }
        if (quarterIdx < 0 || quarterIdx >= simQuarters) {
            throw new IndexOutOfBoundsException(String.format(
                    "Quarter index %d out of range [0, %d)", quarterIdx, simQuarters));
        }

        double kVal = solvedK[quarterIdx];
        double yVal = solvedY[quarterIdx];
        double cVal = solvedC[quarterIdx];  // per head
        double nVal = pathN[quarterIdx];    // persons in employment
        double hVal = pathH[quarterIdx];
        double pVal = pathP[quarterIdx];    // resident population

        // Use time-varying alpha if solved with scenario, otherwise baseline
        double alpha = (solvedAlpha != null) ? solvedAlpha[quarterIdx] : alphaK;
        double delta = (solvedDelta != null) ? solvedDelta[quarterIdx] : deltaQ;

        double effLabor = nVal * hVal;
        double wage = (1.0 - alpha) * yVal / effLabor;
        double rQuarterly = alpha * yVal / kVal - delta;
        double rAnnual = Math.pow(1.0 + rQuarterly, 4) - 1.0;

        double cAgg = pVal * cVal;
        double gVal = pathGY[quarterIdx] * yVal;
        double nxVal = pathNXY[quarterIdx] * yVal;
        double iVal = yVal - cAgg - gVal - nxVal;

        return new TrendState(yVal, cAgg, iVal, kVal, wage, rQuarterly, rAnnual, nVal,
                pathA[quarterIdx], gVal, nxVal, pVal);
    }

    /**
     * Step the model forward one year (4 quarters) and return the annual average trend.
     *
     * <p>Advances the internal quarter counter by 4. Returns the average of
     * the four quarterly trend states within that year.</p>
     *
     * @return Annual average TrendState
     * @throws IllegalStateException If model not solved or quarters exhausted
     */
    public TrendState stepYear() {
        if (!solved) {
            throw new IllegalStateException("Model not solved. Call solveForPath() first.");
        }
        if (currentQuarterIdx + 4 > simQuarters) {
            throw new IllegalStateException(String.format(
                    "Cannot step year: only %d quarters remaining (need 4). Current idx=%d, total=%d",
                    simQuarters - currentQuarterIdx, currentQuarterIdx, simQuarters));
        }

        double ySum = 0, cSum = 0, iSum = 0, kSum = 0;
        double wSum = 0, rqSum = 0, nSum = 0;
        double gSum = 0, nxSum = 0, pSum = 0;

        for (int q = 0; q < 4; q++) {
            TrendState qs = getTrendAtQuarter(currentQuarterIdx + q);
            ySum += qs.output();
            cSum += qs.consumption();
            iSum += qs.investment();
            kSum += qs.capitalStock();
            wSum += qs.wage();
            rqSum += qs.rQuarterly();
            nSum += qs.labor();
            gSum += qs.government();
            nxSum += qs.netExports();
            pSum += qs.population();
        }

        currentQuarterIdx += 4;

        double rqAvg = rqSum / 4.0;
        double raAvg = Math.pow(1.0 + rqAvg, 4) - 1.0;
        double aVal = pathA[currentQuarterIdx - 1]; // end-of-year TFP

        return new TrendState(
                ySum / 4.0,   // average quarterly Y
                cSum / 4.0,   // average quarterly C
                iSum / 4.0,   // average quarterly I
                kSum / 4.0,   // average K
                wSum / 4.0,   // average wage
                rqAvg,        // average quarterly r
                raAvg,        // annualized r*
                nSum / 4.0,   // average N
                aVal,         // end-of-year A
                gSum / 4.0,   // average quarterly G
                nxSum / 4.0,  // average quarterly NX
                pSum / 4.0    // average P
        );
    }

    /**
     * Reset the stepping counter to the beginning.
     */
    public void resetStepping() {
        currentQuarterIdx = 0;
    }

    // ========== Core Stacked-Time Newton Solver ==========

    /**
     * Terminal gross growth of K, Y and C implied by the exogenous drivers.
     *
     * <p>On a balanced growth path Y ∝ A^{1/(1-α)}·L_eff with K/Y constant, so
     * the common gross growth rate is G = (A_T/A_{T-1})^{1/(1-α)} · (L_T/L_{T-1}),
     * computed empirically from the last two extension points (robust to the
     * terminal-growth convergence block). Only used to seed the Newton guess.</p>
     */
    private double terminalGrossGrowth(double[] aPath, double[] nPath, double[] alphaArr, int T) {
        double aGross = aPath[T - 1] / aPath[T - 2];
        double labGross = (nPath[T - 1] * pathH[T - 1]) / (nPath[T - 2] * pathH[T - 2]);
        double alphaT = alphaArr[T - 1];
        double g = Math.pow(aGross, 1.0 / (1.0 - alphaT)) * labGross;
        if (!Double.isFinite(g) || g <= 0) {
            g = Math.pow(1.0 + gAQ, 1.0 / (1.0 - alphaT));
        }
        return g;
    }

    /**
     * Per-quarter economics: labor and output for given (K_t, c_t).
     *
     * @return {N_t, Y_t}, or null if infeasible
     */
    private double[] periodEconomics(int t, double Kt, double ct, double[] aPath,
                                     double[] nPath, double[] hPath, double[] alphaArr) {
        if (!(Kt > 0) || !(ct > 0) || !Double.isFinite(Kt) || !Double.isFinite(ct)) {
            return null;
        }
        double nT = nPath[t];
        double effLabor = nT * hPath[t];
        double yT = aPath[t] * Math.pow(Kt, alphaArr[t]) * Math.pow(effLabor, 1.0 - alphaArr[t]);
        if (!Double.isFinite(yT) || yT <= 0) {
            return null;
        }
        return new double[]{nT, yT};
    }

    /**
     * Stacked-time residual vector for the perfect-foresight Ramsey system.
     *
     * <p>Unknown layout (length 2T-1), K_0 fixed at {@code K0}:
     * u[2t]=c_t (consumption per head), u[2t+1]=K_{t+1} for t=0..T-2; u[2T-2]=c_{T-1}.
     * Residuals (all dimensionless, O(1)):</p>
     * <ul>
     *   <li>row 2t  (t=0..T-2): resource constraint K_{t+1}/RHS - 1,
     *       RHS = (1-δ)K_t + Y_t - P_t·c_t - (g_Y + nx_Y)_t·Y_t</li>
     *   <li>row 2t+1 (t=0..T-2): Euler c_{t+1}/[c_t(β(1+r_{t+1}))^{1/σ}] - 1</li>
     *   <li>row 2T-2: terminal transversality K_{T-1}/(kyBgp·Y_{T-1}) - 1</li>
     * </ul>
     *
     * <p>{@code pPath} is the population path (the consumers); the population weight of
     * the objective cancels from the Euler row.</p>
     *
     * @return residual array of length 2T-1, or null if the point is infeasible
     */
    private double[] stackedResiduals(double[] u, double K0, double[] aPath, double[] nPath,
                                      double[] hPath, double[] pPath, double[] gYPath,
                                      double[] nxYPath, int T,
                                      double[] alphaArr, double[] deltaArr, double[] betaArr,
                                      double[] sigmaArr) {
        int m = 2 * T - 1;
        double[] f = new double[m];
        for (int t = 0; t < T - 1; t++) {
            double Kt = (t == 0) ? K0 : u[2 * t - 1];
            double ct = u[2 * t];
            double Knext = u[2 * t + 1];
            double cNext = u[2 * t + 2];

            double[] e = periodEconomics(t, Kt, ct, aPath, nPath, hPath, alphaArr);
            if (e == null) return null;
            double yT = e[1];

            double rhsK = (1.0 - deltaArr[t]) * Kt + yT - pPath[t] * ct
                    - (gYPath[t] + nxYPath[t]) * yT;
            if (!(rhsK > 0) || !Double.isFinite(rhsK) || !(Knext > 0)) return null;
            f[2 * t] = Knext / rhsK - 1.0;

            double[] eN = periodEconomics(t + 1, Knext, cNext, aPath, nPath, hPath, alphaArr);
            if (eN == null) return null;
            double yNext = eN[1];
            double rNext = alphaArr[t + 1] * yNext / Knext - deltaArr[t + 1];
            double gross = betaArr[t] * (1.0 + rNext);
            if (!(gross > 0) || !Double.isFinite(gross)) return null;
            double eulerRhs = ct * Math.pow(gross, 1.0 / sigmaArr[t]);
            if (!(eulerRhs > 0) || !Double.isFinite(eulerRhs)) return null;
            f[2 * t + 1] = cNext / eulerRhs - 1.0;
        }

        double Klast = u[2 * (T - 1) - 1];
        double cLast = u[2 * (T - 1)];
        double[] eL = periodEconomics(T - 1, Klast, cLast, aPath, nPath, hPath, alphaArr);
        if (eL == null) return null;
        double yLast = eL[1];
        f[m - 1] = Klast / (kyBgp * yLast) - 1.0;

        for (double v : f) {
            if (!Double.isFinite(v)) return null;
        }
        return f;
    }

    /**
     * Damped Newton on the stacked-time system.
     *
     * <p>The finite-difference Jacobian is tridiagonal (each quarter couples only
     * adjacent unknowns) and is factorised by banded LU with partial pivoting.
     * {@code u} is updated in place to the converged solution.</p>
     *
     * @return number of Newton iterations used
     */
    private int stackedNewtonCore(double[] u, double K0,
                                   double[] aPath, double[] nPath, double[] hPath,
                                   double[] pPath, double[] gYPath, double[] nxYPath, int T,
                                   double[] alphaArr, double[] deltaArr, double[] betaArr,
                                   double[] sigmaArr) {
        int m = 2 * T - 1;
        double[] f = stackedResiduals(u, K0, aPath, nPath, hPath, pPath, gYPath, nxYPath, T,
                alphaArr, deltaArr, betaArr, sigmaArr);
        if (f == null) {
            throw new IllegalStateException(
                    "Ramsey stacked-time Newton: infeasible starting point. Check scenario/path assumptions.");
        }
        double norm = infNorm(f);

        int kl = 1, ku = 1;
        int ldab = 2 * kl + ku + 1;
        double ftol = tolerance;
        int maxNewton = maxIterations;
        int it;
        for (it = 1; it <= maxNewton; it++) {
            if (norm < ftol) break;

            // m4: one-sided forward difference (step = 1e-7·max(1,|u|)) is a
            // deliberate choice — empirically the Newton converges in ~5
            // iterations to ||F||<1e-10 with bit-exact MATLAB parity, so the
            // ~1e-7 truncation error is absorbed by the line search near the
            // root. Central differencing would double the residual evaluations
            // for no accuracy that matters at this tolerance.
            // m5: each column recomputes the FULL residual (O(T)), so Jacobian
            // assembly is O(T^2) (~ms at T≈900). A localised recompute would
            // be O(T) total, but is intentionally NOT done: it would diverge
            // the Java/MATLAB code paths and risk the bit-exact cross-
            // validation parity for a routine that is already sub-second.
            // Revisit only if the horizon grows by an order of magnitude.
            double[] ab = new double[ldab * m];
            for (int j = 0; j < m; j++) {
                double uj = u[j];
                double step = 1e-7 * Math.max(1.0, Math.abs(uj));
                u[j] = uj + step;
                double[] fp = stackedResiduals(u, K0, aPath, nPath, hPath, pPath, gYPath, nxYPath, T,
                        alphaArr, deltaArr, betaArr, sigmaArr);
                u[j] = uj;
                if (fp == null) {
                    u[j] = uj - step;
                    fp = stackedResiduals(u, K0, aPath, nPath, hPath, pPath, gYPath, nxYPath, T,
                            alphaArr, deltaArr, betaArr, sigmaArr);
                    u[j] = uj;
                    step = -step;
                    if (fp == null) {
                        throw new IllegalStateException(
                                "Ramsey stacked-time Newton: Jacobian column " + j + " infeasible.");
                    }
                }
                for (int i = Math.max(0, j - ku); i <= Math.min(m - 1, j + kl); i++) {
                    double d = (fp[i] - f[i]) / step;
                    ab[(kl + ku + i - j) + j * ldab] = d;
                }

                // M2 guard: the kl=ku=1 banding is only valid because every
                // residual couples strictly adjacent periods (resource/Euler)
                // and labour is intratemporal. A future structural term that
                // spans >1 period (adjustment costs, time-to-build, habits,
                // two-period-ahead expectations) would put non-zero entries
                // outside the band that this solver silently drops, masking
                // a wrong Newton direction as slow/false convergence. Probe
                // the off-band sensitivity once (first iteration) and fail
                // loudly if the tridiagonal assumption no longer holds.
                if (it == 1) {
                    int loEx = j - ku - 1, hiEx = j + kl + 1;
                    for (int i = 0; i < m; i++) {
                        if (i >= Math.max(0, j - ku) && i <= Math.min(m - 1, j + kl)) continue;
                        double dOff = Math.abs((fp[i] - f[i]) / step);
                        if (dOff > 1e-6) {
                            throw new IllegalStateException(String.format(
                                "Ramsey stacked-time Newton: tridiagonal Jacobian assumption violated"
                                + " — residual %d depends on unknown %d (|dF|=%.3e, outside band"
                                + " [%d,%d]). A cross-period coupling was added; the kl=ku=1 banded"
                                + " solver must be widened to match.", i, j, dOff, loEx, hiEx));
                        }
                    }
                }
            }

            double[] rhs = new double[m];
            for (int i = 0; i < m; i++) rhs[i] = -f[i];
            if (!bandedSolve(ab, m, kl, ku, rhs)) {
                throw new IllegalStateException(
                        "Ramsey stacked-time Newton: singular Jacobian at iteration " + it);
            }

            double lambda = 1.0;
            double[] uTrial = new double[m];
            double newNorm = norm;
            double[] fTrial = null;
            while (lambda > 1e-10) {
                for (int i = 0; i < m; i++) uTrial[i] = u[i] + lambda * rhs[i];
                fTrial = stackedResiduals(uTrial, K0, aPath, nPath, hPath, pPath, gYPath, nxYPath, T,
                        alphaArr, deltaArr, betaArr, sigmaArr);
                if (fTrial != null) {
                    newNorm = infNorm(fTrial);
                    if (newNorm < (1.0 - 1e-4 * lambda) * norm || lambda < 1e-8) break;
                }
                lambda *= 0.5;
            }
            if (fTrial == null) {
                throw new IllegalStateException(
                        "Ramsey stacked-time Newton: line search failed to find a feasible step at iteration " + it);
            }
            System.arraycopy(uTrial, 0, u, 0, m);
            f = fTrial;
            norm = newNorm;
        }

        if (norm >= ftol) {
            log.warn(String.format(
                    "Ramsey stacked-time Newton did not reach tolerance in %d iterations (||F||=%.3e)",
                    maxNewton, norm));
        }
        return it;
    }

    /**
     * Solve the Ramsey perfect-foresight transition. Builds a feasible BGP-like
     * guess, then runs the stacked-time Newton. Fills {@code kOut,cOut,yOut}.
     *
     * @return number of Newton iterations used
     */
    private int stackedNewtonSolve(double K0, double[] aPath, double[] nPath, double[] hPath,
                                    double[] pPath, double[] gYPath, double[] nxYPath, int T,
                                    double[] alphaArr, double[] deltaArr, double[] betaArr,
                                    double[] sigmaArr,
                                    double gTerminal, double[] kOut, double[] cOut,
                                    double[] yOut) {
        int m = 2 * T - 1;
        double[] u = new double[m];

        // Feasible BGP-like initial guess: integrate forward with a constant
        // BGP investment share; private absorption is shared over the population.
        double Kg = K0;
        for (int t = 0; t < T; t++) {
            double nG = nPath[t];
            double yG = aPath[t] * Math.pow(Kg, alphaArr[t])
                    * Math.pow(nG * hPath[t], 1.0 - alphaArr[t]);
            double invShare = (gTerminal - 1.0 + deltaArr[t]) * kyBgp;
            invShare = Math.max(0.03, Math.min(0.45, invShare));
            double iG = invShare * yG;
            double privAbs = (1.0 - gYPath[t] - nxYPath[t]) * yG - iG;
            double cG = Math.max(privAbs / pPath[t], 1e-6 * yG / pPath[t]);
            if (t < T - 1) {
                u[2 * t] = cG;
                double KnextG = (1.0 - deltaArr[t]) * Kg + iG;
                u[2 * t + 1] = Math.max(KnextG, 1e-6 * Kg);
                Kg = u[2 * t + 1];
            } else {
                u[2 * (T - 1)] = cG;
            }
        }

        int it = stackedNewtonCore(u, K0, aPath, nPath, hPath, pPath, gYPath, nxYPath, T,
                alphaArr, deltaArr, betaArr, sigmaArr);

        // Reconstruct full paths from the converged unknowns.
        for (int t = 0; t < T; t++) {
            double Kt = (t == 0) ? K0 : u[2 * t - 1];
            double ct = u[2 * t];
            double[] e = periodEconomics(t, Kt, ct, aPath, nPath, hPath, alphaArr);
            kOut[t] = Kt;
            cOut[t] = ct;
            yOut[t] = e[1];
        }
        return it;
    }

    private static double infNorm(double[] v) {
        double n = 0.0;
        for (double x : v) {
            double a = Math.abs(x);
            if (a > n) n = a;
        }
        return n;
    }

    /**
     * In-place banded LU solve with partial pivoting (LAPACK dgbsv layout).
     *
     * <p>{@code ab} has leading dimension {@code 2*kl+ku+1}; on entry rows
     * {@code kl..2kl+ku} hold the band, the top {@code kl} rows are pivot fill
     * scratch. {@code b} is overwritten with the solution.</p>
     *
     * @return false if a (near-)singular pivot is encountered
     */
    private static boolean bandedSolve(double[] ab, int n, int kl, int ku, double[] b) {
        int ldab = 2 * kl + ku + 1;
        int[] ipiv = new int[n];
        int kv = ku + kl;
        // m3: reject near-singular pivots relative to the band scale rather
        // than only exact zeros. A tiny-but-nonzero pivot otherwise passes and
        // produces an enormous, ill-conditioned Newton step that the line
        // search can only damp, not detect — failing loudly here is safer.
        double anorm = 0.0;
        for (double v : ab) {
            double a = Math.abs(v);
            if (a > anorm) anorm = a;
        }
        double pivTol = 1e-13 * anorm;
        for (int j = 0; j < n; j++) {
            int jp = j;
            double max = Math.abs(ab[kv + j * ldab]);
            int iend = Math.min(kl, n - 1 - j);
            for (int i = 1; i <= iend; i++) {
                double v = Math.abs(ab[kv + i + j * ldab]);
                if (v > max) { max = v; jp = j + i; }
            }
            ipiv[j] = jp;
            if (Math.abs(ab[kv + (jp - j) + j * ldab]) <= pivTol) return false;

            if (jp != j) {
                int jmax = Math.min(n - 1, j + kv);
                for (int col = j; col <= jmax; col++) {
                    int rj = kv + (j - col) + col * ldab;
                    int rp = kv + (jp - col) + col * ldab;
                    double tmp = ab[rj];
                    ab[rj] = ab[rp];
                    ab[rp] = tmp;
                }
                double tb = b[j]; b[j] = b[jp]; b[jp] = tb;
            }
            double piv = ab[kv + j * ldab];
            for (int i = 1; i <= iend; i++) {
                double mlt = ab[kv + i + j * ldab] / piv;
                ab[kv + i + j * ldab] = mlt;
                int jmax = Math.min(n - 1, j + kv);
                for (int col = j + 1; col <= jmax; col++) {
                    ab[kv + (j + i - col) + col * ldab] -= mlt * ab[kv + (j - col) + col * ldab];
                }
                b[j + i] -= mlt * b[j];
            }
        }
        for (int j = n - 1; j >= 0; j--) {
            double s = b[j];
            int jmax = Math.min(n - 1, j + kv);
            for (int col = j + 1; col <= jmax; col++) {
                s -= ab[kv + (j - col) + col * ldab] * b[col];
            }
            b[j] = s / ab[kv + j * ldab];
        }
        return true;
    }

    // ========== Exogenous Path Construction ==========

    /**
     * Trailing-growth windows for the terminal tail appended past the SimPaths horizon.
     * These MUST match {@code input_processing/macromodel/ramsey_trend.m}, which builds its
     * own tail with {@code extend_level_path(handoff_labor_proj, ..., 20)} for labour and
     * {@code apply_smooth_terminal_demographic_closure(handoff_h_ext, ..., 1)} for hours.
     *
     * <p>Unlike the forward projection, the tail is not carried by any bundle artefact:
     * both sides construct it, so this convention is enforced only by the tripwire in
     * {@code MacroModelManagerTest}.</p>
     *
     * <p>The hours tail is tapered on BOTH sides: {@link #extendWithTaperedGrowth} here
     * mirrors {@code apply_smooth_terminal_demographic_closure(handoff_h_ext, ..., 1)} in
     * ramsey_trend.m.</p>
     */
    public static final int TAIL_DEMOGRAPHIC_WINDOW = 20;
    public static final int TAIL_HOURS_WINDOW = 1;

    /** Half-life of the terminal growth taper. Mirrors {@code tail_half_life_quarters} in
     *  {@code apply_smooth_terminal_demographic_closure} in ramsey_trend.m. */
    public static final int TAIL_HALF_LIFE_QUARTERS = 20;

    /**
     * Extend a positive level path past its sample with the trailing growth rate
     * <em>tapered geometrically to zero</em>.
     *
     * <p>Mirrors {@code apply_smooth_terminal_demographic_closure} in ramsey_trend.m,
     * called there with {@code last_projection_idx = sample length}. Both sides must use
     * this rule or the MATLAB-Java parity contract breaks.</p>
     *
     * <p>Used for the hours tail. Extending hours at the fitted log-linear rate for the
     * whole 600-quarter tail drove hours per worker to ~69% of their handoff level, while
     * the solve imposes a balanced-growth terminal condition, on which hours per worker
     * must be asymptotically constant. The tail is a terminal-condition device, not a
     * forecast; the measured effect on the trend wage over 2023-2060 is under 0.003%.</p>
     */
    static double[] extendWithTaperedGrowth(double[] sample, int totalLen, int window) {
        if (sample == null || sample.length == 0) {
            throw new IllegalArgumentException("sample must be non-null and non-empty");
        }
        if (totalLen < sample.length) {
            throw new IllegalArgumentException(String.format(
                    "totalLen (%d) must be >= sample length (%d)", totalLen, sample.length));
        }
        double[] out = new double[totalLen];
        System.arraycopy(sample, 0, out, 0, sample.length);
        int last = sample.length;                    // MATLAB's last_projection_idx (1-based)
        if (last >= totalLen) {
            return out;
        }

        int w = Math.min(window, last - 1);
        if (w < 1 || out[last - 1] <= 0 || out[last - 1 - w] <= 0) {
            Arrays.fill(out, last, totalLen, out[last - 1]);
            return out;
        }

        double logGrowth = Math.log(out[last - 1] / out[last - 1 - w]) / w;
        if (!Double.isFinite(logGrowth)) {
            Arrays.fill(out, last, totalLen, out[last - 1]);
            return out;
        }

        double rho = Math.pow(0.5, 1.0 / TAIL_HALF_LIFE_QUARTERS);
        for (int t = last; t < totalLen; t++) {
            logGrowth *= rho;
            out[t] = out[t - 1] * Math.exp(logGrowth);
        }
        return out;
    }

    /**
     * Extend a population path past its last quarter at that quarter's dependency ratio:
     * {@code out[t] = nExt[t] * p[last] / nExt[last]} for {@code t >= p.length}.
     *
     * <p>Mirrors {@code extend_population_frozen_ratio} in ramsey_trend.m, used there in
     * both solves. The population has no tail rule of its own: it follows the employment
     * path, whatever that path's tail rule, so N/P is constant wherever the balanced-growth
     * closure acts.</p>
     *
     * @param p        population path, authoritative for its whole length
     * @param nExt     employment path over at least {@code totalLen} quarters; the ratio is
     *                 taken at quarter {@code p.length - 1} of this path
     * @param totalLen length of the extended path
     * @return population path of length {@code totalLen}
     */
    static double[] extendWithFrozenRatio(double[] p, double[] nExt, int totalLen) {
        if (p == null || p.length == 0) {
            throw new IllegalArgumentException("p must be non-null and non-empty");
        }
        if (totalLen < p.length) {
            throw new IllegalArgumentException(String.format(
                    "totalLen (%d) must be >= p length (%d)", totalLen, p.length));
        }
        if (nExt == null || nExt.length < totalLen) {
            throw new IllegalArgumentException(String.format(
                    "nExt length (%s) must be >= totalLen (%d)",
                    nExt == null ? "null" : String.valueOf(nExt.length), totalLen));
        }
        double[] out = new double[totalLen];
        System.arraycopy(p, 0, out, 0, p.length);
        int last = p.length - 1;
        for (int t = p.length; t < totalLen; t++) {
            out[t] = nExt[t] * p[last] / nExt[last];
        }
        return out;
    }

    /**
     * Extend an exogenous path beyond the sample period using constant growth.
     *
     * <p>The growth rate is estimated from the last {@code window} observations
     * of the sample path and applied geometrically beyond.</p>
     *
     * @param sample   In-sample path (quarterly)
     * @param totalLen Target total length (sample + extension)
     * @param window   Number of trailing quarters to estimate growth rate from
     * @return Extended path of length totalLen
     */
    public static double[] extendExogenous(double[] sample, int totalLen, int window) {
        if (sample == null || sample.length == 0) {
            throw new IllegalArgumentException("sample must be non-null and non-empty");
        }
        if (totalLen < sample.length) {
            throw new IllegalArgumentException(String.format(
                    "totalLen (%d) must be >= sample length (%d)", totalLen, sample.length));
        }
        double[] extended = new double[totalLen];
        int sampleLen = sample.length;
        System.arraycopy(sample, 0, extended, 0, sampleLen);

        if (sampleLen == totalLen) {
            return extended;
        }

        if (sampleLen == 1) {
            for (int t = 1; t < totalLen; t++) {
                extended[t] = extended[t - 1];
            }
            return extended;
        }

        int w = Math.min(window, sampleLen - 1);
        if (w <= 0) {
            for (int t = sampleLen; t < totalLen; t++) {
                extended[t] = extended[t - 1];
            }
            return extended;
        }

        double base = sample[sampleLen - 1 - w];
        double last = sample[sampleLen - 1];
        double growthRate;
        if (base <= 0 || last <= 0 || !Double.isFinite(base) || !Double.isFinite(last)) {
            growthRate = 0.0;
        } else {
            growthRate = Math.pow(last / base, 1.0 / w) - 1.0;
            if (!Double.isFinite(growthRate)) {
                growthRate = 0.0;
            }
        }

        for (int t = sampleLen; t < totalLen; t++) {
            extended[t] = extended[t - 1] * (1.0 + growthRate);
        }

        return extended;
    }

    /**
     * Extend an expenditure share path beyond the sample period using constant extension.
     *
     * <p>The share is held constant at the last observed value beyond the sample,
     * which is appropriate for G/Y and NX/Y (treated as stationary around a level).
     * If a scenario is active, deviations are applied afterward.</p>
     *
     * @param sample   In-sample share path (quarterly)
     * @param totalLen Target total length (sample + extension)
     * @return Extended path of length totalLen
     */
    public static double[] extendShare(double[] sample, int totalLen) {
        double[] extended = new double[totalLen];
        int sampleLen = sample.length;
        System.arraycopy(sample, 0, extended, 0, Math.min(sampleLen, totalLen));
        double lastVal = sample[sampleLen - 1];
        for (int t = sampleLen; t < totalLen; t++) {
            extended[t] = lastVal;
        }
        return extended;
    }

    /**
     * Build TFP path with constant geometric growth from A_0.
     */
    private double[] buildTfpPath(double A0, int T) {
        double[] aPath = new double[T];
        aPath[0] = A0;
        for (int t = 1; t < T; t++) {
            aPath[t] = aPath[t - 1] * (1.0 + gAQ);
        }
        return aPath;
    }

    /**
     * Build TFP path with time-varying quarterly growth rates.
     *
     * @param A0    initial TFP level
     * @param T     number of quarters
     * @param gAQArr quarterly TFP growth rate for each quarter
     * @return TFP path of length T
     */
    private double[] buildTfpPathVarying(double A0, int T, double[] gAQArr) {
        double[] aPath = new double[T];
        aPath[0] = A0;
        for (int t = 1; t < T; t++) {
            aPath[t] = aPath[t - 1] * (1.0 + gAQArr[t - 1]);
        }
        return aPath;
    }

    /**
     * Build the baseline annual TFP-growth path.
     *
     * <p>When terminal growth is enabled, the path starts from the in-sample calibration
     * value and converges quarter by quarter toward the terminal annual growth anchor.
     * Otherwise it stays constant at {@link #gAAnnual}.</p>
     */
    private double[] buildBaselineAnnualGrowthPath(int totalQ) {
        double[] annualGrowthPath = new double[totalQ];
        if (totalQ == 0) {
            return annualGrowthPath;
        }

        if (!terminalGrowthEnabled) {
            Arrays.fill(annualGrowthPath, gAAnnual);
            return annualGrowthPath;
        }

        // Pre-decay the seed by the phase offset so a mid-horizon re-solve continues the
        // convergence from the original handoff quarter rather than restarting it at t=0.
        // Algebraically exact for the recursion below; reduces to gAAnnual when offset=0.
        annualGrowthPath[0] = gATerminalAnnual
                + Math.pow(terminalGrowthRhoQ, tfpPhaseOffsetQuarters) * (gAAnnual - gATerminalAnnual);
        for (int t = 1; t < totalQ; t++) {
            annualGrowthPath[t] = gATerminalAnnual
                    + terminalGrowthRhoQ * (annualGrowthPath[t - 1] - gATerminalAnnual);
        }
        return annualGrowthPath;
    }

    private static double annualToQuarterlyGrowth(double annualGrowth) {
        return Math.pow(1.0 + annualGrowth, 0.25) - 1.0;
    }

    private static double calculateBgpCapitalOutputRatio(double alpha, double delta, double beta,
                                                         double sigmaVal, double gAQuarterly) {
        double consumptionGrowth = gAQuarterly / (1.0 - alpha);
        double rStar = Math.pow(1.0 + consumptionGrowth, sigmaVal) / beta - 1.0;
        return alpha / (rStar + delta);
    }

    // ========== Validation ==========

    private void validateParams() {
        if (alphaK <= 0 || alphaK >= 1) {
            throw new IllegalArgumentException("alpha_K must be in (0,1), got: " + alphaK);
        }
        if (deltaQ <= 0 || deltaQ >= 1) {
            throw new IllegalArgumentException("delta_quarterly must be in (0,1), got: " + deltaQ);
        }
        if (betaQ <= 0 || betaQ >= 1) {
            throw new IllegalArgumentException("beta_quarterly must be in (0,1), got: " + betaQ);
        }
        if (sigma <= 0) {
            throw new IllegalArgumentException("sigma must be positive, got: " + sigma);
        }
        if (gAQ < 0) {
            throw new IllegalArgumentException("TFP growth rate must be non-negative, got: " + gAQ);
        }
        if (!Double.isFinite(gATerminalQ) || gATerminalQ < 0) {
            throw new IllegalArgumentException("terminal quarterly TFP growth must be non-negative and finite, got: " + gATerminalQ);
        }
        if (terminalGrowthEnabled && (!Double.isFinite(terminalGrowthRhoQ) || terminalGrowthRhoQ <= 0 || terminalGrowthRhoQ >= 1)) {
            throw new IllegalArgumentException("terminal_growth.rho_quarterly must be in (0,1), got: " + terminalGrowthRhoQ);
        }
    }

    private static void validateExogenousInput(double[] values, String name) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException(name + " must be non-null and non-empty");
        }
        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(values[i]) || values[i] <= 0) {
                throw new IllegalArgumentException(String.format(
                        "%s[%d] must be positive and finite, got: %s", name, i, values[i]));
            }
        }
    }

    private static void validateShareInput(double[] values, String name) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException(name + " must be non-null and non-empty");
        }
        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(values[i])) {
                throw new IllegalArgumentException(String.format(
                        "%s[%d] must be finite, got: %s", name, i, values[i]));
            }
        }
    }

    private static void validatePrivateShare(double gYVal, double nxYVal, int calendarYear, int quarterIdx) {
        if (!Double.isFinite(gYVal) || !Double.isFinite(nxYVal)) {
            throw new IllegalArgumentException(String.format(
                    "Invalid share values at quarter %d (year %d): gY=%s, nxY=%s",
                    quarterIdx, calendarYear, gYVal, nxYVal));
        }
        double privateShare = 1.0 - gYVal - nxYVal;
        if (!Double.isFinite(privateShare) || privateShare <= 0) {
            throw new IllegalArgumentException(String.format(
                    "Invalid private share at quarter %d (year %d): 1 - gY - nxY = %.6f (gY=%.6f, nxY=%.6f)",
                    quarterIdx, calendarYear, privateShare, gYVal, nxYVal));
        }
    }

    private static void validateQuarterlyParameters(double alpha, double delta, double beta,
                                                    double sigmaVal, double gA,
                                                    int calendarYear, int quarterIdx) {
        if (!Double.isFinite(alpha) || alpha <= 0 || alpha >= 1) {
            throw new IllegalArgumentException(String.format(
                    "Invalid alpha at quarter %d (year %d): %.6f (must be in (0,1))",
                    quarterIdx, calendarYear, alpha));
        }
        if (!Double.isFinite(delta) || delta < 0 || delta >= 1) {
            throw new IllegalArgumentException(String.format(
                    "Invalid delta_q at quarter %d (year %d): %.6f (must be in [0,1))",
                    quarterIdx, calendarYear, delta));
        }
        if (!Double.isFinite(beta) || beta <= 0 || beta >= 1) {
            throw new IllegalArgumentException(String.format(
                    "Invalid beta_q at quarter %d (year %d): %.6f (must be in (0,1))",
                    quarterIdx, calendarYear, beta));
        }
        if (!Double.isFinite(sigmaVal) || sigmaVal <= 0) {
            throw new IllegalArgumentException(String.format(
                    "Invalid sigma at quarter %d (year %d): %.6f (must be > 0)",
                    quarterIdx, calendarYear, sigmaVal));
        }
        if (!Double.isFinite(gA) || gA <= -1) {
            throw new IllegalArgumentException(String.format(
                    "Invalid g_A_q at quarter %d (year %d): %.6f (must be > -1)",
                    quarterIdx, calendarYear, gA));
        }
    }

    // ========== JSON Parsing Helpers ==========
    // Manual parsing (no external JSON library) — consistent with DSGEModel approach.

    // Thin delegates to MacroJson, the single reader shared with DSGEModel and
    // MacroModelManager. Keep the parsing there: a local parser that disagrees
    // with it on whitespace after the colon would zero parameters silently when
    // the MATLAB writer changes.

    private static double extractJsonDouble(String json, String key) {
        return MacroJson.number(json, key, 0.0);
    }

    private static double extractJsonDoubleEither(String json, String primaryKey, String legacyKey, double defaultValue) {
        double value = extractJsonDouble(json, primaryKey);
        if (value > 0.0) return value;
        value = extractJsonDouble(json, legacyKey);
        return value > 0.0 ? value : defaultValue;
    }

    private static double requireJsonDouble(String json, String key) {
        if (!MacroJson.has(json, key)) {
            throw new IllegalArgumentException("Missing required parameter in growth params JSON: " + key);
        }
        double value = extractJsonDouble(json, key);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Invalid non-finite parameter value for key: " + key);
        }
        return value;
    }

    private static int extractJsonInt(String json, String key) {
        return MacroJson.integer(json, key, 0);
    }

    private static int extractJsonIntEither(String json, String primaryKey, String legacyKey, int defaultValue) {
        int value = extractJsonInt(json, primaryKey);
        if (value > 0) return value;
        value = extractJsonInt(json, legacyKey);
        return value > 0 ? value : defaultValue;
    }

    /** Returns null when the key is absent or its value is not a string. */
    private static String extractJsonString(String json, String key) {
        return MacroJson.string(json, key, null);
    }

    private static boolean extractJsonBoolean(String json, String key, boolean defaultValue) {
        return MacroJson.bool(json, key, defaultValue);
    }

    /** Returns the object text INCLUDING its braces, or null. */
    private static String extractJsonObject(String json, String key) {
        String body = MacroJson.object(json, key);
        return body == null ? null : "{" + body + "}";
    }

    // ========== CSV Loading Helpers ==========

    /**
     * Load the MATLAB reference CSV for cross-validation.
     *
     * <p>Expected columns: time,K,Y,C,I,w,r_star,N,L,h in this order, then g_Y, nx_Y
     * and P located by header.</p>
     * <p>The g_Y and nx_Y columns are optional (default 0). The P column (the resident
     * population, the planner's consumers) is mandatory. Further columns are ignored.</p>
     *
     * @param csvPath Path to growth_model_reference_{country}.csv
     * @return List of ReferenceRow records
     * @throws IOException If the file cannot be read, or its header has no P column
     */
    public static List<ReferenceRow> loadReferenceCSV(Path csvPath) throws IOException {
        List<String> lines = Files.readAllLines(csvPath);
        List<ReferenceRow> rows = new ArrayList<>();

        if (lines.isEmpty()) return rows;

        // Parse header to find the columns located by name
        String[] headers = MacroCsv.splitRow(lines.get(0));
        int gYCol = -1;
        int nxYCol = -1;
        int pCol = -1;
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim();
            if (h.equalsIgnoreCase("g_Y")) gYCol = i;
            if (h.equalsIgnoreCase("nx_Y")) nxYCol = i;
            if (h.equals("P")) pCol = i;
        }
        if (pCol < 0) {
            throw new IOException("Reference CSV missing mandatory P column: " + csvPath
                    + ". The planner's consumers are the resident population; re-export the macro files.");
        }

        // Skip header
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            String[] parts = MacroCsv.splitRow(line);
            if (parts.length < 10) continue;

            double gYVal = (gYCol >= 0 && gYCol < parts.length)
                    ? Double.parseDouble(parts[gYCol].trim()) : 0.0;
            double nxYVal = (nxYCol >= 0 && nxYCol < parts.length)
                    ? Double.parseDouble(parts[nxYCol].trim()) : 0.0;
            if (pCol >= parts.length) {
                throw new IOException(String.format(
                        "Reference CSV %s: row %d has no P value (%d fields, P is column %d); "
                        + "re-export the macro files.", csvPath, i + 1, parts.length, pCol + 1));
            }
            double pVal = Double.parseDouble(parts[pCol].trim());

            rows.add(new ReferenceRow(
                    parts[0].trim(),                    // time
                    Double.parseDouble(parts[1].trim()), // K
                    Double.parseDouble(parts[2].trim()), // Y
                    Double.parseDouble(parts[3].trim()), // C
                    Double.parseDouble(parts[4].trim()), // I
                    Double.parseDouble(parts[5].trim()), // w
                    Double.parseDouble(parts[6].trim()), // r_star
                    Double.parseDouble(parts[7].trim()), // N
                    Double.parseDouble(parts[8].trim()), // L
                    Double.parseDouble(parts[9].trim()), // h
                    gYVal,                               // g_Y
                    nxYVal,                              // nx_Y
                    pVal                                 // P
            ));
        }

        return rows;
    }

    // ========== Accessors ==========

    public boolean isParamsLoaded() { return paramsLoaded; }
    public boolean isSolved() { return solved; }
    public double getAlphaK() { return alphaK; }
    public double getDeltaQ() { return deltaQ; }
    public double getBetaQ() { return betaQ; }
    public double getSigma() { return sigma; }
    public double getGAQ() { return gAQ; }
    public boolean isTerminalGrowthEnabled() { return terminalGrowthEnabled; }
    public double getGATerminalAnnual() { return gATerminalAnnual; }
    public double getTerminalGrowthRhoQ() { return terminalGrowthRhoQ; }
    public double getKyBgp() { return kyBgp; }
    public int getSimQuarters() { return simQuarters; }
    public int getCurrentQuarterIdx() { return currentQuarterIdx; }
    public void setLogEnabled(boolean enabled) { this.logEnabled = enabled; }

    /**
     * Set the TFP terminal-growth convergence phase offset (see field javadoc). Applies
     * to the next {@link #solveForPath}.
     *
     * @throws IllegalArgumentException if quarters is negative
     */
    public void setTfpPhaseOffsetQuarters(int quarters) {
        if (quarters < 0) {
            throw new IllegalArgumentException("tfpPhaseOffsetQuarters must be >= 0, got " + quarters);
        }
        this.tfpPhaseOffsetQuarters = quarters;
    }

    public int getTfpPhaseOffsetQuarters() { return tfpPhaseOffsetQuarters; }

    /** Get the active Ramsey scenario, or null if none set. */
    public RamseyScenario getScenario() { return scenario; }

    /** Get capital stock (set by loadTerminalState or solveForPath). */
    public double getK() { return K; }

    /** Get TFP level (set by loadTerminalState or solveForPath). */
    public double getA() { return A; }

    /**
     * Get the capital stock at a specific quarter (0-based).
     * Useful for extracting the terminal K for forward projection.
     */
    public double getKAtQuarter(int q) {
        if (!solved) throw new IllegalStateException("Model not solved");
        if (q < 0 || q >= simQuarters) {
            throw new IndexOutOfBoundsException("Quarter index out of range: " + q);
        }
        return solvedK[q];
    }

    /**
     * Get the TFP level at a specific quarter (0-based).
     */
    public double getAAtQuarter(int q) {
        if (!solved) throw new IllegalStateException("Model not solved");
        if (q < 0 || q >= simQuarters) {
            throw new IndexOutOfBoundsException("Quarter index out of range: " + q);
        }
        return pathA[q];
    }

    /** Length of the solved path including the terminal tail (simulation quarters + tail). */
    int extendedLength() {
        if (!solved) throw new IllegalStateException("Model not solved");
        return pathN.length;
    }

    /** Employment at quarter {@code t} of the extended path, terminal tail included. */
    double pathNAt(int t) {
        if (!solved) throw new IllegalStateException("Model not solved");
        return pathN[t];
    }

    /** Population at quarter {@code t} of the extended path, terminal tail included. */
    double pathPAt(int t) {
        if (!solved) throw new IllegalStateException("Model not solved");
        return pathP[t];
    }

    // ========== Records ==========

    /**
     * Trend state at a given point in time.
     *
     * @param output           Quarterly real output (Y_trend)
     * @param consumption      Quarterly aggregate consumption (C_trend = P × c)
     * @param investment       Quarterly investment (I_trend = Y - C - G - NX)
     * @param capitalStock     Capital stock (K)
     * @param wage             Real wage per effective labor unit (MPL)
     * @param rQuarterly       Natural interest rate (quarterly, MPK - δ)
     * @param rAnnual          Natural interest rate (annualized)
     * @param labor            Labor input (N, persons in employment)
     * @param tfp              Total factor productivity level (A)
     * @param government       Quarterly government spending (G = g_Y × Y)
     * @param netExports       Quarterly net exports (NX = nx_Y × Y)
     * @param population       Resident population, all ages (P, the planner's consumers)
     */
    public record TrendState(
            double output,
            double consumption,
            double investment,
            double capitalStock,
            double wage,
            double rQuarterly,
            double rAnnual,
            double labor,
            double tfp,
            double government,
            double netExports,
            double population
    ) {}

    /**
     * A row from the MATLAB reference CSV (for cross-validation).
     *
     * @param time   Time label (e.g., "2000q1")
     * @param K      Capital stock
     * @param Y      Quarterly output
     * @param C      Aggregate consumption
     * @param I      Investment
     * @param w      Wage per effective labor unit
     * @param rStar  Natural interest rate (quarterly)
     * @param N      Persons in employment
     * @param L      Labor force
     * @param h      Hours per person employed
     * @param gY     Government spending share of GDP (0 if not available)
     * @param nxY    Net exports share of GDP (0 if not available)
     * @param P      Resident population, all ages (the planner's consumers)
     */
    public record ReferenceRow(
            String time,
            double K, double Y, double C, double I,
            double w, double rStar, double N, double L, double h,
            double gY, double nxY, double P
    ) {}
}
