package simpaths.model.person;

import microsim.statistics.regression.BinomialRegression;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import simpaths.data.Parameters;
import simpaths.model.BenefitUnit;
import simpaths.model.Innovations;
import simpaths.model.Person;
import simpaths.model.enums.Indicator;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialDistressTest {

    private static void setField(Person person, String name, Object value) throws Exception {
        Field field = Person.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(person, value);
    }

    private static Person person(int age, boolean distress, Innovations innovations) throws Exception {
        Person person = mock(Person.class, CALLS_REAL_METHODS);
        person.setDemAge(age);
        setField(person, "yFinDstrssFlag", distress);
        setField(person, "statInnovations", innovations);
        return person;
    }

    @SuppressWarnings("unchecked")
    private static void checkAge(int age, boolean scored) throws Exception {
        Innovations innovations = mock(Innovations.class);
        when(innovations.getDoubleDraw(32)).thenReturn(0.25);
        Person person = person(age, false, innovations);
        BinomialRegression<Indicator> regression = mock(BinomialRegression.class);
        when(regression.getProbability(person, Person.Variables.class)).thenReturn(0.75);
        try (MockedStatic<Parameters> parameters = mockStatic(Parameters.class)) {
            parameters.when(Parameters::getRegFinancialDistress).thenReturn(regression);
            person.onEvent(Person.Processes.FinancialDistress);
            assertEquals(scored, person.getYFinDstrssFlag(), "age " + age);
            verify(regression, times(scored ? 1 : 0)).getProbability(person, Person.Variables.class);
            verify(innovations, times(scored ? 1 : 0)).getDoubleDraw(32);
        }
    }

    @Test void age15IsNotScored() throws Exception { checkAge(15, false); }
    @Test void age16IsScored() throws Exception { checkAge(16, true); }
    @Test void age17IsScored() throws Exception { checkAge(17, true); }
    @Test void age18IsScored() throws Exception { checkAge(18, true); }

    @Test
    @SuppressWarnings("unchecked")
    void distressAt17IsReadByScoringAt18() throws Exception {
        Innovations innovations = mock(Innovations.class);
        when(innovations.getDoubleDraw(32)).thenReturn(0.25);
        Person person = person(17, false, innovations);
        List<Double> observedLags = new ArrayList<>();
        BinomialRegression<Indicator> regression = mock(BinomialRegression.class);
        when(regression.getProbability(person, Person.Variables.class)).thenAnswer(invocation -> {
            observedLags.add(person.getDoubleValue(Person.Variables.L_FinancialDistress));
            return 0.75;
        });
        try (MockedStatic<Parameters> parameters = mockStatic(Parameters.class)) {
            parameters.when(Parameters::getRegFinancialDistress).thenReturn(regression);
            person.onEvent(Person.Processes.FinancialDistress);
            person.setDemAge(18);
            person.onEvent(Person.Processes.FinancialDistress);
            assertEquals(List.of(0.0, 1.0), observedLags);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void followingYearScoringReadsPreviousDistressBeforeOverwritingIt() throws Exception {
        Innovations innovations = mock(Innovations.class);
        when(innovations.getDoubleDraw(32)).thenReturn(0.5);
        Person person = person(18, true, innovations); // distressed at 17
        List<Double> observedLags = new ArrayList<>();
        BinomialRegression<Indicator> regression = mock(BinomialRegression.class);
        when(regression.getProbability(person, Person.Variables.class)).thenAnswer(invocation -> {
            observedLags.add(person.getDoubleValue(Person.Variables.L_FinancialDistress));
            return 0.0;
        });
        try (MockedStatic<Parameters> parameters = mockStatic(Parameters.class)) {
            parameters.when(Parameters::getRegFinancialDistress).thenReturn(regression);
            person.onEvent(Person.Processes.FinancialDistress);
            assertEquals(List.of(1.0), observedLags);
            assertFalse(person.getYFinDstrssFlag());
            person.setDemAge(19);
            person.onEvent(Person.Processes.FinancialDistress);
            assertEquals(List.of(1.0, 0.0), observedLags);
        }
    }

    @Test
    void youthfulNullLabourInputsAreSafeForFinDis() throws Exception {
        Person person = person(16, false, mock(Innovations.class));
        assertAll(
                () -> assertEquals(0.0, person.getDoubleValue(Person.Variables.EmployedToUnemployed)),
                () -> assertEquals(0.0, person.getDoubleValue(Person.Variables.UnemployedToEmployed)),
                () -> assertEquals(0.0, person.getDoubleValue(Person.Variables.PersistentUnemployed)),
                () -> assertEquals(0.0, person.getDoubleValue(Person.Variables.Lhw_10)),
                () -> assertEquals(0.0, person.getDoubleValue(Person.Variables.Lhw_20)),
                () -> assertEquals(0.0, person.getDoubleValue(Person.Variables.Lhw_30)),
                () -> assertEquals(0.0, person.getDoubleValue(Person.Variables.Lhw_40))
        );
    }

    @Test
    void benefitRegressorUsesPreviousYearFlagAndPersonalIncomeIsSafe() throws Exception {
        Person person = person(16, false, mock(Innovations.class));
        setField(person, "yBenReceivedFlagL1", true);
        assertEquals(1.0, person.getDoubleValue(Person.Variables.D_Econ_benefits));
        setField(person, "yBenReceivedFlagL1", false);
        assertEquals(0.0, person.getDoubleValue(Person.Variables.D_Econ_benefits));
        assertEquals(0.0, person.getDoubleValue(Person.Variables.L_Ypncp));
        assertEquals(0.0, person.getDoubleValue(Person.Variables.L_Ypnoab));
    }
}
