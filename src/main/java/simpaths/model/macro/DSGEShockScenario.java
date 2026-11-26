package simpaths.model.macro;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Loads and serves exogenous DSGE shock paths from a CSV scenario file.
 *
 * <p>This class enables counterfactual simulations by injecting pre-specified
 * time series of DSGE shocks (e.g., cost-push, technology, monetary policy).</p>
 *
 * <h3>CSV Format</h3>
 * <p>The scenario file must have a header row with columns:</p>
 * <pre>
 *   year, quarter, eps_a, eps_r, eps_d, eps_p, eps_L, eps_sep, eps_h, eps_inv
 * </pre>
 * <p>Shock names must match the {@code exo_names} array in {@code model_info.json}.
 * Not all shocks need to be present — missing columns default to zero.
 * Quarters not listed in the file also default to zero.</p>
 *
 * <h3>Shock Units</h3>
 * <p>Every column is an innovation in standard-deviation units, as in {@link DSGEModel}:
 * 1.0 is a one-σ shock, eps_L and eps_h included. Because the labour force and hours are
 * persistent, a shock repeated every quarter accumulates (at a persistence of 0.95 a
 * constant 1σ shock converges to twenty times its size) and can trip
 * {@code mm_dsgeMaxStateDeviation}.</p>
 *
 * <p>Scenario shocks are the only shocks the DSGE receives.</p>
 *
 * @author DSGE-SimPaths Integration
 * @version 1.0
 */
public class DSGEShockScenario {

    private static final Logger log = LogManager.getLogger(DSGEShockScenario.class);

    /** Map from (year * 4 + quarter) → shock vector (length = nExo) */
    private final Map<Integer, double[]> shocksByQuarter = new LinkedHashMap<>();

    /** Number of exogenous shocks (must match DSGE model) */
    private final int nExo;

    /** Shock names from model (for column matching) */
    private final String[] modelShockNames;

    /** Mapping from CSV column index → model shock index (-1 if not mapped) */
    private int[] columnToShockIndex;

    /** Name of the loaded scenario file (for logging) */
    private String scenarioName;

    /** Whether logging is enabled */
    private boolean logEnabled = false;

    // ========== Constructor ==========

    /**
     * Create a shock scenario loader for a model with the given shock specification.
     *
     * @param modelShockNames shock names from model_info.json (e.g., ["eps_a", "eps_r", ...])
     * @param nExo            number of exogenous shocks
     */
    public DSGEShockScenario(String[] modelShockNames, int nExo) {
        this.modelShockNames = Objects.requireNonNull(modelShockNames, "modelShockNames must not be null");
        this.nExo = nExo;
        if (modelShockNames.length != nExo) {
            throw new IllegalArgumentException(
                "modelShockNames length (" + modelShockNames.length + ") != nExo (" + nExo + ")");
        }
    }

    // ========== Loading ==========

    /**
     * Load a scenario from a CSV file.
     *
     * <p>Expected format: header row with "year", "quarter", and one or more shock
     * name columns. Data rows with numeric values. Missing shock columns default to zero.</p>
     *
     * @param csvPath absolute or relative path to the scenario CSV file
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the format is invalid
     */
    public void loadFromFile(String csvPath) throws IOException {
        loadFromFile(Path.of(csvPath));
    }

