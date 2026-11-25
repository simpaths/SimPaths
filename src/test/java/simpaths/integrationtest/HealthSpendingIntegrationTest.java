package simpaths.integrationtest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Opt-in end-to-end smoke test. It needs the normal prepared SimPaths input DB
 * and runner JAR; the runner also updates input/DatabaseCountryYear.xlsx.
 */
@EnabledIfSystemProperty(named = "simpaths.healthSpending.integration", matches = "true")
class HealthSpendingIntegrationTest {

    private static final String SMOKE_CONFIG = """
            maxNumberOfRuns: 1
            executeWithGui: false
            randomSeed: 606
            startYear: 2019
            endYear: 2029
            popSize: 20000
            model_args:
              alignPopulation: true
              alignCohabitation: true
              alignFertility: true
              FERTILITY_ALIGNMENT_END_YEAR: 2029
              PARTNERSHIP_ALIGNMENT_END_YEAR: 2029
            collector_args:
              persistHealthSpendingStatistics: true
              persistPersons: false
              persistBenefitUnits: false
              persistHouseholds: false
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    void shortRunExportsReconciling2028And2029Spending() throws Exception {
        assertTrue(Files.isRegularFile(Path.of("input/input.mv.db")),
                "Prepare the SimPaths donor database before this opt-in test");
        assertTrue(Files.isRegularFile(Path.of("multirun.jar")),
                "Build multirun.jar before this opt-in test");

        long started = System.currentTimeMillis();
        runSmokeSimulation(temporaryDirectory.resolve("health-spending-smoke.log"));

        Path csvDirectory = findNewCsvDirectory(started);
        Map<Integer, String[]> annual = rowsByYear(csvDirectory.resolve("HealthSpendingStatistics.csv"));
        assertEquals(2, annual.size());
        assertTrue(annual.containsKey(2028));
        assertTrue(annual.containsKey(2029));

        List<String> annualLines = Files.readAllLines(csvDirectory.resolve("HealthSpendingStatistics.csv"));
        String[] annualHeader = annualLines.get(0).split(",", -1);
        int benchmarkCol = column(annualHeader, "benchmarkAnnualGbp2015");
        int fixedCol = column(annualHeader, "fixedAgeStructureAnnualGbp2015");
        int effectCol = column(annualHeader, "ageingEffectAnnualGbp2015");
        int populationCol = column(annualHeader, "populationExpandedPeople");

        double baseBenchmark = number(annual.get(2028), benchmarkCol);
        assertClose(baseBenchmark, number(annual.get(2028), fixedCol));
        assertClose(0.0, number(annual.get(2028), effectCol));
        assertFalse(annual.get(2029)[fixedCol].equals("null"));

        List<String> ageLines = Files.readAllLines(csvDirectory.resolve("HealthSpendingByAge.csv"));
        String[] ageHeader = ageLines.get(0).split(",", -1);
        int agePopulationCol = column(ageHeader, "populationExpandedPeople");
        int costCol = column(ageHeader, "costAnnualGbp2015PerPerson");
        int expenditureCol = column(ageHeader, "expenditureAnnualGbp2015");
        Map<Integer, Double> expenditureByYear = new HashMap<>();
        Map<Integer, Double> populationByYear = new HashMap<>();
        for (int i = 1; i < ageLines.size(); i++) {
            String[] row = ageLines.get(i).split(",", -1);
            int year = (int) Double.parseDouble(row[column(ageHeader, "time")]);
            double population = number(row, agePopulationCol);
            double expenditure = number(row, expenditureCol);
            assertClose(population * number(row, costCol), expenditure);
            expenditureByYear.merge(year, expenditure, Double::sum);
            populationByYear.merge(year, population, Double::sum);
        }
        assertEquals(annual.keySet(), expenditureByYear.keySet());
        for (int year : annual.keySet()) {
            assertClose(number(annual.get(year), benchmarkCol), expenditureByYear.get(year));
            assertClose(number(annual.get(year), populationCol), populationByYear.get(year));
        }

        // Diagnostic only: the selected alignment workbook implies this 2028 UK total.
        // The realised weighted/unweighted population need not equal it exactly.
        System.out.printf("Health-spending smoke run: realised 2028 population %.2f; "
                        + "prepared alignment reference 69427756.31; difference %.2f%n",
                populationByYear.get(2028), populationByYear.get(2028) - 69427756.31);
    }

    private static void runSmokeSimulation(Path log) throws IOException, InterruptedException {
        // The runner resolves -config names under config/.
        Path config = Files.createTempFile(Path.of("config"), "health-spending-smoke-", ".yml");
        try {
            Files.writeString(config, SMOKE_CONFIG);
            Process process = new ProcessBuilder("java", "-jar", "multirun.jar", "-config",
                    config.getFileName().toString(), "-P", "none")
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!process.waitFor(20, TimeUnit.MINUTES)) {
                process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
                fail("Short health-spending simulation timed out; see " + log);
            }
            assertEquals(0, process.exitValue(), () -> "Short simulation failed:\n" + tail(log));
        } finally {
            Files.deleteIfExists(config);
        }
    }

    private static Path findNewCsvDirectory(long started) throws IOException {
        try (var folders = Files.list(Path.of("output"))) {
            return folders.filter(Files::isDirectory)
                    .map(path -> path.resolve("csv"))
                    .filter(path -> Files.isRegularFile(path.resolve("HealthSpendingStatistics.csv"))
                            && Files.isRegularFile(path.resolve("HealthSpendingByAge.csv")))
                    .filter(path -> modifiedAtOrAfter(path.resolve("HealthSpendingStatistics.csv"),
                            started - 2000))
                    .findFirst().orElseThrow(() -> new AssertionError(
                            "No new health-spending CSV pair was written under output/"));
        }
    }

    private static boolean modifiedAtOrAfter(Path file, long time) {
        try {
            return Files.getLastModifiedTime(file).toMillis() >= time;
        } catch (IOException e) {
            return false;
        }
    }

    private static Map<Integer, String[]> rowsByYear(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        String[] header = lines.get(0).split(",", -1);
        int timeCol = column(header, "time");
        Map<Integer, String[]> rows = new HashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] row = lines.get(i).split(",", -1);
            int year = (int) Double.parseDouble(row[timeCol]);
            assertNull(rows.put(year, row), "Duplicate annual year " + year);
        }
        return rows;
    }

    private static int column(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (name.equals(header[i])) return i;
        }
        throw new AssertionError("Missing CSV column " + name);
    }

    private static double number(String[] row, int column) {
        return Double.parseDouble(row[column]);
    }

    private static void assertClose(double expected, double actual) {
        assertEquals(expected, actual, Math.max(1.0, Math.abs(expected)) * 1e-9);
    }

    private static String tail(Path log) {
        try {
            List<String> lines = Files.readAllLines(log);
            return String.join("\n", lines.subList(Math.max(0, lines.size() - 40), lines.size()));
        } catch (IOException e) {
            return "Could not read process log: " + e.getMessage();
        }
    }
}
