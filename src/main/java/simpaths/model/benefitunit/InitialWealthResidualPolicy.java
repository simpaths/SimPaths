package simpaths.model.benefitunit;

/**
 * Population-load policy for bounding imputed persistent wealth residual states.
 *
 * <p>The limits are expressed as multiples of the matching regression RMSE. A
 * disabled cap leaves the imputed state unchanged. This policy applies only
 * when the initial state is reconstructed from the starting population; it
 * does not alter observed wealth levels or annual regression innovations.</p>
 */
public record InitialWealthResidualPolicy(
        boolean capHousingResiduals,
        double housingRmseMultiplier,
        boolean capMortgageResiduals,
        double mortgageRmseMultiplier) {

    public InitialWealthResidualPolicy {
        validateMultiplier("housing", capHousingResiduals, housingRmseMultiplier);
        validateMultiplier("mortgage", capMortgageResiduals, mortgageRmseMultiplier);
    }

    public double applyHousingCap(double residual, double rmse) {
        return capResidual(residual, rmse, capHousingResiduals, housingRmseMultiplier);
    }

    public double applyMortgageCap(double residual, double rmse) {
        return capResidual(residual, rmse, capMortgageResiduals, mortgageRmseMultiplier);
    }

    static double capResidual(double residual, double rmse, boolean enabled, double multiplier) {
        if (!Double.isFinite(residual)) {
            throw new IllegalArgumentException("Initial wealth residual must be finite");
        }
        if (!enabled) {
            return residual;
        }
        if (!Double.isFinite(rmse) || rmse <= 0.0) {
            throw new IllegalArgumentException("RMSE must be finite and positive when a residual cap is enabled");
        }

        double limit = multiplier * rmse;
        return Math.max(-limit, Math.min(limit, residual));
    }

    private static void validateMultiplier(String name, boolean enabled, double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier < 0.0 || (enabled && multiplier == 0.0)) {
            throw new IllegalArgumentException(
                    "Initial " + name + " residual RMSE multiplier must be finite and positive when enabled");
        }
    }
}
