package simpaths.model.person;

import org.junit.jupiter.api.Test;
import simpaths.model.BenefitUnit;
import simpaths.model.Household;
import simpaths.model.Person;
import simpaths.model.SimPathsModel;
import simpaths.model.annotations.UpdateManager;
import simpaths.model.enums.Labour;
import simpaths.model.enums.Les_c4;
import simpaths.model.enums.Lesdf_c4;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartnershipLagTest {
    private static void set(Person person, String name, Object value) throws Exception {
        Field field = Person.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(person, value);
    }

    private static Person person(long id, BenefitUnit unit, Les_c4 status) throws Exception {
        Household household = mock(Household.class);
        when(unit.getHousehold()).thenReturn(household);
        when(household.getBenefitUnits()).thenReturn(Set.of(unit));
        Person person = new Person(id);
        person.setDemAge(40);
        person.setLabC4(status);
        set(person, "benefitUnit", unit);
        set(person, "model", mock(SimPathsModel.class));
        set(person, "labHrsWorkEnumWeek", Labour.values()[0]);
        return person;
    }

    @Test
    void combinedEmploymentLagDoesNotDependOnUpdateOrder() throws Exception {
        for (Les_c4 firstStatus : new Les_c4[]{Les_c4.EmployedOrSelfEmployed, Les_c4.NotEmployed, null}) {
            for (Les_c4 secondStatus : new Les_c4[]{Les_c4.EmployedOrSelfEmployed, Les_c4.NotEmployed, null}) {
                for (boolean reverse : new boolean[]{false, true}) {
                    BenefitUnit unit = mock(BenefitUnit.class);
                    Set<Person> members = new HashSet<>();
                    when(unit.getMembers()).thenReturn(members);
                    when(unit.getCoupleBoolean()).thenReturn(true);
                    Person first = person(1L, unit, firstStatus);
                    Person second = person(2L, unit, secondStatus);
                    members.add(first);
                    members.add(second);
                    Lesdf_c4 firstExpected = first.getLabStatusPartnerAndOwnC4();
                    Lesdf_c4 secondExpected = second.getLabStatusPartnerAndOwnC4();
                    first.onEvent(Person.Processes.SnapshotPartnerEmployment);
                    second.onEvent(Person.Processes.SnapshotPartnerEmployment);
                    UpdateManager.applyAnnotations(reverse ? second : first);
                    UpdateManager.applyAnnotations(reverse ? first : second);
                    assertEquals(firstExpected, first.getLabStatusPartnerAndOwnC4L1());
                    assertEquals(secondExpected, second.getLabStatusPartnerAndOwnC4L1());
                    for (Person member : new Person[]{first, second}) {
                        Lesdf_c4 expected = member == first ? firstExpected : secondExpected;
                        assertEquals(expected == Lesdf_c4.EmployedSpouseNotEmployed ? 1.0 : 0.0,
                                member.getDoubleValue(Person.Variables.labStatusPartnerAndOwnC42L1));
                        assertEquals(expected == Lesdf_c4.NotEmployedSpouseEmployed ? 1.0 : 0.0,
                                member.getDoubleValue(Person.Variables.labStatusPartnerAndOwnC43L1));
                        assertEquals(expected == Lesdf_c4.BothNotEmployed ? 1.0 : 0.0,
                                member.getDoubleValue(Person.Variables.labStatusPartnerAndOwnC44L1));
                    }
                }
            }
        }
    }

    @Test
    void partnershipDurationIsRefreshedEachYear() throws Exception {
        BenefitUnit unit = mock(BenefitUnit.class);
        when(unit.getMembers()).thenReturn(new HashSet<>());
        Person person = person(1L, unit, Les_c4.NotEmployed);
        person.setDemPartnerNYear(5);
        person.setDemPartnerNYearL1(2);
        UpdateManager.applyAnnotations(person);
        assertEquals(5.0, person.getDoubleValue(Person.Variables.demPartnerNYearL1));
        person.setDemPartnerNYear(6);
        person.setLabC4(Les_c4.NotEmployed);
        UpdateManager.applyAnnotations(person);
        assertEquals(6.0, person.getDoubleValue(Person.Variables.demPartnerNYearL1));
    }

    @Test
    void missingPartnerStatusDoesNotDependOnUpdateOrder() throws Exception {
        Lesdf_c4[] results = new Lesdf_c4[2];
        for (int order = 0; order < 2; order++) {
            BenefitUnit unit = mock(BenefitUnit.class);
            Set<Person> members = new HashSet<>();
            when(unit.getMembers()).thenReturn(members);
            when(unit.getCoupleBoolean()).thenReturn(true);

            Person first = person(1L, unit, Les_c4.EmployedOrSelfEmployed);
            Person second = person(2L, unit, null);
            set(second, "labC4L1", Les_c4.EmployedOrSelfEmployed);
            members.add(first);
            members.add(second);

            first.onEvent(Person.Processes.SnapshotPartnerEmployment);
            second.onEvent(Person.Processes.SnapshotPartnerEmployment);
            UpdateManager.applyAnnotations(order == 0 ? first : second);
            UpdateManager.applyAnnotations(order == 0 ? second : first);
            results[order] = first.getLabStatusPartnerAndOwnC4L1();
            assertEquals(Lesdf_c4.NotEmployedSpouseEmployed, second.getLabStatusPartnerAndOwnC4L1());
        }
        assertEquals(Lesdf_c4.EmployedSpouseNotEmployed, results[0]);
        assertEquals(results[0], results[1]);
    }

    @Test
    void snapshotRefreshesAcrossYearsAndForSingles() throws Exception {
        BenefitUnit unit = mock(BenefitUnit.class);
        Set<Person> members = new HashSet<>();
        when(unit.getMembers()).thenReturn(members);
        when(unit.getCoupleBoolean()).thenReturn(true);
        Person first = person(1L, unit, Les_c4.EmployedOrSelfEmployed);
        Person second = person(2L, unit, Les_c4.EmployedOrSelfEmployed);
        members.add(first);
        members.add(second);
        first.onEvent(Person.Processes.SnapshotPartnerEmployment);
        second.onEvent(Person.Processes.SnapshotPartnerEmployment);
        UpdateManager.applyAnnotations(first);
        UpdateManager.applyAnnotations(second);
        assertEquals(Lesdf_c4.BothEmployed, first.getLabStatusPartnerAndOwnC4L1());

        first.setLabC4(Les_c4.NotEmployed);
        second.setLabC4(null);
        first.onEvent(Person.Processes.SnapshotPartnerEmployment);
        second.onEvent(Person.Processes.SnapshotPartnerEmployment);
        UpdateManager.applyAnnotations(second);
        UpdateManager.applyAnnotations(first);
        assertEquals(Lesdf_c4.BothNotEmployed, first.getLabStatusPartnerAndOwnC4L1());
        assertEquals(Lesdf_c4.BothNotEmployed, second.getLabStatusPartnerAndOwnC4L1());

        when(unit.getCoupleBoolean()).thenReturn(false);
        first.onEvent(Person.Processes.SnapshotPartnerEmployment);
        assertNull(first.getLabStatusPartnerAndOwnC4L1());
    }
}