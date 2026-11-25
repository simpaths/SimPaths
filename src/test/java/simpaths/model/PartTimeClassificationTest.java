package simpaths.model;

import org.junit.jupiter.api.Test;
import simpaths.model.enums.Gender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartTimeClassificationTest {

    @Test
    void exactlyTwentyFiveHoursIsPartTimeForChildcareAndCounts() {
        BenefitUnit unit = coupleWithHours(25, 25);

        assertEquals(1.0, unit.getDoubleValue(BenefitUnit.Variables.couple_emp_2pt));
        assertEquals(0.0, unit.getDoubleValue(BenefitUnit.Variables.couple_emp_2ft));
        assertEquals(0.0, unit.getDoubleValue(BenefitUnit.Variables.NumberFullTimeWork));
        assertEquals(2.0, unit.getDoubleValue(BenefitUnit.Variables.NumberPartTimeWork));
    }

    @Test
    void moreThanTwentyFiveHoursIsFullTimeForChildcareAndCounts() {
        BenefitUnit unit = coupleWithHours(26, 26);

        assertEquals(1.0, unit.getDoubleValue(BenefitUnit.Variables.couple_emp_2ft));
        assertEquals(0.0, unit.getDoubleValue(BenefitUnit.Variables.couple_emp_2pt));
        assertEquals(2.0, unit.getDoubleValue(BenefitUnit.Variables.NumberFullTimeWork));
        assertEquals(0.0, unit.getDoubleValue(BenefitUnit.Variables.NumberPartTimeWork));
    }

    @Test
    void twentyFiveAndTwentySixHoursAreClassifiedAsFullTimeAndPartTime() {
        BenefitUnit unit = coupleWithHours(26, 25);

        assertEquals(1.0, unit.getDoubleValue(BenefitUnit.Variables.couple_emp_ftpt));
        assertEquals(1.0, unit.getDoubleValue(BenefitUnit.Variables.NumberFullTimeWork));
        assertEquals(1.0, unit.getDoubleValue(BenefitUnit.Variables.NumberPartTimeWork));
    }

    private static BenefitUnit coupleWithHours(int maleHours, int femaleHours) {
        BenefitUnit unit = new BenefitUnit(true);
        Person male = person(Gender.Male, maleHours);
        Person female = person(Gender.Female, femaleHours);
        unit.getMembers().add(male);
        unit.getMembers().add(female);
        unit.setI_labHrsWork1Week(maleHours);
        unit.setI_labHrsWork2Week(femaleHours);
        return unit;
    }

    private static Person person(Gender gender, int hours) {
        Person person = mock(Person.class);
        when(person.getDemAge()).thenReturn(30);
        when(person.getDemMaleFlag()).thenReturn(gender);
        when(person.getLabourSupplyHoursWeekly()).thenReturn(hours);
        return person;
    }
}
