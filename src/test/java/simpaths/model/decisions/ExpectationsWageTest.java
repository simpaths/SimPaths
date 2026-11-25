package simpaths.model.decisions;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import simpaths.data.ManagerRegressions;
import simpaths.data.RegressionName;
import simpaths.model.enums.Gender;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ExpectationsWageTest {
    @Test
    void optionWageUsesPartTimeOnlyForOneThrough25Hours() throws Exception {
        Expectations expectations = mock(Expectations.class, CALLS_REAL_METHODS);
        expectations.labWageFullTimeHrly = 20.0;
        expectations.currentStates = mock(States.class);
        Method wage = Expectations.class.getDeclaredMethod("getHourlyWageRate", int.class);
        wage.setAccessible(true);
        try (MockedStatic<ManagerRegressions> regressions = mockStatic(ManagerRegressions.class)) {
            for (Gender gender : Gender.values()) {
                when(expectations.currentStates.getGenderCode()).thenReturn(gender);
                RegressionName name = gender == Gender.Male ? RegressionName.WagesMalesE : RegressionName.WagesFemalesE;
                regressions.when(() -> ManagerRegressions.getRegressionCoeff(name, "labPt")).thenReturn(Math.log(0.8));
                for (int hours : new int[]{0, 1, 24, 25, 26}) {
                    assertEquals(hours > 0 && hours <= 25 ? 16.0 : 20.0,
                            (double) wage.invoke(expectations, hours), 1e-10, gender + " " + hours);
                }
            }
        }
    }
}