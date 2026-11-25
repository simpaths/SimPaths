package simpaths.experiment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;
import microsim.data.db.PanelEntityKey;
import simpaths.data.HealthCostProfile;
import simpaths.data.statistics.HealthSpendingByAge;
import simpaths.data.statistics.HealthSpendingCalculator;
import simpaths.data.statistics.HealthSpendingStatistics;
import simpaths.model.Person;
import simpaths.model.SimPathsModel;
import simpaths.model.enums.Country;
import simpaths.model.enums.SampleExit;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthSpendingCollectorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void schedulesOnlySupportedYearsAndRejectsRunsPastProfileEnd() {
        assertEquals(-1, SimPathsCollector.firstHealthSpendingYear(2019, 2027));
        assertEquals(2028, SimPathsCollector.firstHealthSpendingYear(2019, 2060));
        assertEquals(2035, SimPathsCollector.firstHealthSpendingYear(2035, 2060));
        assertThrows(IllegalArgumentException.class,
                () -> SimPathsCollector.firstHealthSpendingYear(2019, 2061));
    }

    @Test
    void usesSimulationTimeForTheJustCompletedYear() {
        assertEquals(2028, SimPathsCollector.healthSpendingYear(2028.0));
        assertEquals(2060, SimPathsCollector.healthSpendingYear(2060.0));
        for (double time : new double[] {2027.0, 2061.0, 2028.5, Double.NaN}) {
            assertThrows(IllegalArgumentException.class,
                    () -> SimPathsCollector.healthSpendingYear(time));
        }
    }

    @Test
    void scenarioYamlSetsTheActualModelAndCollectorFields() throws IOException, ReflectiveOperationException {
        Map<String, Object> config;
        try (FileInputStream input = new FileInputStream("config/age_related_health_costs_2060.yml")) {
            config = new Yaml().load(input);
        }

        assertEquals(2019, config.get("startYear"));
        assertEquals(2060, config.get("endYear"));
        SimPathsModel model = new SimPathsModel(Country.UK, 2019);
        assertFalse(model.isAlignFertility());
        SimPathsMultiRun.updateParameters(model, castArgs(config.get("model_args")));
        assertTrue(model.isAlignPopulation());
        assertTrue(model.isAlignCohabitation());
        assertTrue(model.isAlignFertility());
        assertEquals(2060, intField(model, "FERTILITY_ALIGNMENT_END_YEAR"));
        assertEquals(2060, intField(model, "PARTNERSHIP_ALIGNMENT_END_YEAR"));

        SimPathsCollector collector = new SimPathsCollector(model);
        assertFalse(collector.isPersistHealthSpendingStatistics());
        SimPathsMultiRun.updateParameters(collector, castArgs(config.get("collector_args")));
        assertTrue(collector.isPersistHealthSpendingStatistics());
    }

    @Test
    void ageBySexScenariosDifferOnlyInPopulationTargets() throws IOException {
        Map<String, Object> baseline;
        Map<String, Object> noAgeing;
        try (var regular = Files.newInputStream(Path.of("config/age_sex_health_costs_baseline_2060.yml"));
             var fixed = Files.newInputStream(Path.of("config/age_sex_health_costs_no_ageing_2060.yml"))) {
            baseline = new Yaml().load(regular);
            noAgeing = new Yaml().load(fixed);
        }
        assertEquals("align_popProjections.xlsx",
                castArgs(baseline.get("parameter_args")).remove("populationProjectionFilename"));
        assertEquals("align_popProjections_no_ageing_2026.xlsx",
                castArgs(noAgeing.get("parameter_args")).remove("populationProjectionFilename"));
        assertEquals(baseline, noAgeing);

        var collector = new SimPathsCollector(new SimPathsModel(Country.UK, 2021));
        SimPathsMultiRun.updateParameters(collector, castArgs(baseline.get("collector_args")));
        assertTrue(collector.isPersistHealthSpendingStatistics());
        assertEquals("age_by_sex_health_cost_profile.xlsx", collector.getHealthCostProfileFilename());
    }

    @Test
    void annualAndAgeRowsUseOneSnapshotAndReconcileAt2028And2060()
            throws IOException, ReflectiveOperationException {
        HealthCostProfile profile = new HealthCostProfile();
        HealthSpendingCalculator calculator = new HealthSpendingCalculator(profile);
        Person exited = person(50, 100.0);
        exited.setSampleExit(SampleExit.Death);
        var base = populationSnapshot(2028, person(0, 2.0), person(101, 1.0),
                person(102, 1.0), exited);
        var baseResult = calculator.calculate(base);
        SimPathsCollector.validateHealthSpendingDetail(base, baseResult, profile);

        HealthSpendingStatistics annual = new HealthSpendingStatistics();
        annual.update(baseResult);
        assertEquals(4.0, annual.getPopulationExpandedPeople());
        assertEquals(baseResult.getBenchmarkExpenditure(), annual.getBenchmarkAnnualGbp2015());
        assertEquals(annual.getBenchmarkAnnualGbp2015(),
                annual.getFixedAgeStructureAnnualGbp2015(), 1e-8);
        assertEquals(0.0, annual.getAgeingEffectAnnualGbp2015(), 1e-8);

        HealthSpendingByAge ageRow = new HealthSpendingByAge();
        ageRow.update(102, base.getPopulationAtAge(102), profile.cost(2028, 102),
                baseResult.getSpendingByAge().get(102));
        assertEquals(102, ageRow.getAgeYears());
        assertEquals(profile.cost(2028, 101), ageRow.getCostAnnualGbp2015PerPerson());
        assertEquals(baseResult.getSpendingByAge().get(102),
                ageRow.getExpenditureAnnualGbp2015());
        Field idField = HealthSpendingByAge.class.getDeclaredField("key");
        idField.setAccessible(true);
        assertEquals(103L, ((PanelEntityKey) idField.get(ageRow)).getId());

        var last = populationSnapshot(2060, person(0, 1.0), person(101, 2.0),
                person(102, 3.0));
        var lastResult = calculator.calculate(last);
        SimPathsCollector.validateHealthSpendingDetail(last, lastResult, profile);
        assertEquals(6.0, lastResult.getTotalPopulation());
        assertEquals(lastResult.getBenchmarkExpenditure() -
                        lastResult.getFixedAgeExpenditure().orElseThrow(),
                lastResult.getAgeingEffect().orElseThrow(), 1e-8);
    }

    @Test
    void laterStartingRunExportsUnavailableCounterfactualAsNull() throws IOException {
        HealthCostProfile profile = new HealthCostProfile();
        var population = populationSnapshot(2029, person(0, 1.0));
        var result = new HealthSpendingCalculator(profile).calculate(population);
        SimPathsCollector.validateHealthSpendingDetail(population, result, profile);

        HealthSpendingStatistics annual = new HealthSpendingStatistics();
        annual.update(result);
        assertEquals(profile.cost(2029, 0), annual.getBenchmarkAnnualGbp2015());
        assertNull(annual.getFixedAgeStructureAnnualGbp2015());
        assertNull(annual.getAgeingEffectAnnualGbp2015());
    }

    @Test
    void ageRowRejectsInvalidMeasurements() {
        HealthSpendingByAge row = new HealthSpendingByAge();
        assertThrows(IllegalArgumentException.class,
                () -> row.update(-1, 1.0, 100.0, 100.0));
        assertThrows(IllegalArgumentException.class,
                () -> row.update(0, Double.NaN, 100.0, 100.0));
        assertThrows(IllegalArgumentException.class,
                () -> row.update(0, 1.0, 0.0, 0.0));
    }

    @Test
    void outputReadmeDistinguishesHealthSpendingFromHealthStates() throws IOException {
        SimPathsModel model = new SimPathsModel(Country.UK, 2019);
        model.setEndYear(2060);
        SimPathsCollector collector = new SimPathsCollector(model);
        collector.setPersistHealthSpendingStatistics(true);

        OutputReadme.write(collector, model, temporaryDirectory);
        String readme = Files.readString(temporaryDirectory.resolve("README.md"));

        assertTrue(readme.contains("HealthSpendingStatistics.csv"));
        assertTrue(readme.contains("HealthSpendingByAge.csv"));
        assertTrue(readme.contains("HealthStatistics.csv"));
        assertTrue(readme.contains("Ages 102+ remain distinct rows but use the age-101 cost"));
        assertTrue(readme.contains("`2028` labels 2028-29"));
        assertTrue(readme.contains("annual, not equivalised, and in real-2015 pounds"));
    }

    private static HealthSpendingCalculator.PopulationSnapshot populationSnapshot(int year,
                                                                                   Person... people) {
        SimPathsModel model = mock(SimPathsModel.class);
        Set<Person> persons = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(persons, people);
        when(model.getPersons()).thenReturn(persons);
        when(model.isUseWeights()).thenReturn(true);
        when(model.getScalingFactor()).thenReturn(1.0);
        return HealthSpendingCalculator.snapshot(year, model);
    }

    private static Person person(int age, double weight) {
        Person person = new Person(true);
        person.setDemAge(age);
        person.setWgt(weight);
        return person;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castArgs(Object value) {
        return (Map<String, Object>) value;
    }

    private static int intField(SimPathsModel model, String name) throws ReflectiveOperationException {
        Field field = SimPathsModel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(model);
    }
}
