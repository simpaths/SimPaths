package simpaths.model.person;

import jakarta.persistence.Column;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.Test;
import simpaths.model.BenefitUnit;
import simpaths.model.Household;
import simpaths.model.Person;
import simpaths.model.SimPathsModel;
import simpaths.model.annotations.UpdateManager;
import simpaths.model.enums.Labour;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BenefitReceiptInitialisationTest {
    @Test
    void inputFlagIsMappedAndCopiedToLagBeforeReset() throws Exception {
        Field field = Person.class.getDeclaredField("yBenReceivedFlag");
        Column column = field.getAnnotation(Column.class);
        assertNotNull(column);
        assertEquals("yBenReceivedFlag", column.name());
        assertNull(field.getAnnotation(Transient.class));

        for (boolean initial : new boolean[]{true, false}) {
            Person person = new Person(1L);
            BenefitUnit unit = mock(BenefitUnit.class);
            Household household = mock(Household.class);
            when(unit.getHousehold()).thenReturn(household);
            when(household.getBenefitUnits()).thenReturn(Set.of(unit));
            set(person, "benefitUnit", unit);
            set(person, "model", mock(SimPathsModel.class));
            set(person, "labHrsWorkEnumWeek", Labour.ZERO);
            person.setDemAge(40);
            person.setReceivesBenefitsFlag(initial);
            UpdateManager.applyAnnotations(person);
            assertEquals(initial, person.isReceivesBenefitsFlag_L1());
            field.setAccessible(true);
            assertNull(field.get(person));
            person.setReceivesBenefitsFlag(!initial);
            UpdateManager.applyAnnotations(person);
            assertEquals(!initial, person.isReceivesBenefitsFlag_L1());
        }
    }

    private static void set(Person person, String name, Object value) throws Exception {
        Field field = Person.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(person, value);
    }
}