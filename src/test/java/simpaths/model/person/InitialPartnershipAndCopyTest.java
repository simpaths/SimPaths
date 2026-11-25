package simpaths.model.person;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import simpaths.data.Parameters;
import simpaths.model.BenefitUnit;
import simpaths.model.Person;
import simpaths.model.SimPathsModel;
import simpaths.model.enums.Dcpst;
import simpaths.model.enums.Education;
import simpaths.model.enums.Labour;
import simpaths.model.enums.SampleEntry;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class InitialPartnershipAndCopyTest {
    private static void set(Person person, String name, Object value) throws Exception {
        Field field = Person.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(person, value);
    }

    private static Object get(Person person, String name) throws Exception {
        Field field = Person.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(person);
    }

    @Test
    void initialPreviousStatusUsesDurationAndMissingDefaultsToSingle() throws Exception {
        for (Integer duration : new Integer[]{null, 0, 1, 2, 5}) {
            Person person = new Person(1L);
            person.setDemAge(10);
            person.setDemPartnerNYear(duration);
            set(person, "model", mock(SimPathsModel.class));
            set(person, "labHrsWorkEnumWeek", Labour.ZERO);
            set(person, "careHrsProvidedWeek", 0.0);
            person.setAdditionalFieldsInInitialPopulation();
            Dcpst expected = duration != null && duration >= 2 ? Dcpst.Partnered : Dcpst.Single;
            assertEquals(expected, get(person, "demPartnerStatusL1"));
            set(person, "demPartnerStatusL2", get(person, "demPartnerStatusL1"));
            set(person, "demPartnerStatusL1", Dcpst.Partnered);
            assertEquals(expected == Dcpst.Single ? 1.0 : 0.0,
                    person.getDoubleValue(Person.Variables.demEnterPartnerFlagL1));
        }
    }

    @Test
    void copyUsesPartnerEducationAndIndependentMentalHealthLag() throws Exception {
        try (MockedStatic<Parameters> parameters = mockStatic(Parameters.class, CALLS_REAL_METHODS)) {
            parameters.when(() -> Parameters.getWageAndAgeDifferentialMultivariateNormalDistribution(anyLong()))
                    .thenReturn(new double[]{0.0, 0.0});
            Person original = new Person(1L, 123L);
            original.setDemAge(10);
            set(original, "benefitUnit", mock(BenefitUnit.class));
            set(original, "eduHighestC4L1", Education.High);
            set(original, "eduHighestPartnerC4L1", Education.Low);
            set(original, "healthMentalMcs", 50.0);
            set(original, "labWageFullTimeHrly", 0.0);
            set(original, "labHrsWorkEnumWeek", Labour.ZERO);
            for (boolean hasMentalLag : new boolean[]{true, false}) {
                set(original, "healthMentalMcsL1", hasMentalLag ? 42.0 : null);
                set(original, "healthWbScore0to36L1", hasMentalLag ? null : 10.0);
                Person copy = new Person(original, 456L, SampleEntry.ProcessedInputData);
                assertEquals(hasMentalLag ? 42.0 : 50.0, copy.getHealthMentalMcsL1());
                assertEquals(Education.Low, get(copy, "eduHighestPartnerC4L1"));
            }
        }
    }
}