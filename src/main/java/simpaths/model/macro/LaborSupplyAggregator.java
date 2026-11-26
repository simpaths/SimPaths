package simpaths.model.macro;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import simpaths.model.Person;
import simpaths.model.enums.Labour;

/**
 * Aggregates SimPaths' labour supply for the macro layer's level diagnostics
 * (MacroPaths.csv) and for the planner's end-of-year feedback. It computes no shocks:
 * the DSGE takes no input from SimPaths.
 *
 * <h2>Aggregation Strategy</h2>
 *
 * <p>For each working-age person in the microsimulation:</p>
 * <ol>
 *   <li>Labor force participation: Count persons with positive hours (hours &gt; 0)</li>
 *   <li>Hours per worker: Average weekly hours among those working</li>
 * </ol>
 *
 * <p>{@link #computeRawAggregates} returns these with the working-age population, the
 * at-risk pool, the total population and the labour-category distribution. The start-year
 * anchors (L and h latched once after the first labour market, the head counts eagerly in
 * the start year) are the bases of the level indices.</p>
 *
 * <h2>Extensive Margin (L) — Critical Design Decision</h2>
 *
 * <p>L is the population-weighted count of persons who are <b>at risk of work</b>
 * (SimPaths' {@link simpaths.model.Person#atRiskOfWork()} — the same gate the
 * labour market uses to enter the discrete-choice model) <b>and</b> have
 * <b>positive working hours</b> (hours &gt; 0.5). Gating L on the canonical
 * at-risk predicate, rather than a looser "not student/retired" test, both
 * makes L responsive to wage changes (the micro-macro feedback) and prevents a
 * person who never entered this year's labour-supply model from leaking into L
 * via stale hours. The at-risk pool determines who enters the labor supply
 * model but is not itself part of L.</p>
 *
 * <h2>Weighting — Correct For Both Run Modes</h2>
 *
 * <p>Every count-like aggregate is a weighted sum of {@code Person.getWeight()},
 * not a raw head count. SimPaths supports two population modes:</p>
 * <ul>
 *   <li><b>Unweighted</b> ({@code useWeights = false}, the default): the survey
 *       is expanded by cloning and every person's weight is reset to exactly
 *       {@code 1.0} ({@code Household.resetWeights(1.0)}), newborns inheriting
 *       the mother's weight. Weighted sums then reduce identically to head
 *       counts.</li>
 *   <li><b>Weighted</b> ({@code useWeights = true}): survey households are kept
 *       with heterogeneous survey weights. A head count would bias L, h and
 *       the level indices; weighting removes that bias.</li>
 * </ul>
 * <p>Because weighting by {@code getWeight()} is correct in <em>both</em> modes,
 * the aggregator does not depend on the run mode. Invalid
 * weights (null, non-finite, non-positive) are rejected loudly rather than
 * silently degrading the aggregates (see {@code personWeight}).</p>
 *
 * @author DSGE-SimPaths Integration
 * @version 1.0
 */
public class LaborSupplyAggregator {

    private static final Logger log = LogManager.getLogger(LaborSupplyAggregator.class);

    // ========== Constants ==========

    /*
     * Working-age band for every macro aggregate (WAP, at-risk pool, L, hours).
     *
     * CONTRACT — this band is NOT arbitrary and must NOT be changed in
     * isolation. It deliberately encodes the Ramsey/DSGE working-age concept:
     *
     *  - It MUST equal the working-age band used by build_population_projection.m
     *    in input_processing/macromodel/ (currently 15-64), because that script builds
     *    the N path the Ramsey trend layer consumes. L and the exported
     *    demographic decomposition must be measured on the SAME population the
     *    planner's labour input N represents; if these diverge, the planner's
     *    feedback and the demographic series are silently measured against the
     *    wrong denominator. There is no shared config between the MATLAB build
     *    and this Java runtime, so the only thing keeping them in sync is this
     *    contract — change both sides together or not at all.
     *
     *  - It is intentionally NARROWER than SimPaths' own flexible-labour band
     *    (Parameters.MIN/MAX_AGE_FLEXIBLE_LABOUR_SUPPLY = 16-75, used by
     *    Person.atRiskOfWork()). L and the at-risk pool are the
     *    atRiskOfWork() subset of THIS band, so the effective at-risk lower
     *    bound is max(15, 16) = 16: a 15-year-old counts toward WAP but never
     *    toward the at-risk pool or L (atRiskOfWork() excludes age < 16). That
     *    asymmetry is correct — 15-year-olds are working-age population but not
     *    labour-market participants — and must not be "reconciled" by widening
     *    atRiskOfWork()'s band or narrowing WAP.
     */

