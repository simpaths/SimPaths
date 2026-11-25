package simpaths.model;

import microsim.statistics.regression.LinearRegression;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import simpaths.data.ManagerRegressions;
import simpaths.data.Parameters;
import simpaths.data.RegressionName;
import simpaths.model.enums.Gender;
import simpaths.model.enums.Les_c4;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WagePartTimeTest {
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = Person.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void personHoursAndWageBoundaries() throws Exception {
        Person person = mock(Person.class, CALLS_REAL_METHODS);
        set(person, "labWageFullTimeHrly", 20.0);
        set(person, "demMaleFlag", Gender.Male);
        set(person, "labC4L1", Les_c4.EmployedOrSelfEmployed);
        try (MockedStatic<ManagerRegressions> regressions = mockStatic(ManagerRegressions.class)) {
            regressions.when(() -> ManagerRegressions.getRegressionCoeff(RegressionName.WagesMalesE, "labPt"))
                    .thenReturn(Math.log(0.8));
            for (int hours : new int[]{0, 1, 24, 25, 26}) {
                doReturn(hours).when(person).getLabourSupplyHoursWeekly();
                boolean partTime = hours > 0 && hours <= 25;
                assertEquals(partTime ? 1.0 : 0.0, person.getDoubleValue(Person.Variables.labPt));
                assertEquals(partTime, Person.isPartTimeHours(hours));
                assertEquals(hours > 25, Person.isFullTimeHours(hours));
                assertEquals(partTime ? 16.0 : 20.0, person.getHourlyWageRate(hours), 1e-10);
            }
            assertEquals(16.0, person.getHourlyWageRate(25.49), 1e-10);
            assertEquals(20.0, person.getHourlyWageRate(25.5), 1e-10);
        }
    }

    @Test
    void fullTimePredictionRemovesSelectedEquationPremiumBeforeClipping() throws Exception {
        try (MockedStatic<Parameters> parameters = mockStatic(Parameters.class, CALLS_REAL_METHODS);
             MockedStatic<ManagerRegressions> coefficients = mockStatic(ManagerRegressions.class)) {
            for (Gender gender : Gender.values()) {
                for (boolean employed : new boolean[]{true, false}) {
                    RegressionName name = gender == Gender.Male
                            ? (employed ? RegressionName.WagesMalesE : RegressionName.WagesMalesNE)
                            : (employed ? RegressionName.WagesFemalesE : RegressionName.WagesFemalesNE);
                    double premium = gender == Gender.Male ? (employed ? -0.5 : 0.3) : (employed ? -0.2 : 0.6);
                    double[] fullTimeWage = {200.0};
                    LinearRegression regression = mock(LinearRegression.class);
                    when(regression.getScore(any(Person.class), eq(Person.Variables.class)))
                            .thenAnswer(inv -> Math.log(fullTimeWage[0]) + premium * ((Person) inv.getArgument(0)).getDoubleValue(Person.Variables.labPt));
                    if (gender == Gender.Male && employed) parameters.when(Parameters::getRegWagesMalesE).thenReturn(regression);
                    if (gender == Gender.Male && !employed) parameters.when(Parameters::getRegWagesMalesNE).thenReturn(regression);
                    if (gender != Gender.Male && employed) parameters.when(Parameters::getRegWagesFemalesE).thenReturn(regression);
                    if (gender != Gender.Male && !employed) parameters.when(Parameters::getRegWagesFemalesNE).thenReturn(regression);
                    coefficients.when(() -> ManagerRegressions.getRegressionCoeff(name, "labPt")).thenReturn(premium);
                    Person person = mock(Person.class, CALLS_REAL_METHODS);
                    set(person, "demAge", 40);
                    set(person, "demMaleFlag", gender);
                    set(person, "labC4L1", employed ? Les_c4.EmployedOrSelfEmployed : Les_c4.NotEmployed);
                    set(person, "statInnovations", mock(Innovations.class));
                    set(person, employed ? "labWageRegressRandomCompoponentEmp" : "labWageRegressRandomCompoponentNotEmp", 0.0);
                    SimPathsModel model = mock(SimPathsModel.class);
                    model.fixRegressionStochasticComponent = true;
                    set(person, "model", model);
                    for (double base : new double[]{0.5, 20.0, 200.0}) {
                        fullTimeWage[0] = base;
                        double clipped = Math.max(Parameters.MIN_HOURLY_WAGE_RATE, Math.min(Parameters.MAX_HOURLY_WAGE_RATE, base));
                        for (int hours : new int[]{0, 24, 25, 26}) {
                            doReturn(hours).when(person).getLabourSupplyHoursWeekly();
                            person.updateFullTimeHourlyEarnings();
                            assertEquals(clipped, person.getHourlyWageRate(26), 1e-10, name + " " + hours);
                            assertEquals(clipped * Math.exp(premium), person.getHourlyWageRate(25), 1e-10, name + " " + hours);
                        }
                    }
                }
            }
        }
    }
}
