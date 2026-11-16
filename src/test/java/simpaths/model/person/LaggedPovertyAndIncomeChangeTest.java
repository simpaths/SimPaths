package simpaths.model.person;

import org.junit.jupiter.api.Test;
import simpaths.model.BenefitUnit;
import simpaths.model.Household;
import simpaths.model.Person;
import simpaths.model.SimPathsModel;
import simpaths.model.annotations.UpdateManager;
import simpaths.model.enums.Gender;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LaggedPovertyAndIncomeChangeTest {

    private static BenefitUnit unit(int pvrtyFlag, double equivYear, Integer pvrtyFlagLag, Double equivYearLag) {
        BenefitUnit unit = mock(BenefitUnit.class);
        when(unit.getHousehold()).thenReturn(mock(Household.class));
        when(unit.getMembers()).thenReturn(new HashSet<>());
        when(unit.getYPvrtyFlag()).thenReturn(pvrtyFlag);
        when(unit.getEquivalisedDisposableIncomeYearly()).thenReturn(equivYear);
        when(unit.getYPvrtyFlagL1()).thenReturn(pvrtyFlagLag);
        when(unit.getYDispEquivYearL1()).thenReturn(equivYearLag);
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

    private static void captureLag(Person person) throws Exception {
        setLagField(person, "yPvrtyFlagL1", "getYPvrtyFlagForLag");
        setLagField(person, "yDispEquivYearL1", "getYDispEquivYearForLag");
    }

    private static void setLagField(Person person, String fieldName, String sourceMethodName) throws Exception {
        Method source = Person.class.getDeclaredMethod(sourceMethodName);
        source.setAccessible(true);
        Field lag = Person.class.getDeclaredField(fieldName);
        lag.setAccessible(true);
        lag.set(person, source.invoke(person));
    }

    @Test
    void povertyTransitionFollowsPersonAcrossBenefitUnitChange() throws Exception {
        // captured while in a unit at risk of poverty (flag = 1)
        Person person = person(unit(1, 1000.0, null, null));
        captureLag(person);

        // moves to a new unit not at risk of poverty this year
        person.setBenefitUnit(unit(0, 2000.0, null, null));

        assertEquals(1.0, person.getDoubleValue(Person.Variables.PovertyToNonPoverty));
        assertEquals(0.0, person.getDoubleValue(Person.Variables.NonPovertyToPoverty));
        assertEquals(0.0, person.getDoubleValue(Person.Variables.PersistentPoverty));
    }

    @Test
    void missingPovertyHistoryFallsBackToUnitLagThenNoTransition() throws Exception {
        Person withUnitLag = person(unit(1, 1000.0, 1, null));
        assertEquals(1.0, withUnitLag.getDoubleValue(Person.Variables.PersistentPoverty));

        Person withoutUnitLag = person(unit(1, 1000.0, null, null));
        assertEquals(0.0, withoutUnitLag.getDoubleValue(Person.Variables.NonPovertyToPoverty));
        assertEquals(0.0, withoutUnitLag.getDoubleValue(Person.Variables.PovertyToNonPoverty));
        assertEquals(0.0, withoutUnitLag.getDoubleValue(Person.Variables.PersistentPoverty));
    }

    @Test
    void incomeDecreaseFollowsPersonAcrossBenefitUnitChange() throws Exception {
        // captured while in a unit with high income last year; by Person.Update the unit's current value has
        // already been reset (the -9999.99 placeholder), so only its stored lag holds last year's income
        Person person = person(unit(0, -9999.99, null, 5000.0));
        captureLag(person);

        // moves to a new, poorer unit this year
        person.setBenefitUnit(unit(0, 1000.0, null, null));

        assertEquals(1.0, person.getDoubleValue(Person.Variables.RealIncomeDecrease_D));
    }

    @Test
    void missingIncomeHistoryFallsBackToUnitLagThenNoSignal() throws Exception {
        Person withUnitLag = person(unit(0, 1000.0, null, 5000.0));
        assertEquals(1.0, withUnitLag.getDoubleValue(Person.Variables.RealIncomeDecrease_D));

        Person withoutUnitLag = person(unit(0, 1000.0, null, null));
        assertEquals(0.0, withoutUnitLag.getDoubleValue(Person.Variables.RealIncomeDecrease_D));
        assertEquals(0.0, withoutUnitLag.getDoubleValue(Person.Variables.RealIncomeChange));
    }

    @Test
    void capturedIncomeLagSurvivesBenefitUnitResetInRealUpdateOrder() throws Exception {
        // Real objects, real annotation processing: BenefitUnit.Update runs before Person.Update and resets
        // yDispEquivYear, so the person must capture last year's income from the unit's stored lag.
        BenefitUnit unit = new BenefitUnit(1L, 1L);
        Field current = BenefitUnit.class.getDeclaredField("yDispEquivYear");
        current.setAccessible(true);
        current.set(unit, 24000.0);

        Person person = new Person(1001L);
        person.setDemMaleFlag(Gender.Female);
        person.setDemAge(40);
        person.setWgt(1.0);
        Field bu = Person.class.getDeclaredField("benefitUnit");
        bu.setAccessible(true);
        bu.set(person, unit);
        unit.getMembers().add(person);

        UpdateManager.applyAnnotations(unit);    // BenefitUnit.Update
        assertEquals(-9999.99, unit.getEquivalisedDisposableIncomeYearly());
        setLagField(person, "yDispEquivYearL1", "getYDispEquivYearForLag");    // Person.Update

        Field lag = Person.class.getDeclaredField("yDispEquivYearL1");
        lag.setAccessible(true);
        assertEquals(24000.0, lag.get(person));

        // moves to a poorer unit this year: the decrease is detected
        bu.set(person, unit(0, 12000.0, null, null));
        assertEquals(1.0, person.getDoubleValue(Person.Variables.RealIncomeDecrease_D));
    }

    @Test
    void realIncomeChangeIsZeroWhenCurrentValueIsNegativeSentinel() throws Exception {
        Person person = person(unit(0, -9999.99, null, 1000.0));
        assertEquals(0.0, person.getDoubleValue(Person.Variables.RealIncomeChange));
    }
}