    /** Minimum working age (inclusive). See CONTRACT above before changing. */
    public static final int DEFAULT_MIN_WORKING_AGE = 15;

    /** Maximum working age (inclusive). See CONTRACT above before changing. */
    public static final int DEFAULT_MAX_WORKING_AGE = 64;

    /** Minimum valid hours to count as "working" */
    private static final double MIN_HOURS_WORKING = 0.5;

    /** Whether DSGE logging is enabled */
    private boolean logEnabled = false;

    // ========== Start-Year Anchors (level indices) ==========

    /** Start-year labor force (fixed, never rolls forward). Used for the L level index. */
    private double startYearL;

    /** Start-year average hours per worker (fixed, never rolls forward). Used for h level index. */
    private double startYearH;

    /** Start-year total population (all ages). Used for decomposition index. */
    private double startYearTotalPop;

    /** Start-year working-age population (15-64). Used for decomposition index. */
    private double startYearWAP;

    /** Start-year at-risk pool (non-student, non-retired 15-64). Used for decomposition index. */
    private double startYearAtRisk;

    /** Whether the L/h start-year values (post-labour-market) have been recorded */
    private boolean startYearLSet = false;

    /**
     * Whether the demographic start anchors (TotalPop, WAP, AtRisk) have been
     * recorded. These are labour-market-invariant head counts and are latched
     * eagerly in the first simulation year (before the labour market runs), so
     * the demographic indices are available from year 1 without waiting for the
     * post-labour-market L/h anchor latch. Split from {@link #startYearLSet}
     * on purpose: L/h MUST be captured post-labour-market (input-DB hours differ
     * from model-assigned hours), but the head counts must not be — see
     * {@link #latchDemographicAnchors}.
     */
    private boolean startYearDemogSet = false;

    // ========== Start-Year Latch ==========

    /**
     * Latch the start-year anchors from the post-labour-market population of the first year
     * with a labour market ({@code startYear + 1}). The L/h anchors are latched once and
     * never move again; the demographic anchors are latched here only if the eager start-year
     * latch ({@link #latchDemographicAnchors}) has not run. The latch also opens the gate of
     * the workers-derived level columns ({@link #isStartYearLatched}).
     *
     * @param persons Set of all persons in the simulation, after the labour market
     * @return The aggregates of {@code persons}
     */
    public LaborSupplyAggregates latchStartYearAnchors(Set<Person> persons) {
        LaborSupplyAggregates aggregates = computeRawAggregates(persons);

        // L/h start anchors: MUST be the post-labour-market state, latched once.
        if (!startYearLSet) {
            startYearL = aggregates.laborForce();
            startYearH = aggregates.avgHoursPerWorker();
            startYearLSet = true;
            if (logEnabled) {
                log.info(String.format("Start-year L/h recorded (post-labour-market): L_0=%.0f, h_0=%.2f",
                        startYearL, startYearH));
            }
        }

        // Demographic anchors are labour-market-invariant. If they were not
        // already latched eagerly in year 1 (latchDemographicAnchors), latch
        // them here as a fallback so callers that never call the eager path
        // still get them. If the eager latch already ran, its values
        // are kept (they are numerically identical — no demographic process
        // runs between the eager point and here within the first year).
        if (!startYearDemogSet) {
            startYearTotalPop = aggregates.totalPopulation();
            startYearWAP = aggregates.workingAgePopulation();
            startYearAtRisk = aggregates.atRiskPool();
            startYearDemogSet = true;
            if (logEnabled) {
                log.info(String.format("Start-year demographic anchors recorded (fallback): TotalPop_0=%.0f, WAP_0=%.0f, AtRisk_0=%.0f",
                        startYearTotalPop, startYearWAP, startYearAtRisk));
            }
        }

        return aggregates;
    }

