package simpaths.model.benefitunit;

import microsim.data.MultiKeyCoefficientMap;
import microsim.statistics.regression.LinearRegression;
import org.junit.jupiter.api.Test;
import simpaths.data.Parameters;
import simpaths.experiment.SimPathsMultiRun;
import simpaths.model.BenefitUnit;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WealthModuleTest {

    private static final double TOLERANCE = 1.0e-12;

    @Test
    void multiRunConfigurationBindsToTheWealthModule() {
        WealthModule module = new WealthModule();

        SimPathsMultiRun.updateParameters(module, Map.of(
                "capInitialHousingResiduals", true,
                "initialHousingResidualCapRmseMultiplier", 3.1,
                "capInitialMortgageResiduals", true,
                "initialMortgageResidualCapRmseMultiplier", 1.75));

        assertTrue(module.isCapInitialHousingResiduals());
        assertEquals(3.1, module.getInitialHousingResidualCapRmseMultiplier(), TOLERANCE);
        assertTrue(module.isCapInitialMortgageResiduals());
        assertEquals(1.75, module.getInitialMortgageResidualCapRmseMultiplier(), TOLERANCE);
    }

    @Test
    void disabledProjectionRebuildsLevelsWithoutRegressionParameters() throws Exception {
        BenefitUnit benefitUnit = benefitUnit(100.0, 20.0, 14.0, 4.0, 2.0, 3.0);
        WealthModule module = new WealthModule();

        // A disabled module must not require any regression or RMSE state. If
        // residual scoring leaks into this path, these null overrides make the
        // test fail at the point of access.
        try (ParameterOverrides ignored = new ParameterOverrides()
                .set("regHW1c", null)
                .set("regHW2c", null)
                .set("regFW2c", null)
                .set("coefficientMapRMSE", null)) {

            WealthModule.WealthInitializationSummary summary =
                    module.initializePopulation(List.of(benefitUnit), false);

            assertEquals(1, summary.benefitUnits());
            assertEquals(0, summary.homeowners());
            assertEquals(0, summary.housingCappedLower());
            assertEquals(0, summary.housingCappedUpper());
            assertEquals(0, summary.mortgageHolders());
            assertEquals(0, summary.mortgageCappedLower());
            assertEquals(0, summary.mortgageCappedUpper());
            assertEquals(0, summary.highCostDebtHolders());
            assertEquals(0.0, summary.maxAbsoluteHousingResidualBefore(), TOLERANCE);
            assertEquals(0.0, summary.maxAbsoluteHousingResidualAfter(), TOLERANCE);
            assertEquals(0.0, summary.maxAbsoluteMortgageResidualBefore(), TOLERANCE);
            assertEquals(0.0, summary.maxAbsoluteMortgageResidualAfter(), TOLERANCE);

            WealthNonPension wealth = benefitUnit.getWealthNonPension();
            assertEquals(80.0, wealth.getWealthNonPensionValue(), TOLERANCE);
            assertEquals(14.0, wealth.getWealthPrptyValue(), TOLERANCE);
            assertEquals(4.0, wealth.getWealthMortgageDebtValue(), TOLERANCE);
            assertEquals(10.0, wealth.getWealthHousing().getWealthNetHousing(), TOLERANCE);
            assertEquals(70.0, wealth.getWealthFinancial().getValue(), TOLERANCE);
            assertEquals(75.0,
                    wealth.getWealthFinancial().getWealthFinancialAssetsValue(), TOLERANCE);
            assertEquals(2.0,
                    wealth.getWealthFinancial().getWealthUnsecuredDebtLowValue(), TOLERANCE);
            assertEquals(3.0,
                    wealth.getWealthFinancial().getWealthUnsecuredDebtHighValue(), TOLERANCE);
            assertEquals(0.0, wealth.getWealthHousing().getWealthNetInnovation(), TOLERANCE);
            assertEquals(0.0,
                    wealth.getWealthHousing().getWealthMortgageDebtInnovation(), TOLERANCE);
            assertEquals(0.0,
                    wealth.getWealthFinancial().getWealthUnsecuredDebtHighInnovation(), TOLERANCE);

            assertObservedLevels(benefitUnit, 100.0, 20.0, 14.0, 4.0, 2.0, 3.0);
        }
    }

    @Test
    void initializesAllBenefitUnitsReportsBothCapTailsAndIsRepeatable() throws Exception {
        // With a structural score of 1.0 these give raw residuals +2.0
        // and -0.9 respectively on the asinh housing scale.
        BenefitUnit upperTail = benefitUnit(
                Math.sinh(3.0), 0.0, Math.sinh(3.0), 0.0, 0.0, 0.0);
        BenefitUnit lowerTail = benefitUnit(
                Math.sinh(0.1), 0.0, Math.sinh(0.1), 0.0, 0.0, 0.0);

        LinearRegression housingRegression = regression(
                1.0, BenefitUnit.Variables.HousingPersistence, 100.0);
        MultiKeyCoefficientMap rmse = rmseMap(0.25);

        WealthModule module = new WealthModule();
        module.setCapInitialHousingResiduals(true);
        module.setInitialHousingResidualCapRmseMultiplier(2.0);

        try (ParameterOverrides ignored = new ParameterOverrides()
                .set("regHW1c", housingRegression)
                .set("coefficientMapRMSE", rmse)) {

            List<BenefitUnit> population = List.of(upperTail, lowerTail);
            WealthModule.WealthInitializationSummary first =
                    module.initializePopulation(population, true);

            assertEquals(2, first.benefitUnits());
            assertEquals(2, first.homeowners());
            assertEquals(1, first.housingCappedLower());
            assertEquals(1, first.housingCappedUpper());
            assertEquals(0, first.mortgageHolders());
            assertEquals(0, first.mortgageCappedLower());
            assertEquals(0, first.mortgageCappedUpper());
            assertEquals(0, first.highCostDebtHolders());
            assertEquals(2.0, first.maxAbsoluteHousingResidualBefore(), TOLERANCE);
            assertEquals(0.5, first.maxAbsoluteHousingResidualAfter(), TOLERANCE);
            assertEquals(0.0, first.maxAbsoluteMortgageResidualBefore(), TOLERANCE);
            assertEquals(0.0, first.maxAbsoluteMortgageResidualAfter(), TOLERANCE);

            assertEquals(0.5, upperTail.getWealthNonPension()
                    .getWealthHousing().getWealthNetInnovation(), TOLERANCE);
            assertEquals(-0.5, lowerTail.getWealthNonPension()
                    .getWealthHousing().getWealthNetInnovation(), TOLERANCE);
            assertObservedLevels(upperTail, Math.sinh(3.0), 0.0,
                    Math.sinh(3.0), 0.0, 0.0, 0.0);
            assertObservedLevels(lowerTail, Math.sinh(0.1), 0.0,
                    Math.sinh(0.1), 0.0, 0.0, 0.0);

            // This is the same reconstruction performed after a processed
            // population reload: transient wealth is rebuilt from levels and
            // must produce the same state and diagnostics.
            WealthModule.WealthInitializationSummary second =
                    module.initializePopulation(population, true);

            assertEquals(first, second);
            assertEquals(0.5, upperTail.getWealthNonPension()
                    .getWealthHousing().getWealthNetInnovation(), TOLERANCE);
            assertEquals(-0.5, lowerTail.getWealthNonPension()
                    .getWealthHousing().getWealthNetInnovation(), TOLERANCE);
        }
    }

    private static BenefitUnit benefitUnit(
            double totalWealth,
            double pensionWealth,
            double propertyValue,
            double mortgageDebt,
            double lowCostDebt,
            double highCostDebt) throws ReflectiveOperationException {

        BenefitUnit benefitUnit = new BenefitUnit(true);
        benefitUnit.setWealthTotValue(totalWealth);
        benefitUnit.setWealthPensValue(pensionWealth);
        benefitUnit.setWealthPrptyValue(propertyValue);
        setField(benefitUnit, "wealthMortgageDebtValue", mortgageDebt);
        benefitUnit.setWealthUnsecuredDebtLowValue(lowCostDebt);
        benefitUnit.setWealthUnsecuredDebtHighValue(highCostDebt);
        return benefitUnit;
    }

    private static void assertObservedLevels(
            BenefitUnit benefitUnit,
            double totalWealth,
            double pensionWealth,
            double propertyValue,
            double mortgageDebt,
            double lowCostDebt,
            double highCostDebt) {

        assertEquals(totalWealth, benefitUnit.getWealthTotValue(), TOLERANCE);
        assertEquals(pensionWealth, benefitUnit.getWealthPensValue(), TOLERANCE);
        assertEquals(propertyValue, benefitUnit.getWealthPrptyValue(), TOLERANCE);
        assertEquals(mortgageDebt, benefitUnit.getWealthMortgageDebtValue(), TOLERANCE);
        assertEquals(lowCostDebt, benefitUnit.getWealthUnsecuredDebtLowValue(), TOLERANCE);
        assertEquals(highCostDebt, benefitUnit.getWealthUnsecuredDebtHighValue(), TOLERANCE);
    }

    private static LinearRegression regression(
            double constant, BenefitUnit.Variables persistenceVariable, double persistenceCoefficient) {
        MultiKeyCoefficientMap coefficients = new MultiKeyCoefficientMap(
                new String[]{"REGRESSOR"}, new String[]{"COEFFICIENT"});
        coefficients.putValue(BenefitUnit.Variables.Constant.name(), constant);
        coefficients.putValue(persistenceVariable.name(), persistenceCoefficient);
        return new LinearRegression(coefficients);
    }

    private static MultiKeyCoefficientMap rmseMap(double housingRmse) {
        MultiKeyCoefficientMap rmse = new MultiKeyCoefficientMap(
                new String[]{"REGRESSION"}, new String[]{"RMSE"});
        rmse.putValue("HW1c", housingRmse);
        return rmse;
    }

    private static void setField(Object target, String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** Restores process-wide parameter fields even if an assertion fails. */
    private static final class ParameterOverrides implements AutoCloseable {

        private final Map<Field, Object> originals = new LinkedHashMap<>();

        ParameterOverrides set(String fieldName, Object replacement)
                throws ReflectiveOperationException {
            Field field = Parameters.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            originals.put(field, field.get(null));
            field.set(null, replacement);
            return this;
        }

        @Override
        public void close() throws ReflectiveOperationException {
            for (Map.Entry<Field, Object> entry : originals.entrySet()) {
                entry.getKey().set(null, entry.getValue());
            }
        }
    }
}