    /**
     * Load a scenario from a CSV file.
     *
     * @param csvPath path to the scenario CSV file
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the format is invalid
     */
    public void loadFromFile(Path csvPath) throws IOException {
        if (!Files.exists(csvPath)) {
            throw new FileNotFoundException("Scenario file not found: " + csvPath);
        }

        scenarioName = csvPath.getFileName().toString();
        shocksByQuarter.clear();

        List<String> allLines = Files.readAllLines(csvPath);

        // Comment/blank filtering and row splitting live in MacroCsv so the two
        // scenario readers cannot drift apart.
        List<String> lines = MacroCsv.stripCommentsAndBlanks(allLines);

        if (lines.size() < 2) {
            throw new IllegalArgumentException("Scenario file must have header + at least one data row: " + csvPath);
        }

        // Parse header (first non-comment line)
        String[] headers = MacroCsv.splitRow(lines.get(0));
        for (int i = 0; i < headers.length; i++) {
            headers[i] = headers[i].trim().toLowerCase();
        }

        // Find year and quarter columns
        int yearCol = findColumn(headers, "year");
        int quarterCol = findColumn(headers, "quarter");
        if (yearCol < 0) {
            throw new IllegalArgumentException("Scenario CSV must have a 'year' column: " + csvPath);
        }
        if (quarterCol < 0) {
            throw new IllegalArgumentException("Scenario CSV must have a 'quarter' column: " + csvPath);
        }

        // Map shock columns to model indices
        columnToShockIndex = new int[headers.length];
        Arrays.fill(columnToShockIndex, -1);
        int mappedColumns = 0;

        for (int col = 0; col < headers.length; col++) {
            if (col == yearCol || col == quarterCol) continue;
            String colName = headers[col];
            int shockIdx = findShockIndex(colName);
            if (shockIdx >= 0) {
                columnToShockIndex[col] = shockIdx;
                mappedColumns++;
            } else if (!colName.isEmpty()) {
                if (logEnabled) {
                    log.warn("Scenario column '" + colName + "' does not match any model shock — ignored");
                }
            }
        }

        if (mappedColumns == 0) {
            throw new IllegalArgumentException(
                "No shock columns matched model shocks. Expected some of: " +
                String.join(", ", modelShockNames) + ". Found: " + String.join(", ", headers));
        }

        // Parse data rows (comments and blanks already filtered out)
        for (int row = 1; row < lines.size(); row++) {
            String line = lines.get(row);

            String[] parts = MacroCsv.splitRow(line);
            if (parts.length < Math.max(yearCol, quarterCol) + 1) {
                throw new IllegalArgumentException(String.format(
                    "Scenario row %d has too few columns (%d, need at least %d): %s",
                    row + 1, parts.length, Math.max(yearCol, quarterCol) + 1, csvPath));
            }

            int year;
            int quarter;
            try {
                year = Integer.parseInt(parts[yearCol].trim());
                quarter = Integer.parseInt(parts[quarterCol].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(String.format(
                    "Invalid year/quarter at row %d in %s: '%s', '%s'",
                    row + 1, csvPath, parts[yearCol].trim(), parts[quarterCol].trim()));
            }

            if (quarter < 1 || quarter > 4) {
                throw new IllegalArgumentException(String.format(
                    "Quarter must be 1-4, got %d at row %d in %s", quarter, row + 1, csvPath));
            }

            // Build shock vector for this quarter
            double[] shocks = new double[nExo];
            for (int col = 0; col < parts.length && col < columnToShockIndex.length; col++) {
                int shockIdx = columnToShockIndex[col];
                if (shockIdx >= 0) {
                    String val = parts[col].trim();
                    if (!val.isEmpty()) {
                        shocks[shockIdx] = MacroCsv.requireFinite(val, row + 1, headers[col], csvPath);
                    }
                }
            }

            int key = encodeQuarterKey(year, quarter);
            if (shocksByQuarter.containsKey(key)) {
                throw new IllegalArgumentException(String.format(
                    "Duplicate entry for %dQ%d in scenario file %s", year, quarter, csvPath));
            }
            shocksByQuarter.put(key, shocks);
        }

        if (logEnabled) {
            log.info(String.format("Loaded shock scenario '%s': %d quarters, %d shock columns mapped",
                scenarioName, shocksByQuarter.size(), mappedColumns));
            logScenarioSummary();
        }
    }

    // ========== Querying ==========

    /**
     * Get the exogenous shock vector for a given year and quarter.
     *
     * <p>Returns a zero vector if the quarter is not specified in the scenario file.</p>
     *
     * @param year    simulation year (e.g., 2025)
     * @param quarter quarter within year (1-4)
     * @return shock vector of length nExo (never null)
     */
    public double[] getShocks(int year, int quarter) {
        int key = encodeQuarterKey(year, quarter);
        double[] shocks = shocksByQuarter.get(key);
        return (shocks != null) ? shocks.clone() : new double[nExo];
    }

    /**
     * Check whether this scenario has any non-zero shocks for a given year.
     *
     * @param year simulation year
     * @return true if any quarter in this year has non-zero shocks
     */
    public boolean hasShocksForYear(int year) {
        for (int q = 1; q <= 4; q++) {
            int key = encodeQuarterKey(year, q);
            double[] shocks = shocksByQuarter.get(key);
            if (shocks != null) {
                for (double v : shocks) {
                    if (v != 0.0) return true;
                }
            }
        }
        return false;
    }

    /**
     * Check whether any scenario data has been loaded.
     *
     * @return true if a scenario file has been loaded with at least one data row
     */
    public boolean isLoaded() {
        return !shocksByQuarter.isEmpty();
    }

    /**
     * Get the name of the loaded scenario file.
     *
     * @return filename or null if not loaded
     */
    public String getScenarioName() {
        return scenarioName;
    }

    /**
     * Get the total number of quarters with shock data.
     *
     * @return count of quarters in the scenario
     */
    public int getQuarterCount() {
        return shocksByQuarter.size();
    }

    /**
     * Get a summary of the loaded scenario for logging.
     *
     * @return human-readable summary string
     */
    public String getSummary() {
        if (shocksByQuarter.isEmpty()) {
            return "No scenario loaded";
        }

        // Find year range
        int minYear = Integer.MAX_VALUE;
        int maxYear = Integer.MIN_VALUE;
        int nonZeroQuarters = 0;

        for (var entry : shocksByQuarter.entrySet()) {
            int key = entry.getKey();
            int year = decodeYearFromKey(key);
            minYear = Math.min(minYear, year);
            maxYear = Math.max(maxYear, year);

            for (double v : entry.getValue()) {
                if (v != 0.0) {
                    nonZeroQuarters++;
                    break;
                }
            }
        }

        return String.format("Scenario '%s': %d quarters (%d-%d), %d with non-zero shocks",
            scenarioName, shocksByQuarter.size(), minYear, maxYear, nonZeroQuarters);
    }

    public void setLogEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
    }