    /**
     * Eagerly latch the demographic start anchors (TotalPop, WAP, AtRisk) from
     * the first-year population, before the labour market runs.
     *
     * <p>These are pure head counts, invariant to the labour-market update, so
     * latching them at the macro event in year 1 yields values numerically
     * identical to the post-labour-market latch — only earlier, so the
     * demographic indices (wap_level, atrisk_level, total_population) are
     * emitted from the first simulated year instead of being skipped until the
     * L/h anchors are latched. It deliberately does NOT touch the L/h anchors
     * or the post-labour-market latch: those must remain post-labour-market,
     * because the input database's hours differ from model-assigned hours.</p>
     *
     * <p>Idempotent: a no-op once the anchors are set (by this method or the
     * {@link #latchStartYearAnchors} fallback).</p>
     *
     * @param persons Set of all persons in the simulation
     */
    public void latchDemographicAnchors(Set<Person> persons) {
        if (startYearDemogSet) {
            return;
        }
        LaborSupplyAggregates a = computeRawAggregates(persons);
        startYearTotalPop = a.totalPopulation();
        startYearWAP = a.workingAgePopulation();
        startYearAtRisk = a.atRiskPool();
        startYearDemogSet = true;
        if (logEnabled) {
            log.info(String.format("Start-year demographic anchors recorded (eager, pre-labour-market): TotalPop_0=%.0f, WAP_0=%.0f, AtRisk_0=%.0f",
                    startYearTotalPop, startYearWAP, startYearAtRisk));
        }
    }

    /**
     * Whether the post-labour-market latch ({@link #latchStartYearAnchors}) has run. Gates the
     * workers-derived level columns, which are meaningful only once L comes from SimPaths'
     * labour-supply model.
     */
    public boolean isStartYearLatched() {
        return startYearLSet;
    }

