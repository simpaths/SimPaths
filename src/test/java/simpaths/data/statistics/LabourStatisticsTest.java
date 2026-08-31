package simpaths.data.statistics;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import simpaths.model.Person;
import simpaths.model.enums.Les_c4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Calculating labour statistics")
class LabourStatisticsTest {

    private static List<Person> testPopulation;

    private static Person createTestPerson(
            Les_c4 les_c4_lag1,
            Les_c4 les_c4
    ) {
        Person testPerson = new Person(true);
        testPerson.setDemAge(30);
        testPerson.setLabC4L1(les_c4_lag1);
        testPerson.setLabC4(les_c4);

        return testPerson;
    }

    @BeforeAll
    public static void setupTestPopulation() {

        testPopulation = Arrays.asList(
                // 25% move from employment into unemployment
                createTestPerson(Les_c4.EmployedOrSelfEmployed, Les_c4.NotEmployed),
                createTestPerson(Les_c4.EmployedOrSelfEmployed, Les_c4.Student),
                createTestPerson(Les_c4.EmployedOrSelfEmployed, Les_c4.EmployedOrSelfEmployed),
                createTestPerson(Les_c4.EmployedOrSelfEmployed, Les_c4.EmployedOrSelfEmployed),
                createTestPerson(Les_c4.EmployedOrSelfEmployed, Les_c4.NotEmployed),
                createTestPerson(Les_c4.EmployedOrSelfEmployed, Les_c4.EmployedOrSelfEmployed),
                createTestPerson(Les_c4.EmployedOrSelfEmployed, Les_c4.EmployedOrSelfEmployed),
                createTestPerson(Les_c4.EmployedOrSelfEmployed, Les_c4.Retired),
                // 50% from unemployment into employment
                createTestPerson(Les_c4.NotEmployed, Les_c4.EmployedOrSelfEmployed),
                createTestPerson(Les_c4.NotEmployed, Les_c4.EmployedOrSelfEmployed),
                createTestPerson(Les_c4.NotEmployed, Les_c4.NotEmployed),
                createTestPerson(Les_c4.NotEmployed, Les_c4.Retired),
                // Ignore all rest as should be filtered out
                createTestPerson(Les_c4.Student, Les_c4.Student),
                createTestPerson(Les_c4.Retired, Les_c4.EmployedOrSelfEmployed),
                createTestPerson(Les_c4.Retired, Les_c4.NotEmployed),
                createTestPerson(Les_c4.Student, Les_c4.EmployedOrSelfEmployed)
        );
    }

    @Test
    @DisplayName("Proportion becoming unemployed")
    public void proportionEmpToNotEmp() {
        var stats = new LabourStatistics();
        var agg = AgeBandAggregates.computeWithSupplier(ArrayList::new);
        stats.updateWithSupplier(() -> testPopulation, agg);
        assertEquals(0.25, stats.getEmpToNotEmp());
    }

    @Test
    @DisplayName("Proportion becoming employed")
    public void proportionNotEmpToEmp() {
        var stats = new LabourStatistics();
        var agg = AgeBandAggregates.computeWithSupplier(ArrayList::new);
        stats.updateWithSupplier(() -> testPopulation, agg);
        assertEquals(0.5, stats.getNotEmpToEmp());
    }
}