    // ========== Internal Helpers ==========

    private int findColumn(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equals(name)) return i;
        }
        return -1;
    }

    private int encodeQuarterKey(int year, int quarter) {
        return year * 4 + quarter;
    }

    private int decodeYearFromKey(int key) {
        return (key - 1) / 4;
    }

    private int findShockIndex(String columnName) {
        for (int i = 0; i < modelShockNames.length; i++) {
            if (modelShockNames[i].equalsIgnoreCase(columnName)) return i;
        }
        return -1;
    }

    private void logScenarioSummary() {
        if (!logEnabled || shocksByQuarter.isEmpty()) return;

        // Find which shocks have non-zero values
        boolean[] hasNonZero = new boolean[nExo];
        double[] maxAbs = new double[nExo];

        for (double[] shocks : shocksByQuarter.values()) {
            for (int i = 0; i < nExo; i++) {
                if (shocks[i] != 0.0) {
                    hasNonZero[i] = true;
                    maxAbs[i] = Math.max(maxAbs[i], Math.abs(shocks[i]));
                }
            }
        }

        var sb = new StringBuilder("  Active scenario shocks:");
        for (int i = 0; i < nExo; i++) {
            if (hasNonZero[i]) {
                sb.append(String.format(" %s(max|%.4f|)", modelShockNames[i], maxAbs[i]));
            }
        }
        log.info(sb.toString());
    }
}