    public void setLogEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
    }

    /**
     * Get the start-year labor force (L_0).
     * @return start-year labor force count, or 0 if not yet set
     */
    public double getStartYearL() {
        return startYearLSet ? startYearL : 0.0;
    }

    /**
     * Get the start-year average hours per worker (h_0).
     * @return start-year hours per worker, or 0 if not yet set
     */
    public double getStartYearH() {
        return startYearLSet ? startYearH : 0.0;
    }

    /**
     * Get the start-year total population (all ages).
     * @return start-year total population, or 0 if not yet set
     */
    public double getStartYearTotalPop() {
        return startYearDemogSet ? startYearTotalPop : 0.0;
    }

    /**
     * Get the start-year working-age population (15-64).
     * @return start-year WAP, or 0 if not yet set
     */
    public double getStartYearWAP() {
        return startYearDemogSet ? startYearWAP : 0.0;
    }

    /**
     * Get the start-year at-risk pool (non-student, non-retired 15-64).
     * @return start-year at-risk count, or 0 if not yet set
     */
    public double getStartYearAtRisk() {
        return startYearDemogSet ? startYearAtRisk : 0.0;
    }

    // ========== Core Aggregation ==========

    /**
     * Aggregate SimPaths' labour supply: L, mean hours per worker, the head counts and the
     * labour-category distribution of {@code persons}. Reads the persons only; no state of
     * this aggregator changes.
     *
     * @param persons Set of all persons in the simulation
     * @return Raw labor supply aggregates
     */
    public LaborSupplyAggregates computeRawAggregates(Set<Person> persons) {
        Objects.requireNonNull(persons, "Persons set cannot be null");

        // All aggregates are population-weighted. In unweighted runs every person's
        // weight is reset to exactly 1.0 (SimPathsModel.cloneHousehold ->
        // Household.resetWeights(1.0), and newborns inherit the mother's weight),
        // so these weighted sums reduce identically to plain head counts. In
        // weighted runs survey weights are heterogeneous and a head count would
        // bias L, h and the level indices. Weighting by getWeight() is therefore
        // correct in BOTH modes.
        double totalPopulationWeight = 0.0;
        double workingAgeWeight = 0.0;
        double atRiskWeight = 0.0;
        double laborForceWeight = 0.0;   // extensive margin L = weighted workers with hours > 0
        double totalHours = 0.0;         // weighted hours = sum of weight * hours
        Labour[] countryCategories = Labour.values();
        Map<Labour, double[]> categoryAccumulators = new LinkedHashMap<>();
        for (Labour category : countryCategories) {
            categoryAccumulators.put(category, new double[2]);
        }

        for (Person person : persons) {
            if (person == null) {
                continue;
            }

            double weight = personWeight(person);

            totalPopulationWeight += weight;

            int age = person.getDemAge();
            if (age < DEFAULT_MIN_WORKING_AGE || age > DEFAULT_MAX_WORKING_AGE) {
                continue;
            }

            workingAgeWeight += weight;

            // Labor force: the at-risk pool only (students, retired etc. excluded)
            if (!defaultWorkingAgeFilter(person)) {
                continue;
            }

            atRiskWeight += weight;

            // Get hours worked
            Labour category = person.getLabourSupplyWeekly();
            if (category == null || !categoryAccumulators.containsKey(category)) {
                category = Labour.ZERO;
            }
            int hoursWeekly = getPersonHours(person);
            double[] categoryTotals = categoryAccumulators.get(category);
            categoryTotals[0] += weight;
            categoryTotals[1] += weight * hoursWeekly;

            if (hoursWeekly > MIN_HOURS_WORKING) {
                laborForceWeight += weight;
                totalHours += weight * hoursWeekly;
            }
        }

        // Intensive margin is the weighted mean hours among workers:
        // sum(w*h) / sum(w). Avoid division by zero.
        double avgHoursPerWorker = laborForceWeight > 0.0 ? totalHours / laborForceWeight : 0.0;
        // Employment rate: weighted share of working-age pop with positive hours
        double employmentRate = workingAgeWeight > 0.0 ? laborForceWeight / workingAgeWeight : 0.0;
        Map<Labour, LabourCategorySummary> categoryDistribution = new LinkedHashMap<>();
        for (Labour category : countryCategories) {
            double[] totals = categoryAccumulators.get(category);
            double categoryWeight = totals[0];
            double meanHours = categoryWeight > 0.0 ? totals[1] / categoryWeight : 0.0;
            double share = atRiskWeight > 0.0 ? 100.0 * categoryWeight / atRiskWeight : 0.0;
            categoryDistribution.put(category, new LabourCategorySummary(share, categoryWeight, meanHours));
        }

        return new LaborSupplyAggregates(
            laborForceWeight,       // laborForce = extensive margin (weighted workers, hours > 0)
            avgHoursPerWorker,      // avgHoursPerWorker (weighted mean among workers)
            totalHours,             // totalHours (weighted)
            workingAgeWeight,       // workingAgePopulation (weighted)
            employmentRate,         // participationRate (weighted employment rate)
            atRiskWeight,           // atRiskPool (weighted, non-student/retired 15-64)
            totalPopulationWeight,  // totalPopulation (weighted, all persons all ages)
            categoryDistribution
        );
    }

    /**
     * Build a verbose diagnostic of the labour-supply category distribution and
     * mean hours per category over the current at-risk pool.
     *
     * <p>Uses exactly the age band and at-risk filter that
     * {@link #computeRawAggregates} applies, so the shares are taken over the
     * same denominator as the participation rate and {@code avgHoursPerWorker}.
     * Persons are binned by {@link Person#getLabourSupplyWeekly()} (the discrete
     * choice the labour-supply model assigned, {@code ZERO} = not working).
     * Mean hours per category is the population-weighted mean of the assigned
     * weekly hours within that category.</p>
     *
     * <p>This is the lens for the wage-feedback intensive-margin question:
     * because PL has only {@code ZERO/PL_1[1-39]/PL_2[40]/PL_3[41-60]} and the
     * within-bin hours draw is person-frozen, an aggregate mean-hours decline
     * can only come from mass shifting toward the wide {@code PL_1} bin — this
     * table makes that shift directly observable.</p>
     *
     * @param persons Set of all persons in the simulation
     * @return Multi-line, log-ready category distribution table
     */
    public String formatLabourCategoryDistribution(Set<Person> persons) {
        Objects.requireNonNull(persons, "Persons set cannot be null");

        return formatLabourCategoryDistribution(computeRawAggregates(persons));
    }

    public String formatLabourCategoryDistribution(LaborSupplyAggregates aggregates) {
        Objects.requireNonNull(aggregates, "Aggregates cannot be null");

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "Labour category distribution (at-risk pool=%.0f, mean hours/worker=%.2f):",
                aggregates.atRiskPool(), aggregates.avgHoursPerWorker()));
        for (Map.Entry<Labour, LabourCategorySummary> e : aggregates.categoryDistribution().entrySet()) {
            LabourCategorySummary summary = e.getValue();
            sb.append(String.format(
                    "%n    %-14s share=%5.1f%%  weight=%10.0f  meanHours=%5.2f",
                    e.getKey().name(), summary.share(), summary.weight(), summary.meanHours()));
        }
        return sb.toString();
    }

    /**
     * Returns the person's population weight, failing loudly on values that
     * would silently corrupt the aggregates. A non-finite or non-positive
     * weight means the run is misconfigured (e.g. a person created without a
     * weight); silently treating it as 0 or 1 would bias L and the planner's
     * feedback with no warning, so this is a hard error rather than a
     * degraded-but-quiet path.
     */
    private double personWeight(Person person) {
        Double w = person.getWeight();
        if (w == null || !Double.isFinite(w) || w <= 0.0) {
            throw new IllegalStateException(
                "Person " + person.getKey() + " has invalid population weight (" + w
                + "). Labour-supply aggregation requires every person to carry a "
                + "positive, finite weight (1.0 in unweighted runs, the survey "
                + "weight in weighted runs).");
        }
        return w;
    }

    /**
     * Get hours worked by a person, handling nulls safely.
     */
    private int getPersonHours(Person person) {
        try {
            return person.getLabourSupplyHoursWeekly();
        } catch (RuntimeException e) {
            // Labour supply not yet initialized
            return 0;
        }
    }

    // ========== Working-Age Filter ==========

    /**
     * Default filter: working-age (15-64) AND at risk of work in SimPaths'
     * own sense.
     *
     * <p>This delegates the eligibility decision to {@link Person#atRiskOfWork()}
     * — the exact predicate the labour market uses to decide who enters the
     * discrete-choice labour-supply model. It excludes not only students and
     * retirees but also the long-term disabled and persons with a non-finite
     * inverse Mills ratio ("will not work for any wage"). A looser
     * Les_c4-only test would make the at-risk pool — and hence the
     * participation/employment-rate denominator and the exported demographic
     * series — diverge from the population SimPaths actually sends through the
     * labour market, with a bias that grows as disability prevalence rises with
     * age.</p>
     *
     * <p>Because this gate is applied before the hours test, it also makes the
     * extensive margin L robust: a person who did not pass through this year's
     * labour-supply model cannot leak into L via a stale positive
     * {@code labHrsWorkEnumWeek}, even though the labour market normally already
     * resets not-at-risk persons to zero hours.</p>
     *
     * <p>The explicit [15, 64] age band is retained for working-age-population
     * consistency and is intentionally <em>not</em> reconciled here with
     * {@code atRiskOfWork()}'s own flexible-labour age bounds (16-75); that age
     * basis is tracked separately. The net effect is the intersection of the
     * two bands.</p>
     */
    private boolean defaultWorkingAgeFilter(Person person) {
        if (person == null) {
            return false;
        }

        // Check age (working-age-population band; see Javadoc on the age basis)
        int age = person.getDemAge();
        if (age < DEFAULT_MIN_WORKING_AGE || age > DEFAULT_MAX_WORKING_AGE) {
            return false;
        }

        // Canonical SimPaths labour-market entry gate (single source of truth).
        return isAtRiskOfWork(person);
    }

    /**
     * Safe wrapper around {@link Person#atRiskOfWork()}. That method recomputes
     * an inverse Mills ratio and can throw on a degenerate probit CDF; a
     * pathological individual must not abort the whole macro aggregation, and
     * "cannot evaluate participation" is conservatively treated as not at risk
     * of work — consistent with {@code atRiskOfWork()}'s own non-finite-IMR
     * branch.
     */
    private boolean isAtRiskOfWork(Person person) {
        try {
            return person.atRiskOfWork();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Whether a person is in the at-risk pool — i.e. would pass the
     * working-age filter and so be eligible to enter the discrete-choice labour
     * supply model. This is the canonical entry gate used by {@link #computeRawAggregates}
     * to compute {@code atRiskPool}, so callers that need to measure something
     * over the same population (e.g. the realized-wage diagnostic) should use
     * this predicate rather than re-implementing the filter.
     *
     * @param person the person to test (may be null)
     * @return true if the person is in the at-risk pool
     */
    public boolean isInAtRiskPool(Person person) {
        if (person == null) {
            return false;
        }
        return defaultWorkingAgeFilter(person);
    }

    @Override
    public String toString() {
        if (!startYearLSet) {
            return "LaborSupplyAggregator[start year not latched]";
        }
        return String.format(
            "LaborSupplyAggregator[start year: L=%.1f, h=%.2f]", startYearL, startYearH);
    }

    // ========== Inner Records ==========

    /**
     * Container for raw labor supply aggregates (before computing deviations).
     *
     * <p><b>Critical distinction:</b></p>
     * <ul>
     *   <li>{@code laborForce} = extensive margin of labor supply = at-risk-of-work
     *       persons with positive hours (hours > 0.5). This is the L the planner's feedback
     *       reads and can respond to wage changes because it is measured AFTER the discrete
     *       choice labor supply model.</li>
     *   <li>{@code atRiskPool} = persons aged 15-64 who are at risk of work in SimPaths'
     *       sense ({@link simpaths.model.Person#atRiskOfWork()}: not student/retired, not
     *       long-term disabled, finite inverse Mills ratio) and therefore ENTER the labor
     *       supply model. This is a demographic concept that does not respond to wages.</li>
     * </ul>
     *
     * <p>All count-like fields ({@code laborForce}, {@code workingAgePopulation},
     * {@code atRiskPool}, {@code totalPopulation}) are population-weighted sums,
     * not raw head counts: they equal the head count when every weight is 1.0
     * (unweighted runs) and the survey-weighted total otherwise.</p>
     *
     * @param laborForce          Extensive margin: weighted at-risk persons with positive hours (L)
     * @param avgHoursPerWorker   Weighted mean weekly hours among workers (intensive margin)
     * @param totalHours          Weighted total hours worked by all workers (sum of weight*hours)
     * @param workingAgePopulation Weighted working-age population (15-64)
     * @param participationRate   Weighted employment rate (laborForce / workingAgePop)
     * @param atRiskPool          Weighted persons entering labor supply model (Person.atRiskOfWork(), age 15-64)
     * @param totalPopulation     Weighted total simulation population (all ages)
     */
    public record LaborSupplyAggregates(
            double laborForce,
            double avgHoursPerWorker,
            double totalHours,
            double workingAgePopulation,
            double participationRate,
            double atRiskPool,
            double totalPopulation,
            Map<Labour, LabourCategorySummary> categoryDistribution
    ) {
        public LaborSupplyAggregates {
            categoryDistribution = categoryDistribution == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(categoryDistribution));
        }

        public LaborSupplyAggregates(
                double laborForce,
                double avgHoursPerWorker,
                double totalHours,
                double workingAgePopulation,
                double participationRate,
                double atRiskPool,
                double totalPopulation
        ) {
            this(laborForce, avgHoursPerWorker, totalHours, workingAgePopulation,
                    participationRate, atRiskPool, totalPopulation, Collections.emptyMap());
        }

        @Override
        public String toString() {
            return String.format(
                "LaborSupplyAggregates[L=%.0f workers with positive hours (%.1f%% of %.0f WAP, %.0f at-risk, %.0f total pop), h=%.1f hrs/wk, total=%.0f hrs]",
                laborForce, participationRate * 100, workingAgePopulation,
                atRiskPool, totalPopulation, avgHoursPerWorker, totalHours);
        }
    }

    public record LabourCategorySummary(
            double share,
            double weight,
            double meanHours
    ) {}
}
