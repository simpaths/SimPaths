package simpaths.model.person;

import org.junit.jupiter.api.Test;
import simpaths.data.Parameters;
import simpaths.model.BenefitUnit;
import simpaths.model.Household;
import simpaths.model.Person;
import simpaths.model.SimPathsModel;
import simpaths.model.enums.Dhhtp_c4;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LaggedChildrenAndHouseholdTypeTest {

    private static BenefitUnit unit(int childrenAll, int children02, Dhhtp_c4 compHhC4,
                                     Integer childrenAllLag, Integer children02Lag, Dhhtp_c4 compHhC4Lag) {
        BenefitUnit unit = mock(BenefitUnit.class);
        when(unit.getHousehold()).thenReturn(mock(Household.class));
        when(unit.getMembers()).thenReturn(new HashSet<>());
        when(unit.getNumberChildrenAll()).thenReturn(childrenAll);
        when(unit.getNumberChildren0to2()).thenReturn(children02);
        when(unit.getDemCompHhC4()).thenReturn(compHhC4);
        when(unit.getNumberChildrenAllL1()).thenReturn(childrenAllLag);
        when(unit.getNumberChildren02L1()).thenReturn(children02Lag);
        when(unit.getDemCompHhC4L1()).thenReturn(compHhC4Lag);
        return unit;
    }

    private static Person person(BenefitUnit unit, int age) throws Exception {
        Person person = mock(Person.class, CALLS_REAL_METHODS);
        Field model = Person.class.getDeclaredField("model");
        model.setAccessible(true);
        model.set(person, mock(SimPathsModel.class));
        person.setBenefitUnit(unit);
        person.setDemAge(age);
        return person;
    }

    private static int numberChildrenAllL1(Person person) throws Exception {
        Method getter = Person.class.getDeclaredMethod("getNumberChildrenAllL1");
        getter.setAccessible(true);
        return (int) (Integer) getter.invoke(person);
    }

    private static int numberChildren02L1(Person person) throws Exception {
        Method getter = Person.class.getDeclaredMethod("getNumberChildren02L1");
        getter.setAccessible(true);
        return (int) (Integer) getter.invoke(person);
    }

    private static Dhhtp_c4 demCompHhC4L1(Person person) throws Exception {
        Method getter = Person.class.getDeclaredMethod("getDemCompHhC4L1");
        getter.setAccessible(true);
        return (Dhhtp_c4) getter.invoke(person);
    }

    private static void captureLag(Person person) throws Exception {
        setLagField(person, "numberChildrenAllL1", "getNumberChildrenAllForLag");
        setLagField(person, "numberChildren02L1", "getNumberChildren02ForLag");
        setLagField(person, "demCompHhC4L1", "getDemCompHhC4ForLag");
    }

    private static void setLagField(Person person, String fieldName, String sourceMethodName) throws Exception {
        Method source = Person.class.getDeclaredMethod(sourceMethodName);
        source.setAccessible(true);
        Field lag = Person.class.getDeclaredField(fieldName);
        lag.setAccessible(true);
        lag.set(person, source.invoke(person));
    }

    @Test
    void lagFollowsPersonAcrossBenefitUnitChange() throws Exception {
        Person person = person(unit(2, 1, Dhhtp_c4.CoupleChildren, null, null, null), 30);
        captureLag(person);

        person.setBenefitUnit(unit(0, 0, Dhhtp_c4.SingleNoChildren, null, null, null));
        assertEquals(2, numberChildrenAllL1(person));
        assertEquals(1, numberChildren02L1(person));
        assertEquals(Dhhtp_c4.CoupleChildren, demCompHhC4L1(person));
    }

    @Test
    void underEighteenCapturesIndependentBaselineNotParentsUnit() throws Exception {
        Person person = person(unit(3, 1, Dhhtp_c4.CoupleChildren, null, null, null), Parameters.AGE_TO_BECOME_RESPONSIBLE - 1);
        captureLag(person);

        assertEquals(0, numberChildrenAllL1(person));
        assertEquals(0, numberChildren02L1(person));
        assertEquals(Dhhtp_c4.SingleNoChildren, demCompHhC4L1(person));
    }

    @Test
    void missingHistoryFallsBackToUnitLagThenDefault() throws Exception {
        Person withUnitLag = person(unit(0, 0, null, 2, 1, Dhhtp_c4.CoupleChildren), Parameters.AGE_TO_BECOME_RESPONSIBLE);
        assertEquals(2, numberChildrenAllL1(withUnitLag));
        assertEquals(1, numberChildren02L1(withUnitLag));
        assertEquals(Dhhtp_c4.CoupleChildren, demCompHhC4L1(withUnitLag));

        Person withoutUnitLag = person(unit(0, 0, null, null, null, null), Parameters.AGE_TO_BECOME_RESPONSIBLE);
        assertEquals(0, numberChildrenAllL1(withoutUnitLag));
        assertEquals(0, numberChildren02L1(withoutUnitLag));
        assertEquals(Dhhtp_c4.SingleNoChildren, demCompHhC4L1(withoutUnitLag));
    }

    @Test
    void missingHistoryUnderEighteenIgnoresUnitLag() throws Exception {
        Person person = person(unit(0, 0, null, 3, 2, Dhhtp_c4.CoupleChildren), Parameters.AGE_TO_BECOME_RESPONSIBLE - 1);

        assertEquals(0, numberChildrenAllL1(person));
        assertEquals(0, numberChildren02L1(person));
        assertEquals(Dhhtp_c4.SingleNoChildren, demCompHhC4L1(person));
    }
}
