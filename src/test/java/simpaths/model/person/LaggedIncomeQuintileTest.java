package simpaths.model.person;

import org.junit.jupiter.api.Test;
import simpaths.model.BenefitUnit;
import simpaths.model.Household;
import simpaths.model.Person;
import simpaths.model.SimPathsModel;
import simpaths.model.enums.Ydses_c5;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LaggedIncomeQuintileTest {

    private static BenefitUnit unit(Ydses_c5 current, Ydses_c5 lag) {
        BenefitUnit unit = mock(BenefitUnit.class);
        when(unit.getHousehold()).thenReturn(mock(Household.class));
        when(unit.getMembers()).thenReturn(new HashSet<>());
        when(unit.getYHhQuintilesMonthC5()).thenReturn(current);
        when(unit.getYHhQuintilesMonthC5L1()).thenReturn(lag);
        return unit;
    }

    private static Person person(BenefitUnit unit) throws Exception {
        Person person = mock(Person.class, CALLS_REAL_METHODS);
        Field model = Person.class.getDeclaredField("model");
        model.setAccessible(true);
        model.set(person, mock(SimPathsModel.class));
        person.setBenefitUnit(unit);
        return person;
    }

    private static Ydses_c5 lagged(Person person) throws Exception {
        Method getter = Person.class.getDeclaredMethod("getYHhQuintilesMonthC5L1");
        getter.setAccessible(true);
        return (Ydses_c5) getter.invoke(person);
    }

    private static void captureLag(Person person) throws Exception {
        Method source = Person.class.getDeclaredMethod("getYHhQuintilesMonthC5ForLag");
        source.setAccessible(true);
        Field lag = Person.class.getDeclaredField("yHhQuintilesMonthC5L1");
        lag.setAccessible(true);
        lag.set(person, source.invoke(person));
    }

    @Test
    void lagFollowsPersonAcrossBenefitUnitChange() throws Exception {
        Person person = person(unit(Ydses_c5.Q5, Ydses_c5.Q2));
        captureLag(person);

        person.setBenefitUnit(unit(Ydses_c5.Q3, Ydses_c5.Q2));
        assertEquals(Ydses_c5.Q5, lagged(person));
    }

    @Test
    void missingHistoryFallsBackToUnitLagThenQ3() throws Exception {
        assertEquals(Ydses_c5.Q4, lagged(person(unit(null, Ydses_c5.Q4))));
        assertEquals(Ydses_c5.Q3, lagged(person(unit(null, null))));
    }
}
