package simpaths.model;

import microsim.engine.SimulationEngine;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import simpaths.data.Parameters;
import simpaths.model.annotations.UpdateManager;
import simpaths.model.enums.Gender;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class NewbornHealthTest {
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Person donor(int age, Gender gender, Double mcs, Double pcs) throws Exception {
        Person person = new Person(true);
        set(person, "demAge", age);
        set(person, "demMaleFlag", gender);
        set(person, "healthMentalMcs", mcs);
        set(person, "healthPhysicalPcs", pcs);
        return person;
    }

    @Test
    void newbornReceivesStartingPopulationPair() throws Exception {
        SimPathsModel model = mock(SimPathsModel.class);
        SimulationEngine engine = mock(SimulationEngine.class);
        try (MockedStatic<SimulationEngine> engines = mockStatic(SimulationEngine.class);
             MockedStatic<Parameters> parameters = mockStatic(Parameters.class, CALLS_REAL_METHODS)) {
            engines.when(SimulationEngine::getInstance).thenReturn(engine);
            engines.when(SimulationEngine::getRnd).thenReturn(new Random(5));
            when(engine.getManager(SimPathsModel.class.getCanonicalName())).thenReturn(model);
            parameters.when(() -> Parameters.getWageAndAgeDifferentialMultivariateNormalDistribution(anyLong()))
                    .thenReturn(new double[]{0, 0});
            when(model.getNewbornHealthDonors()).thenReturn(new NewbornHealthDonors(
                    List.of(donor(15, Gender.Male, 35.0, 75.0))));
            Person mother = mock(Person.class);
            BenefitUnit unit = mock(BenefitUnit.class);
            Household household = mock(Household.class);
            set(mother, "benefitUnit", unit);
            when(mother.getBenefitUnit()).thenReturn(unit);
            when(unit.getHousehold()).thenReturn(household);
            when(unit.getId()).thenReturn(1L);
            when(household.getId()).thenReturn(2L);
            Person child = new Person(Gender.Male, mother);
            assertEquals(35.0, child.getHealthMentalMcs());
            assertEquals(75.0, child.getHealthPhysicalPcs());
            for (int age = 1; age <= 16; age++) {
                set(child, "demAge", age - 1);
                UpdateManager.applyAnnotations(child);
                set(child, "demAge", age);
                if (age < 16) {
                    child.healthMCS1();
                    child.healthPCS1();
                    child.healthMCS2();
                    child.healthPCS2();
                }
            }
            assertEquals(35.0, child.getHealthMentalMcsL1());
            assertEquals(75.0, child.getHealthPhysicalPcsL1());
        }
    }

    @Test
    void poolsAreSexSpecificValidImmutableAndSeeded() throws Exception {
        List<Person> population = new ArrayList<>(List.of(
                donor(0, Gender.Male, 10.0, 90.0), donor(15, Gender.Male, 20.0, 80.0),
                donor(4, Gender.Female, 30.0, 70.0), donor(16, Gender.Male, 40.0, 60.0),
                donor(4, Gender.Male, null, 50.0), donor(4, Gender.Male, 50.0, Double.NaN),
                donor(4, Gender.Male, -9.0, 50.0), donor(4, Gender.Male, 50.0, 101.0)));
        NewbornHealthDonors pool = new NewbornHealthDonors(population);
        population.add(donor(0, Gender.Male, 99.0, 99.0));
        set(population.get(0), "healthMentalMcs", 99.0);
        Random first = new Random(123), second = new Random(123);
        for (int i = 0; i < 100; i++) {
            NewbornHealthDonors.Scores sample = pool.sample(Gender.Male, first);
            assertEquals(sample, pool.sample(Gender.Male, second));
            assertTrue(sample.equals(new NewbornHealthDonors.Scores(10, 90))
                    || sample.equals(new NewbornHealthDonors.Scores(20, 80)));
            assertEquals(new NewbornHealthDonors.Scores(30, 70), pool.sample(Gender.Female, first));
            pool.sample(Gender.Female, second);
        }
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new NewbornHealthDonors(List.of()).sample(Gender.Female, new Random(1)));
        assertTrue(error.getMessage().contains("Female"));
        NewbornHealthDonors maleOnly = new NewbornHealthDonors(List.of(donor(1, Gender.Male, 1.0, 2.0)));
        assertThrows(IllegalStateException.class, () -> maleOnly.sample(Gender.Female, new Random(1)));
        assertEquals(new NewbornHealthDonors.Scores(1, 2), maleOnly.sample(Gender.Male, new Random(1)));
    }
}