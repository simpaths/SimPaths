package simpaths.model.benefitunit;

import microsim.data.MultiKeyCoefficientMap;
import microsim.statistics.regression.LinearRegression;
import org.junit.jupiter.api.Test;
import simpaths.data.Parameters;
import simpaths.model.BenefitUnit;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WealthNonPensionResidualInitializationTest {

    private static final double TOLERANCE = 1.0e-12;

    @Test
    void initializesStatesOnTheirEstimationScalesAndCapsOnlyConfiguredProcesses() throws Exception {
        // Property 14 less mortgage 4 gives net housing wealth of 10.
        WealthNonPension wealth = new WealthNonPension(100.0, 14.0, 4.0, 0.0, 0.0, 3.0);
        BenefitUnit benefitUnit = new BenefitUnit(true);
        double propertyBefore = wealth.getWealthPrptyValue();
        double mortgageBefore = wealth.getWealthMortgageDebtValue();
        double nonPensionWealthBefore = wealth.getWealthNonPensionValue();
        double highCostDebtBefore = wealth.getWealthFinancial().getWealthUnsecuredDebtHighValue();

        LinearRegression housingRegression = regression(
                0.5, BenefitUnit.Variables.HousingPersistence, 100.0);
        LinearRegression mortgageRegression = regression(
                0.25, BenefitUnit.Variables.MortgagePersistence, 100.0);
        LinearRegression highCostDebtRegression = regression(
                0.4, BenefitUnit.Variables.HighCostDebtPersistence, 100.0);
        MultiKeyCoefficientMap rmse = rmseMap(1.0, 0.5);

        try (ParameterOverrides ignored = new ParameterOverrides()
                .set("regHW1c", housingRegression)
                .set("regHW2c", mortgageRegression)
                .set("regFW2c", highCostDebtRegression)
                .set("coefficientMapRMSE", rmse)) {

            InitialWealthResidualPolicy policy = new InitialWealthResidualPolicy(
                    true, 2.0, false, 0.0);
            WealthNonPension.ResidualInitializationResult result =
                    wealth.initializeResidualStates(benefitUnit, policy);

            double expectedHousingResidual = Parameters.asinh(10.0) - 0.5;
            double expectedMortgageResidual = Math.log(4.0) - 0.25;
            double expectedHighCostDebtResidual = Parameters.asinh(3.0) - 0.4;

            assertTrue(result.housingInitialized());
            assertEquals(expectedHousingResidual, result.housingRawResidual(), TOLERANCE);
            assertEquals(2.0, result.housingStoredResidual(), TOLERANCE);
            assertTrue(result.housingCapped());
            assertEquals(2.0, wealth.getWealthHousing().getWealthNetInnovation(), TOLERANCE);

            assertTrue(result.mortgageInitialized());
            assertEquals(expectedMortgageResidual, result.mortgageRawResidual(), TOLERANCE);
            assertEquals(expectedMortgageResidual, result.mortgageStoredResidual(), TOLERANCE);
            assertFalse(result.mortgageCapped());
            assertEquals(expectedMortgageResidual,
                    wealth.getWealthHousing().getWealthMortgageDebtInnovation(), TOLERANCE);

            assertTrue(result.highCostDebtInitialized());
            assertEquals(expectedHighCostDebtResidual,
                    wealth.getWealthFinancial().getWealthUnsecuredDebtHighInnovation(), TOLERANCE);

            // Initialisation changes persistent states only, never the observed starting levels.
            assertEquals(propertyBefore, wealth.getWealthPrptyValue(), TOLERANCE);
            assertEquals(mortgageBefore, wealth.getWealthMortgageDebtValue(), TOLERANCE);
            assertEquals(nonPensionWealthBefore, wealth.getWealthNonPensionValue(), TOLERANCE);
            assertEquals(highCostDebtBefore,
                    wealth.getWealthFinancial().getWealthUnsecuredDebtHighValue(), TOLERANCE);
        }
    }

    @Test
    void skipsModelsForAbsentWealthStates() {
        WealthNonPension wealth = new WealthNonPension(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        BenefitUnit benefitUnit = new BenefitUnit(true);
        InitialWealthResidualPolicy policy = new InitialWealthResidualPolicy(
                false, 0.0, false, 0.0);

        WealthNonPension.ResidualInitializationResult result =
                wealth.initializeResidualStates(benefitUnit, policy);

        assertFalse(result.housingInitialized());
        assertFalse(result.mortgageInitialized());
        assertFalse(result.highCostDebtInitialized());
        assertEquals(0.0, wealth.getWealthHousing().getWealthNetInnovation(), TOLERANCE);
        assertEquals(0.0, wealth.getWealthHousing().getWealthMortgageDebtInnovation(), TOLERANCE);
        assertEquals(0.0,
                wealth.getWealthFinancial().getWealthUnsecuredDebtHighInnovation(), TOLERANCE);
    }

    private static LinearRegression regression(
            double constant, BenefitUnit.Variables persistenceVariable, double persistenceCoefficient) {
        MultiKeyCoefficientMap coefficients = new MultiKeyCoefficientMap(
                new String[]{"REGRESSOR"}, new String[]{"COEFFICIENT"});
        coefficients.putValue(BenefitUnit.Variables.Constant.name(), constant);
        coefficients.putValue(persistenceVariable.name(), persistenceCoefficient);
        return new LinearRegression(coefficients);
    }

    private static MultiKeyCoefficientMap rmseMap(double housingRmse, double mortgageRmse) {
        MultiKeyCoefficientMap rmse = new MultiKeyCoefficientMap(
                new String[]{"REGRESSION"}, new String[]{"RMSE"});
        rmse.putValue("HW1c", housingRmse);
        rmse.putValue("HW2c", mortgageRmse);
        return rmse;
    }

    /** Restores the process-wide Parameters fields even if an assertion fails. */
    private static final class ParameterOverrides implements AutoCloseable {

        private final Map<Field, Object> originals = new LinkedHashMap<>();

        ParameterOverrides set(String fieldName, Object replacement) throws ReflectiveOperationException {
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
