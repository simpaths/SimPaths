package simpaths.model.person;

import org.junit.jupiter.api.Test;
import simpaths.model.BenefitUnit;
import simpaths.model.Person;
import simpaths.model.annotations.UpdateManager;
import simpaths.model.enums.Gender;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LaggedHomeOwnershipTest {

    private static Person personWithLag(Boolean lag) throws Exception {
        Person person = new Person(true);
        Field field = Person.class.getDeclaredField("demPrptyFlagL1");
        field.setAccessible(true);
        field.set(person, lag);
        return person;
    }

    @Test
    void missingHistoryDefaultsToNonOwnerRatherThanThrowing() throws Exception {
        Person person = personWithLag(null);
        assertEquals(0.0, person.getDoubleValue(Person.Variables.Dhh_owned_L1));
        assertEquals(0.0, person.getDoubleValue(Person.Variables.wealthPrptyFlagL1));
    }

    @Test
    void ownerHistoryIsReportedAsOne() throws Exception {
        Person person = personWithLag(true);
        assertEquals(1.0, person.getDoubleValue(Person.Variables.Dhh_owned_L1));
        assertEquals(1.0, person.getDoubleValue(Person.Variables.wealthPrptyFlagL1));
    }

    @Test
    void nonOwnerHistoryIsReportedAsZero() throws Exception {
        Person person = personWithLag(false);
        assertEquals(0.0, person.getDoubleValue(Person.Variables.Dhh_owned_L1));
        assertEquals(0.0, person.getDoubleValue(Person.Variables.wealthPrptyFlagL1));
    }

    @Test
    void capturedOwnershipLagSurvivesBenefitUnitResetInRealUpdateOrder() throws Exception {
        // Real objects, real annotation processing: BenefitUnit.Update runs before Person.Update and resets
        // wealthPrptyFlag, so the person must copy last year's ownership from the unit's stored lag.
        BenefitUnit unit = new BenefitUnit(1L, 1L);
        unit.setWealthPrptyFlag(true);

        Person person = new Person(1001L);
        person.setDemMaleFlag(Gender.Female);
        person.setDemAge(40);
        person.setWgt(1.0);
        Field bu = Person.class.getDeclaredField("benefitUnit");
        bu.setAccessible(true);
        bu.set(person, unit);
        unit.getMembers().add(person);

        UpdateManager.applyAnnotations(unit);    // BenefitUnit.Update
        assertEquals(Boolean.TRUE, unit.getWealthPrptyFlagL1());

        // Person.Update: copy the lag the way @Lag does
        Method source = Person.class.getDeclaredMethod("getDemPrptyFlagForLag");
        source.setAccessible(true);
        Field lag = Person.class.getDeclaredField("demPrptyFlagL1");
        lag.setAccessible(true);
        lag.set(person, source.invoke(person));

        assertEquals(Boolean.TRUE, lag.get(person));
        assertEquals(1.0, person.getDoubleValue(Person.Variables.Dhh_owned_L1));

        // capturing the lag must not write "not owned" back into the unit's (reset) current flag
        Field current = BenefitUnit.class.getDeclaredField("wealthPrptyFlag");
        current.setAccessible(true);
        assertNull(current.get(unit));
    }
}
