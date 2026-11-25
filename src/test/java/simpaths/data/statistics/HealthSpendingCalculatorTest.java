package simpaths.data.statistics;

import org.junit.jupiter.api.Test;
import simpaths.data.HealthCostProfile;
import simpaths.data.Parameters;
import simpaths.model.Person;
import simpaths.model.SimPathsModel;
import simpaths.model.enums.Gender;
import simpaths.model.enums.SampleExit;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthSpendingCalculatorTest {

    @Test
    void calculatesBenchmarkAndFixed2028AgeSharesAtSingleAges() throws IOException {
        HealthCostProfile profile = new HealthCostProfile();
        HealthSpendingCalculator calculator = new HealthSpendingCalculator(profile);
        var base = HealthSpendingCalculator.snapshot(2028,
                List.of(person(0, 2.0), person(101, 1.0), person(102, 1.0)), true, 1.0);
        var baseResult = calculator.calculate(base);
        double baseExpected = 2.0 * profile.cost(2028, 0) + 2.0 * profile.cost(2028, 101);

        assertEquals(baseExpected, baseResult.getBenchmarkExpenditure(), 1e-8);
        assertEquals(baseExpected, baseResult.getFixedAgeExpenditure().orElseThrow(), 1e-8);
        assertEquals(0.0, baseResult.getAgeingEffect().orElseThrow(), 1e-8);
        assertEquals(profile.cost(2028, 101), baseResult.getSpendingByAge().get(102), 1e-8);
        assertEquals(baseResult.getBenchmarkExpenditure(),
                baseResult.getSpendingByAge().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-8);
        assertThrows(UnsupportedOperationException.class,
                () -> baseResult.getSpendingByAge().put(40, 1.0));

        var future = HealthSpendingCalculator.snapshot(2029,
                List.of(person(0, 2.0), person(101, 4.0), person(102, 2.0)), true, 1.0);
        var futureResult = calculator.calculate(future);
        double youngCost = profile.cost(2029, 0);
        double oldCost = profile.cost(2029, 101);
        assertEquals(8.0, futureResult.getTotalPopulation());
        assertEquals(2.0 * youngCost + 6.0 * oldCost,
                futureResult.getBenchmarkExpenditure(), 1e-8);
        assertEquals(4.0 * youngCost + 4.0 * oldCost,
                futureResult.getFixedAgeExpenditure().orElseThrow(), 1e-8);
        assertEquals(2.0 * (oldCost - youngCost),
                futureResult.getAgeingEffect().orElseThrow(), 1e-8);
        assertEquals(futureResult.getBenchmarkExpenditure(),
                futureResult.getSpendingByAge().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-8);
        assertTrue(futureResult.getSpendingByAge().containsKey(102));
    }

    @Test
    void leavesCounterfactualUnavailableWhenRunHasNo2028Snapshot() throws IOException {
        HealthCostProfile profile = new HealthCostProfile();
        HealthSpendingCalculator calculator = new HealthSpendingCalculator(profile);
        var later = HealthSpendingCalculator.snapshot(2029,
                List.of(person(0, 2.0)), true, 1.0);

        var result = calculator.calculate(later);

        assertEquals(2.0 * profile.cost(2029, 0), result.getBenchmarkExpenditure());
        assertTrue(result.getFixedAgeExpenditure().isEmpty());
        assertTrue(result.getAgeingEffect().isEmpty());
    }

    @Test
    void requiresPositiveBasePopulationAndCapturesItOnlyOnce() throws IOException {
        HealthSpendingCalculator calculator = new HealthSpendingCalculator(new HealthCostProfile());
        var empty = HealthSpendingCalculator.snapshot(2028, List.of(), true, 1.0);
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(empty));

        var base = HealthSpendingCalculator.snapshot(2028, List.of(person(0, 1.0)), true, 1.0);
        calculator.calculate(base);
        assertThrows(IllegalStateException.class, () -> calculator.calculate(base));
    }

    @Test
    void rejectsUnsupportedYearEvenWithEmptyPopulation() throws IOException {
        HealthSpendingCalculator calculator = new HealthSpendingCalculator(new HealthCostProfile());
        var unsupported = HealthSpendingCalculator.snapshot(2061, List.of(), true, 1.0);
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(unsupported));
    }

    @Test
    void unweightedSnapshotKeepsActualAgesAndExcludesExitedPeople() {
        Person exited = person(40, 100.0);
        exited.setSampleExit(SampleExit.Death);

        var snapshot = HealthSpendingCalculator.snapshot(2028,
                List.of(person(0, 1.0), person(101, 2.0), person(102, 1.5), exited),
                false, 10.0);

        assertEquals(2028, snapshot.getYear());
        assertEquals(10.0, snapshot.getPopulationAtAge(0));
        assertEquals(20.0, snapshot.getPopulationAtAge(101));
        assertEquals(15.0, snapshot.getPopulationAtAge(102));
        assertEquals(0.0, snapshot.getPopulationAtAge(40));
        assertFalse(snapshot.getPopulationByAge().containsKey(40));
        assertEquals(45.0, snapshot.getTotalPopulation());
        assertEquals(snapshot.getPopulationByAge().values().stream().mapToDouble(Double::doubleValue).sum(),
                snapshot.getTotalPopulation());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getPopulationByAge().put(40, 100.0));
    }

    @Test
    void weightedSnapshotUsesPersonWeightsWithoutRescaling() {
        var snapshot = HealthSpendingCalculator.snapshot(2030,
                List.of(person(0, 2.0), person(0, 3.0), person(102, 4.0)),
                true, 10.0);

        assertEquals(5.0, snapshot.getPopulationAtAge(0));
        assertEquals(4.0, snapshot.getPopulationAtAge(102));
        assertEquals(9.0, snapshot.getTotalPopulation());
    }

    @Test
    void modelEntryPointUsesModelPopulationAndWeightMode() {
        SimPathsModel model = mock(SimPathsModel.class);
        when(model.getPersons()).thenReturn(Collections.singleton(person(102, 3.0)));
        when(model.isUseWeights()).thenReturn(false);
        when(model.getScalingFactor()).thenReturn(7.0);

        var snapshot = HealthSpendingCalculator.snapshot(2028, model);

        assertEquals(21.0, snapshot.getPopulationAtAge(102));
        assertEquals(21.0, snapshot.getTotalPopulation());
    }

    @Test
    void rejectsInvalidScalingFactorsInBothWeightModes() {
        for (double scalingFactor : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> HealthSpendingCalculator.snapshot(2028, List.of(person(0, 1.0)),
                            false, scalingFactor));
            assertThrows(IllegalArgumentException.class,
                    () -> HealthSpendingCalculator.snapshot(2028, List.of(person(0, 1.0)),
                            true, scalingFactor));
        }
    }

    @Test
    void rejectsInvalidActiveWeightsAndNegativeAges() {
        for (double weight : new double[] {-1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> HealthSpendingCalculator.snapshot(2028, List.of(person(0, weight)),
                            true, 1.0));
        }
        assertThrows(IllegalArgumentException.class,
                () -> HealthSpendingCalculator.snapshot(2028, List.of(person(-1, 1.0)),
                        true, 1.0));
    }

    @Test
    void ignoresExitedPeopleBeforeReadingTheirAgeOrWeight() {
        Person exited = person(-1, Double.NaN);
        exited.setSampleExit(SampleExit.EmigrationAlignment);

        var snapshot = HealthSpendingCalculator.snapshot(2028,
                List.of(person(0, 0.0), exited), true, 1.0);

        assertEquals(0.0, snapshot.getTotalPopulation());
        assertEquals(0.0, snapshot.getPopulationAtAge(0));
    }

    @Test
    void rejectsOverflowInEffectiveWeightAndAgeTotals() {
        assertThrows(IllegalArgumentException.class,
                () -> HealthSpendingCalculator.snapshot(2028,
                        List.of(person(0, Double.MAX_VALUE)), false, 2.0));
        assertThrows(IllegalArgumentException.class,
                () -> HealthSpendingCalculator.snapshot(2028,
                        List.of(person(0, Double.MAX_VALUE), person(0, Double.MAX_VALUE)), true, 1.0));
    }

    @Test
    void pricesEachSexAndActualAgeWithTheActivePopulationWeights() throws IOException {
        var profile = new HealthCostProfile(Parameters.getAgeHealthCostProfileFile("age_by_sex_health_cost_profile.xlsx"));
        Person male = person(32, 2.0);
        male.setDemMaleFlag(Gender.Male);
        Person female = person(32, 3.0);
        female.setDemMaleFlag(Gender.Female);
        Person older = person(103, 1.0);
        older.setDemMaleFlag(Gender.Male);
        Person exited = person(32, 500.0);
        exited.setSampleExit(SampleExit.Death);
        var snapshot = HealthSpendingCalculator.snapshot(2028,
                List.of(male, female, older, exited, person(40, 0.0)), false, 4.0, true);
        var result = new HealthSpendingCalculator(profile).calculate(snapshot);

        double at32 = 8.0 * profile.cost(2028, 32, Gender.Male) + 12.0 * profile.cost(2028, 32, Gender.Female);
        double at103 = 4.0 * profile.cost(2028, 101, Gender.Male);
        assertEquals(24.0, result.getTotalPopulation());
        assertEquals(at32, result.getSpendingByAge().get(32), 1e-8);
        assertEquals(at103, result.getSpendingByAge().get(103), 1e-8);
        assertEquals(at32 + at103, result.getBenchmarkExpenditure(), 1e-8);
        assertTrue(result.getFixedAgeExpenditure().isEmpty());
        assertTrue(result.getAgeingEffect().isEmpty());
        assertFalse(result.getSpendingByAge().containsKey(40));
    }

    private static Person person(int age, double weight) {
        Person person = new Person(true);
        person.setDemAge(age);
        person.setWgt(weight);
        return person;
    }
}
