package simpaths.model.macro;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import simpaths.model.Person;
import simpaths.model.enums.Labour;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UKMacroBundleTest {

    @ParameterizedTest
    @CsvSource({"MacroModel,2023", "MacroModel-2019q1,2019"})
    void loadsTheMatchingHandoffAndAppliesFeedback(String directory, int startYear) {
        Path bundle = Path.of("input", "UK", directory);
        MacroModelManager manager = ramseyManager(bundle);
        MacroModelManager mismatch = ramseyManager(bundle);

        Person worker = mock(Person.class);
        double[] weight = {1.0};
        when(worker.getWeight()).thenAnswer(invocation -> weight[0]);
        when(worker.getDemAge()).thenReturn(40);
        when(worker.atRiskOfWork()).thenReturn(true);
        when(worker.getLabourSupplyWeekly()).thenReturn(Labour.ZERO);
        when(worker.getLabourSupplyHoursWeekly()).thenReturn(40);
        when(worker.getLabWageFullTimeHrly()).thenReturn(15.0);

        Set<Person> people = Set.of(worker);
        RuntimeException wrongHandoff = assertThrows(RuntimeException.class,
                () -> mismatch.updateEquilibrium(startYear + 1, startYear + 1, "UK", people));
        assertTrue(wrongHandoff.toString().contains("handoff") ||
                (wrongHandoff.getCause() != null && wrongHandoff.getCause().toString().contains("handoff")));

        manager.updateEquilibrium(startYear, startYear, "UK", people);
        manager.applyRamseyRecursiveFeedback(startYear, people);
        assertTrue(manager.getRamseyTrend().isSolved());
        double initialWage = manager.getWageDeviation();
        for (int year = startYear + 1; year <= startYear + 3; year++) {
            weight[0] = year == startYear + 1 ? 1.0 : 1.1;
            manager.updateEquilibrium(year, startYear, "UK", people);
            manager.latchStartYearAnchorsPostLabourMarket(year, people);
            manager.applyRamseyRecursiveFeedback(year, people);
        }
        assertTrue(manager.isStartYearLatched());
        assertTrue(manager.getWageDeviation() > initialWage);
    }

    @ParameterizedTest
    @CsvSource({"MacroModel,eps_r", "MacroModel-2019q1,eps_r", "MacroModel-2019q1,eps_h"})
    void replaysAOneSigmaDynareImpulse(String directory, String shock) throws IOException {
        Path bundle = Path.of("input", "UK", directory);
        DSGEModel model = new DSGEModel();
        model.setLogEnabled(false);
        model.loadFromDirectory(bundle.toString());
        assertTrue(model.isLoaded());

        int shockIndex = List.of(model.getShockNames()).indexOf(shock);
        assertTrue(shockIndex >= 0);
        List<String> rows = Files.readAllLines(bundle.resolve("irfs_" + shock + ".csv"));
        assertTrue(rows.size() > 1);
        List<String> columns = List.of(rows.getFirst().split(","));
        int wage = columns.indexOf("w");
        int hours = columns.indexOf("h");
        assertTrue(wage >= 0 && hours >= 0);

        for (int row = 1; row < rows.size(); row++) {
            double[] innovations = new double[model.getNumShocks()];
            if (row == 1) {
                innovations[shockIndex] = 1.0;
            }
            DSGEState state = model.stepQuarterly(innovations);
            String[] expected = rows.get(row).split(",");
            double wagePerWorker = Double.parseDouble(expected[wage]);
            double hoursPerWorker = Double.parseDouble(expected[hours]);
            assertEquals(wagePerWorker - hoursPerWorker, state.getHourlyWageDeviation(), 1e-8,
                    "Hourly wage response at horizon " + row);
        }
    }

    @ParameterizedTest
    @CsvSource({"MacroModel", "MacroModel-2019q1"})
    void bundleProvenanceResolvesAgainstTheUkCheckout(String directory) throws Exception {
        Path bundle = Path.of("input", "UK", directory);
        assertFalse(Files.exists(bundle.resolve("BUNDLE_NOT_VERIFIED.txt")));
        List<String> hashes = Files.readAllLines(bundle.resolve("BUNDLE_PROVENANCE.txt")).stream()
                .filter(line -> line.startsWith("sha256 "))
                .toList();
        assertFalse(hashes.isEmpty());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String line : hashes) {
            String[] fields = line.split(" ", 4);
            Path file = Path.of(fields[3]);
            assertTrue(Files.isRegularFile(file), "Bundle input is missing: " + file);
            byte[] bytes = Files.readAllBytes(file);
            if (fields[1].equals("t")) {
                for (byte value : bytes) {
                    if (value != '\r') {
                        digest.update(value);
                    }
                }
            } else {
                assertEquals("b", fields[1], "Unexpected hash mode");
                digest.update(bytes);
            }
            assertEquals(fields[2], HexFormat.of().formatHex(digest.digest()), file.toString());
        }
    }

    private static MacroModelManager ramseyManager(Path bundle) {
        MacroModelManager manager = new MacroModelManager();
        manager.setMacroBundleDir(bundle.toString());
        manager.setEnabled(true);
        manager.setUseDsge(false);
        manager.setUseRamseyTrend(true);
        return manager;
    }
}
