package simpaths.model.benefitunit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InitialWealthResidualPolicyTest {

    private static final double TOLERANCE = 1.0e-12;

    @Test
    void disabledCapsLeaveFiniteResidualsUnchanged() {
        InitialWealthResidualPolicy policy = new InitialWealthResidualPolicy(
                false, 0.0, false, 0.0);

        assertEquals(3.5, policy.applyHousingCap(3.5, Double.NaN), TOLERANCE);
        assertEquals(-4.5, policy.applyMortgageCap(-4.5, -1.0), TOLERANCE);
    }

    @Test
    void enabledCapsClampAtSymmetricRmseLimits() {
        InitialWealthResidualPolicy policy = new InitialWealthResidualPolicy(
                true, 2.6, true, 1.5);

        assertEquals(1.3, policy.applyHousingCap(2.0, 0.5), TOLERANCE);
        assertEquals(-1.3, policy.applyHousingCap(-2.0, 0.5), TOLERANCE);
        assertEquals(1.3, policy.applyHousingCap(1.3, 0.5), TOLERANCE);
        assertEquals(0.75, policy.applyMortgageCap(1.0, 0.5), TOLERANCE);
        assertEquals(-0.75, policy.applyMortgageCap(-1.0, 0.5), TOLERANCE);
        assertEquals(0.25, policy.applyMortgageCap(0.25, 0.5), TOLERANCE);
    }

    @Test
    void housingAndMortgageCapsCanBeEnabledIndependently() {
        InitialWealthResidualPolicy housingOnly = new InitialWealthResidualPolicy(
                true, 2.0, false, 0.0);
        InitialWealthResidualPolicy mortgageOnly = new InitialWealthResidualPolicy(
                false, 0.0, true, 3.0);

        assertEquals(1.0, housingOnly.applyHousingCap(4.0, 0.5), TOLERANCE);
        assertEquals(4.0, housingOnly.applyMortgageCap(4.0, 0.5), TOLERANCE);
        assertEquals(4.0, mortgageOnly.applyHousingCap(4.0, 0.5), TOLERANCE);
        assertEquals(1.5, mortgageOnly.applyMortgageCap(4.0, 0.5), TOLERANCE);
    }

    @Test
    void rejectsInvalidMultipliers() {
        assertThrows(IllegalArgumentException.class,
                () -> new InitialWealthResidualPolicy(true, 0.0, false, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new InitialWealthResidualPolicy(false, -0.1, false, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new InitialWealthResidualPolicy(false, Double.NaN, false, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new InitialWealthResidualPolicy(false, 0.0, true, Double.POSITIVE_INFINITY));
    }

    @Test
    void rejectsInvalidResidualsAndEnabledRmseValues() {
        InitialWealthResidualPolicy enabled = new InitialWealthResidualPolicy(
                true, 2.6, true, 2.6);
        InitialWealthResidualPolicy disabled = new InitialWealthResidualPolicy(
                false, 0.0, false, 0.0);

        assertThrows(IllegalArgumentException.class,
                () -> enabled.applyHousingCap(Double.NaN, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> disabled.applyMortgageCap(Double.NEGATIVE_INFINITY, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> enabled.applyHousingCap(1.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> enabled.applyHousingCap(1.0, -1.0));
        assertThrows(IllegalArgumentException.class,
                () -> enabled.applyMortgageCap(1.0, Double.NaN));
    }
}
