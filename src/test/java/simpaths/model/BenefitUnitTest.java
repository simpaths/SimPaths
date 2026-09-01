package simpaths.model;

import jdk.jfr.Description;
import org.junit.jupiter.api.*;
import simpaths.data.Parameters;
import simpaths.model.enums.Gender;
import simpaths.model.enums.Labour;
import simpaths.model.enums.Occupancy;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BenefitUnitTest {


    private BenefitUnit bu;
    private Person p1;
    private Person p2;



    private void setBenefitUnit(Person person, BenefitUnit benefitUnit) {
        try {
            var f = Person.class.getDeclaredField("benefitUnit");
            f.setAccessible(true);
            f.set(person, benefitUnit);
        } catch (Exception e) {
            throw new RuntimeException("Failed to associate Person to BenefitUnit in test", e);
        }
    }

    private void addMember(BenefitUnit benefitUnit, Person person) {
        try {
            var f = BenefitUnit.class.getDeclaredField("members");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<Person> members = (Set<Person>) f.get(benefitUnit);
            if (members == null) {
                members = new LinkedHashSet<>();
                f.set(benefitUnit, members);
            }
            members.add(person);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add member to BenefitUnit in test", e);
        }
    }


    @Nested
    @DisplayName("Test Enum return values")
    class DoubleValueTests {

        @BeforeEach
        public void setup() {

            Parameters.calculatePartnershipDifferentials("UK");
            bu = new BenefitUnit(1L, 1234L);

            p1 = new Person(100L, 2000L);
            p2 = new Person(101L, 2001L);

            // Make them adults so they’re valid members for propagation checks
            p1.setDemAge(30);
            p2.setDemAge(28);

            p1.setDemMaleFlag(Gender.Male);
            p2.setDemMaleFlag(Gender.Female);


        }

        @AfterEach
        public void tearDown() {

            bu = null;
            p1 = null;
            p2 = null;

        }

        /**
         * Tests single male returns correct employment years
         */
        @Test
        @DisplayName("Single Male returns correct values for years in employment")
        public void testSingleMalesLiwwh() {

            bu.setI_demOccupancy(Occupancy.Single_Male);
            setBenefitUnit(p1, bu);
            addMember(bu, p1);

            bu.setUC_takeup(1);
            p1.setLiwwh(2);
            p1.setLabourSupplyWeekly(Labour.THIRTY_EIGHT);

            assertEquals(2, bu.getDoubleValue(BenefitUnit.Regressors.Liwwh_1), "Should return Liwwh = 2");
            assertEquals(0, bu.getDoubleValue(BenefitUnit.Regressors.Liwwh_021), "Should return zero as male");
            assertEquals(0, bu.getDoubleValue(BenefitUnit.Regressors.Liwwh_200), "Should return zero as uc on");
            assertEquals(0, bu.getDoubleValue(BenefitUnit.Regressors.Liwwh_301), "Should return zero as work hours FORTY");
            assertEquals(2, bu.getDoubleValue(BenefitUnit.Regressors.Liwwh_401), "Should return Liwwh = 2");

        }

        /**
         * Tests single female returns correct employment values
         */
        @Test
        @DisplayName("Single Female returns correct values for years in employment")
        public void testSingleFemalesLiwwh() {

            bu.setI_demOccupancy(Occupancy.Single_Female);
            setBenefitUnit(p2, bu);
            addMember(bu, p2);

            bu.setUC_takeup(0);
            p2.setLiwwh(4);
            p2.setLabourSupplyWeekly(Labour.TWENTY);

            assertEquals(0, bu.getDoubleValue(BenefitUnit.Regressors.Liwwh_1), "Should return zero as uc off");
            assertEquals(0, bu.getDoubleValue(BenefitUnit.Regressors.Liwwh_200), "Should return zero as female");
            assertEquals(0, bu.getDoubleValue(BenefitUnit.Regressors.Liwwh_021), "Should return zero as uc off");
            assertEquals(0, bu.getDoubleValue(BenefitUnit.Regressors.Liwwh_030), "Should return zero as work hours TWENTY");
            assertEquals(4, bu.getDoubleValue(BenefitUnit.Regressors.Liwwh_020), "Should return Liwwh = 4");

        }

    }
}